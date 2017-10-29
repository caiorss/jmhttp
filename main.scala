
import java.net.{ServerSocket}

val httpVersion = "HTTP/1.0"

case class HttpRequest(
  method:    String,
  path:      String,
  headers:   Map[String, String],
  inpStream: java.io.InputStream,
  outStream: java.io.OutputStream,
)



val port = 8080

val ssock = new ServerSocket(port)

val client = ssock.accept()
println("Client has connected.")


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

