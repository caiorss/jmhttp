
import java.net.{ServerSocket}

val httpVersion = "HTTP/1.0"

case class HttpRequest(
  method:    String,
  path:      String,
  headers:   Map[String, String],
  version:   String,
  inpStream: java.io.InputStream,
  outStream: java.io.OutputStream,
) {

  /** Send response with http request parameters for debugging. */
  def sendEchoDebug() {
    val crlf = "\r\n"
    val out = new java.io.DataOutputStream(req.outStream)
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
}


def getClientRequest(client: java.net.Socket) = {
  def getHeaders(sc: java.util.Scanner) = {
    var headers = Map[String, String]()
    var line: String = ""
    while({line = sc.nextLine(); line} != ""){
      line.split(" ") match {
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
    inpStream = client.getInputStream(),
    outStream = client.getOutputStream()
  )

} //------ End of getClientRequest() ----- //






scala> sc.nextLine()
res1: String = GET /index HTTP/1.1

