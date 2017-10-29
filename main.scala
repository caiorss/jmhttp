
import java.net.{ServerSocket}

val httpVersion = "HTTP/1.0"

type HttpHeaders = Map[String, String]

case class HttpRequest(
  method:    String,
  path:      String,
  headers:   Map[String, String],
  version:   String,
  inpStream: java.io.InputStream,
  outStream: java.io.OutputStream,
) {

  /** Send response with http request parameters for debugging. */
  def sendDebugResponse() {
    val crlf = "\r\n"
    val out = new java.io.DataOutputStream(outStream)
    out.writeBytes(s"${httpVersion} 200 OK" + crlf)
    out.writeBytes(crlf)
    out.writeBytes("Method =  " + method  + crlf)
    out.writeBytes("Path   =  " + path    + crlf)
    out.writeBytes(crlf)
    out.writeBytes("headers = " + crlf)
    headers foreach { case (k, v) =>
      out.writeBytes(s"${k}\t=\t${v}" + crlf)
    }
    out.close()
  }

  def sendTextResponse(text: String, status: Int = 200, statusMsg: String = "Ok", headers: HttpHeaders = Map[String, String]()) = {
    val crlf = "\r\n"
    val out = new java.io.DataOutputStream(outStream)
    out.writeBytes(s"${httpVersion} ${status} ${statusMsg}" + crlf)
    headers foreach { case (k, v) =>
      out.writeBytes(s"${k}: ${v}" + crlf)
    }
    out.writeBytes(crlf)
    text.lines.foreach{ lin =>
      out.writeBytes(lin + crlf)
    }
    out.close()
  } // --- EoF sendTexResponse ---- //



  def send404Response(text: String, headers: HttpHeaders = Map[String, String]()) = {
    this.sendTextResponse(text, 404, "NOT FOUND", headers)
  }


} //----  Eof case class HttpRequest ----- //


def getClientRequest(client: java.net.Socket, verbose: Boolean = false) = {
  def getHeaders(sc: java.util.Scanner) = {
    var headers = Map[String, String]()

    var line: String = ""
    while({line = sc.nextLine(); line} != ""){

      if (verbose) println("Htp header = " + line)

      line.split(":\\s+", 2) match {
        case Array(key, value)
            => headers += key.stripSuffix(":") -> value
        case Array("")
            => ()
        case _
            => throw new IllegalArgumentException("Error: invalid http header")
      }
    }
    headers
  }

  val sc = new java.util.Scanner(client.getInputStream())
  val Array(httpMethod, urlPath, httpVersion) = sc.nextLine().split(" ")
  val headers = getHeaders(sc)

  HttpRequest(
    method  = httpMethod,
    path    = urlPath,
    headers = headers,
    version = httpVersion,
    inpStream = client.getInputStream(),
    outStream = client.getOutputStream()
  )

} //------ End of getClientRequest() ----- //


val port = 8080

val ssock = new ServerSocket(port)

println("Waiting connection")
val client = ssock.accept()
println("Client has connected.")


val req = getClientRequest(client, true)
req.sendEchoDebug()


