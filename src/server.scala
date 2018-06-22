package jmhttp.server

import java.net.{InetAddress, ServerSocket, Socket}
import jmhttp.utils.{Utils, ImageUtils}

class HttpResponseWriter(outStream: java.io.OutputStream){

  private val out = new java.io.DataOutputStream(outStream)
  private val crlf = "\r\n"

  def print(text: String) = {
    out.writeBytes(text)
  }

  def println(line: String = "") = {
    out.writeBytes(line + crlf)
  }

  def printLines(lines: String*) = {
    lines foreach this.println
  }

  def printlnHtml(line: String = "") = {
    out.writeBytes(line + "</br>\n" + crlf)
  }

  def copyStream(from: java.io.InputStream, size: Int = 1024) = {
    val to = this.out
    // number of bytes read
    var n = 0
    // Buffer with 1024 bytes or 1MB
    val buf = new Array[Byte](size)
    while( {n = from.read(buf) ; n} > 0 ){
      to.write(buf, 0, n)
    }
    from.close()
  }

  def close() =
    out.close()

  def flush() =
    out.flush()

} /* --- EoF class HttpResponseWriter --- */

sealed trait ResponseBody
case class ResponseBodyText(text: String)                         extends ResponseBody
case class ResponseBodyStream(stream: java.io.InputStream)        extends ResponseBody
case class ResponseBodyWriter(writer: HttpResponseWriter => Unit) extends ResponseBody


case class HttpResponse(
  statusCode:    Int,
  statusMessage: String,
  mimeType:      String,
  headers:       Map[String, String],
  body:          ResponseBody
) {
  def addHeader(nameValue: (String, String)) ={
    val (name, value) = nameValue
    this.copy(headers = this.headers ++ Map(name -> value))
  }
  def addHeader(name: String, value: String) =
    this.copy(headers = this.headers ++ Map(name -> value))

  def setContentLenght(len: Long) =
    addHeader("Content-Length", len.toString)
}

case class HttpRequest(
  method:    String,
  path:      String,
  headers:   Map[String, String],
  version:   String,
  address:   InetAddress,
  inpStream: java.io.InputStream
) {
  override def toString() =
    s"HTTP Request: path = ${this.path} - method = ${this.method} - address = ${this.address}"
} //----  Eof case class HttpRequest ----- //

case class HttpTransaction(
                            request:  HttpRequest,
                            response: HttpResponseWriter,
                            logger:   java.util.logging.Logger
 ){


} // ---- End of class HttpTransaction ----- //


/** Generic http route
  * @param matcher - Predicate which matches the HTTP request
  * @param action  - Action that writes the response when the HTTP request is matched.
  **/
trait HttpRoute{
  def matcher(rq: HttpRequest): Boolean
  def action(rq:  HttpRequest): HttpResponse
}

object HttpRoute{
  def apply(
    matcher: HttpRequest => Boolean,
    action:  HttpRequest => HttpResponse
  ) = {
    val m = matcher
    val a = action
    new HttpRoute{
      def matcher(rq: HttpRequest) =
        m(rq)
      def action(rq:  HttpRequest) =
        a(rq)
    }
  }
}


object ResponseUtils{
  def textResponse(
    text:            String,
    status:          Int = 200,
    statusMessage:   String = "OK"
  ) = HttpResponse(
    statusCode    = status,
    statusMessage = statusMessage,
    mimeType      = "text/plain",
    headers       = Map(),
    body          = ResponseBodyText(text)
  )

  def writerResponse(
    status:          Int = 200,
    statusMessage:   String = "OK",
    mimeType:        String = "text/html"
  )(writer: HttpResponseWriter => Unit) =
    HttpResponse(
      statusCode    = status,
      statusMessage = statusMessage,
      mimeType      = mimeType,
      headers       = Map(),
      body          = ResponseBodyWriter(writer)
    )

  def htmlResponse(
    htmlCode:        String,
    status:          Int = 200,
    statusMessage:   String = "OK"
  ) = HttpResponse(
    statusCode    = status,
    statusMessage = statusMessage,
    mimeType      = "text/html",
    headers       = Map(),
    body          = ResponseBodyText(htmlCode)
  )

  def error404Response(text: String) =
    textResponse(text, status = 404, statusMessage = "NOT FOUND")

  def redirectResponse(url: String) =
    textResponse("Moved to " + url, status = 302, statusMessage = "MOVED PERMANENTLY")
      .addHeader("Location" -> url)


  /** Responses that writes back the request to the client side.
    * It is useful for debugging http request. */
  def echoResponse(req: HttpRequest) =
    writerResponse(mimeType = "text/plain"){ w =>
      val HttpRequest(method, path, headers, version, address, _) = req
      w.println("Method   =  " + method)
      w.println("Path     =  " + path)
      w.println()
      w.println("headers = ")
      headers foreach { case (k, v) =>
        w.println(s"${k}\t=\t${v}")
      }
    }

  def fileResponse(
    file:     String,
    mimeType: String = "application/octet-stream"
  ) = {
    //  logger.fine(s"Sending HTTP Response file: $file - mime type = $mimeType " )
    var inp: java.io.FileInputStream = null
    try {
      inp = new java.io.FileInputStream(file)
      val fileSize = new java.io.File(file).length()
      writerResponse(mimeType = mimeType){ w =>
        w.copyStream(inp)
      } // .setContentLenght(fileSize)
    } catch {
      case ex: java.io.FileNotFoundException
          => error404Response("Error: file not found in the server.")
    } finally {
      // if(inp != null) inp.close()
    }
  }

  private val denyAccess = textResponse(
    "Unauthorized Access",
    401,
    "UNAUTHORIZED ACCESS"
  ).addHeader("Www-Authenticate" -> "Basic realm=\"Fake Realm\"")

  def basicAuth(
    user:   String,
    passwd: String,
    req:    HttpRequest
  )(action: HttpRequest => HttpResponse): HttpResponse =  {
    val secret =
      java.util.Base64
        .getEncoder()
        .encodeToString((user + ":" + passwd).getBytes("UTF-8"))
    val auth = req.headers.get("Authorization")
    // logger.info("User authorization = " + auth + " secret " + secret)
    auth match {
      case None
          => denyAccess
      case Some(a)
          =>
        if (a == "Basic " + secret) {
          //logger.info("User authtentication successful")
          action(req)
        }
        else
          denyAccess
    }
  }
} /** --- EoF object ResponseUtils --- **/


class HttpServer(
  logger: java.util.logging.Logger,
  tsl:    Boolean = false,
  login:  Option[(String, String)] = None
){

  import scala.collection.mutable.ListBuffer
  import javax.net.ServerSocketFactory
  import javax.net.ssl.SSLServerSocketFactory
  import javax.net.ssl.SSLSession
  import javax.net.ssl.SSLSocket

  private val routes = ListBuffer[HttpRoute]()
  private var basicAuthLogin: Option[(String, String)] = login

  /** Socket server */
  private var ssock = if (tsl) {
    val ss = javax.net.ssl.SSLServerSocketFactory.getDefault()
    ss.createServerSocket()
  } else
    new ServerSocket()

  init()
  private def init(){
  }

  def setLogin(login: Option[(String, String)]): Unit = {
    this.basicAuthLogin = login
  }

  def enableTSL(): Unit = {
    val ss = javax.net.ssl.SSLServerSocketFactory.getDefault()
    ssock = ss.createServerSocket()
  }

  /** Add a generic route to the HTTP Server */
  def addRoute(route: HttpRoute) =
    routes.append(route)

  def addRoute(pred: HttpRequest => Boolean)(action: HttpRequest => HttpResponse) = {
    val route = HttpRoute(pred, action)
    routes.append(route)
  }

  /** Add route with constant response which doesn't depend on the request. */
  def addRouteResponse(pred: HttpRequest => Boolean, resp: HttpResponse) =
    routes.append(HttpRoute(pred, _ => resp ))

  /** Add a route with get request /{route} GET, for instance http://192.168.0.1/books */
  def addRoutePathGET(predicate: HttpRequest => Boolean)(action: HttpRequest => HttpResponse) =
    routes.append(HttpRoute(
      matcher = (req: HttpRequest) => req.method == "GET" && predicate(req),
      action  = action
    ))

  // def addRouteParamGET(path: String)(action: (HttpTransaction, String) => Unit) = {
  //   val rule = HttpRoute(
  //     matcher = (req: HttpRequest) => {
  //       if (path == "/")
  //         req.method == "GET" &&  req.path.startsWith("/")
  //       else
  //         req.method == "GET" &&  req.path.startsWith(path)
  //     },
  //     action  = (req: HttpTransaction) => action(req, req.getPath().stripPrefix(path))
  //   )
  //   this.addRoute(rule)
  // }

  def addRouteRedirect(pred: HttpRequest => Boolean, url: String) = {
    val rule = HttpRoute(
      matcher = (req: HttpRequest) => pred(req),
      action  = (req: HttpRequest) => ResponseUtils.redirectResponse(url)
    )
    this.addRoute(rule)
  }


  // def addRouteDirNav(
  //   dirPath: String,
  //   urlPath: String,
  //   showIndex: Boolean = true,
  //   showImage: Boolean = false
  // ) = {
  //   this.addRouteParamGET(urlPath){ (req: HttpTransaction, fileURL: String) =>
  //     // println("File URL = " + fileURL)
  //     logger.fine(s"Setting up route: addRouteDirNav(dirPath = $dirPath, urlPath = $urlPath )")
  //     req.sendDirNavResponse(dirPath, urlPath, fileURL, showIndex = showIndex, showImage = showImage)
  //   }
  // }

  // def addRouteDirsIndex(
  //   urlPaths: Seq[(String, String)],
  //   showIndex: Boolean = true,
  //   showImage: Boolean = false
  // ) = {
  //   val indexPage = urlPaths.foldLeft(""){ (acc, tpl) =>
  //     val (dirUrl, dirPath) = tpl
  //     acc + "\n" + s"Directory: <a href='${dirUrl}'>${dirUrl}</a></br></br>"
  //   }
  //   this.addRoutePathGET("/"){
  //     val pageHeader = "<h1>Shared Directories</h1></br>\n"
  //     _.sendTextResponse(pageHeader + indexPage, mimeType = "text/html")
  //   }
  //   urlPaths foreach { case (dirUrl, dirPath) =>
  //     this.addRouteDirNav(dirPath, dirUrl, showIndex = showIndex, showImage)
  //   }
  // }


  def addRouteEcho(path: String = "/echo") = {
    val rule = HttpRoute(
      matcher = (req: HttpRequest) => req.path.startsWith(path),
      action  = (req: HttpRequest) => ResponseUtils.echoResponse(req)
    )
    this.addRoute(rule)
  }

  private def writeResponse(w: HttpResponseWriter, resp: HttpResponse) =  try {
      val httpVersion =  "HTTP/1.0"
      // val w = new HttpResponseWriter(out)
      w.println(s"${httpVersion} ${resp.statusCode} ${resp.statusMessage}")
      // First header is the server name
      w.println("Server: JmHttp")
      // Second header is the content type
      w.println("Content-Type:   " + resp.mimeType)
      // Write the remaining response headers
      resp.headers foreach { case (k, v) =>
        w.println(s"${k}: ${v}")
      }
      // Empty line separating wonse line and header from body
      w.println()
      resp.body match {
        case ResponseBodyText(text)
            => w.print(text)
        case ResponseBodyStream(is)
            => w.copyStream(is)
        case ResponseBodyWriter(writer)
          => writer(w)
        }
  }
  finally w.close()

  /** Accept client socket connection and try to parser HTTP request.
    * It returns None for an invalid request message.
    ******************************************************************/
  private def parseRequest(client: Socket, verbose: Boolean = false): Option[HttpTransaction] = {
    //val client: Socket = ssock.accept()
    logger.fine("Get client socket " + client)
    def getHeaders(sc: java.util.Scanner) = {
      var headers = Map[String, String]()
      var line: String = ""
      logger.fine("Parsing client headers")
      while({line = sc.nextLine(); line} != ""){
        //logger.fine("Request header line = " + line)
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
    if (!sc.hasNextLine()){
      logger.fine("Ignoring empty line request. Client closed.")
      None
    }
    else {
      logger.finer("Reading request line")
      val reqline = sc.nextLine()
      logger.fine("Request line = " + reqline)
      val Array(httpMethod, urlPath, httpVersion) = reqline.split(" ")
      val headers = getHeaders(sc)
      val req = HttpRequest(
        method    = httpMethod,
        path      = urlPath,
        headers   = headers,
        version   = httpVersion,
        address   = client.getInetAddress(),
        inpStream = client.getInputStream()
      )
      val resp = new HttpResponseWriter(client.getOutputStream())
      Some(HttpTransaction(req, resp, this.logger))
    }
  } //------ End of getClientRequest() ----- //


  def serveRequest(tra: HttpTransaction) = {
    val rule: Option[HttpRoute] = routes.find(r => r.matcher(tra.request))
    rule match {
      case None
        => {
          val response = ResponseUtils.error404Response(
            s"Error: resource ${tra.getPath()} not found"
          )
          this.writeResponse(tra.response, response)
        }
      case Some(r: HttpRoute)
          => {
            basicAuthLogin match {
              case None
                  => {
                    val response = r.action(tra.request)
                    this.writeResponse(tra.response, response)
                  }
              case Some((user, pass))
                  =>{
                    val response = ResponseUtils.basicAuth(user, pass, tra.request){r.action _}
                    this.writeResponse(tra.response, response)
                  }
            }
        }
    }
  }
  
  /** Run server handling requests in a thread pool */
  def run(
    port:     Int    = 8080,
    host:     String = "0.0.0.0",
    timeout:  Int    = 2000,
    poolSize: Int    = 5
  ): Unit = {
    import java.util.concurrent.Executors
    val threadPool = Executors.newFixedThreadPool(poolSize)
    ssock.bind(new java.net.InetSocketAddress(host, port), 60)
    logger.info(s"Starting server at host = ${host} and port = ${port}")
    while (true) try {
      logger.fine(s"Server waiting for connection.")
      val client = this.ssock.accept()
      threadPool.execute{() =>
        this.parseRequest(client) foreach { req =>
          logger.info(req.toString())
          this.serveRequest(req)
          logger.fine("Client request served")
        }
      }
    } catch {
      //Continue when a timeout exception happens.
      case ex: java.net.SocketTimeoutException
          => ()
      case ex: java.io.IOException => ex.printStackTrace()
    }
  }


} // ----- Eof class HttpServer ----

object HttpServer{
  def makeSimple() = {
    val logger = java.util.logging.Logger.getLogger("jmhttp")
    new HttpServer(logger)
  }

  def main(array: Array[String]) = {
    val server = makeSimple()

    array.toList match{
      case List("echo") => {
        server.addRouteEcho("/")
      }

      case List("file") => {
        val fileRoute = HttpRoute(
          req => true,
          req => ResponseUtils.fileResponse("/etc/protocols", "text/plain")
        )
        server.addRoute(fileRoute)
      }

      case List("demo1") => {
        server.addRouteRedirect(r => r.path == "/", "/demo1")
        server.addRoutePathGET(req => req.path == "/demo1"){ req =>
          ResponseUtils.textResponse("Hello world I am here!!")
        }
      }
      case List("basicAuth") => {
        server.addRoute(r => true){r =>
          ResponseUtils.basicAuth("user", "pass", r){ req =>
            ResponseUtils.textResponse("User logged OK.! Enjoy your stay.")
          }
        }
      }
      case List("basicAuth2") => {
        server.setLogin(Some(("john", "pass")))
        server.addRouteResponse(r => r.path == "/",
          ResponseUtils.htmlResponse(""" 
          <h1>This is the index</h1>      </br>
          <a href='/files'>User files</a> </br>
          <a href='/data'> User data </a> </br>
          """))
        server.addRouteResponse(r => r.path == "/files",
          ResponseUtils.textResponse("Show user files"))
        server.addRouteResponse(r => r.path == "/data",
          ResponseUtils.textResponse("Show user data"))
      }
      case _ => {
        println("Error: invalid option.")
        System.exit(1)
      }
    }

    server.run(port  = 9090)
  }
}
