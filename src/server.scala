package jmhttp.server

import java.net.{InetAddress, ServerSocket, Socket}
import jmhttp.utils.Utils

case class HttpRequest(
  method:    String,
  path:      String,
  headers:   Map[String, String],
  version:   String,
  address:   InetAddress,
  inpStream: java.io.InputStream,
  outStream: java.io.OutputStream
) {

  val httpVersion = "HTTP/1.0"

  type HttpHeaders = Map[String, String]

  def print() =
    println(s"\n${new java.util.Date()} - path = ${this.path} - method = ${this.method} - address = ${this.address}")

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

  def sendRedirect(url: String) =
    this.sendTextResponse("", 302, "MOVED PERMANENTLY", Map("Location" -> url))


  def sendFileResponse(
    file:     String,
    mimeType: String      = "application/octet-stream",
    headers:  HttpHeaders = Map[String, String]()
  ) = {

    def copyStream(from: java.io.InputStream, to: java.io.OutputStream){
      // number of bytes read
      var n = 0
      // Buffer with 1024 bytes or 1MB
      val buf = new Array[Byte](1024)
      while( {n = from.read(buf) ; n} > 0 ){
        to.write(buf, 0, n)
      }
    }

    try {
      val inp = new java.io.FileInputStream(file)
      val crlf = "\r\n"
      val out = new java.io.DataOutputStream(outStream)
      val fileSize = new java.io.File(file).length()
      out.writeBytes(s"${httpVersion} 200 OK" + crlf)
      out.writeBytes("Content-Type: " + mimeType + crlf)
      out.writeBytes("Content-Length: " + fileSize + crlf)
      headers foreach { case (k, v) =>
        out.writeBytes(s"${k}: ${v}" + crlf)
      }
      out.writeBytes(crlf)
      copyStream(inp, out)
      inp.close()
      out.close()
    } catch {
      case ex: java.io.FileNotFoundException
          => this.send404Response("Error: file not found in the server.")
    }
  }


  def sendDirFileResponse(
    dirpath: String,
    fileURL: String,
    mimeFn: String => String = Utils.getMimeType
  ) = {

    // Secure against web server against Attacks Based On File and Path Names
    // by removing (..) character and disllowing access to top directories.
    // See: https://www.w3.org/Protocols/rfc2616/rfc2616-sec15.html
    //
    val file  = Utils.decodeURL(fileURL).replace("..", "")
    val fpath = new java.io.File(dirpath, file).getAbsolutePath
    this.sendFileResponse(fpath, mimeFn(file))
  }

  def sendDirListResponse(dirpath: String, urlPath: String) = {
    val crlf = "\r\n"
    val out = new java.io.DataOutputStream(outStream)
    out.writeBytes(s"${httpVersion} 200 OK"  + crlf)
    out.writeBytes("Content-type: text/html" + crlf)
    out.writeBytes(crlf)

    val entries = new java.io.File(dirpath).listFiles
    val files   = entries.filter(_.isFile).map(_.getName)

    files foreach { file =>
      if (urlPath == "/")
        out.writeBytes(s"<a href='/${file}'>${file}</a></br></br>" + crlf)
      else
        out.writeBytes(s"<a href='${urlPath}/${file}'>${file}</a></br></br>" + crlf)
    }
    out.close()
  }

  def sendDirListTransvResponse(dirpath: String, urlPath: String) = {
    val crlf = "\r\n"
    val out = new java.io.DataOutputStream(outStream)
    out.writeBytes(s"${httpVersion} 200 OK"  + crlf)
    out.writeBytes("Content-type: text/html" + crlf)
    out.writeBytes(crlf)
    val files   = Utils.getAllFiles(dirpath) map(_.toString)
    files foreach { file =>
      if (urlPath == "/")
        out.writeBytes(s"<a href='/${file}'>${file}</a></br></br>" + crlf)
      else
        out.writeBytes(s"<a href='${urlPath}/${file}'>${file}</a></br></br>" + crlf)
    }
    out.close()
  }


} //----  Eof case class HttpRequest ----- //


case class HttpRoute(
  val matcher: HttpRequest => Boolean,
  val action:  HttpRequest => Unit
)

class HttpServer(verbose: Boolean = false){
  import scala.collection.mutable.ListBuffer

  private val ssock  = new ServerSocket()
  private val routes = ListBuffer[HttpRoute]()


  def addRoute(route: HttpRoute) =
    routes.append(route)

  def addRoutePathGET(path: String)(action: HttpRequest => Unit) =
    routes.append(HttpRoute(
      matcher = (req: HttpRequest) => req.method == "GET" && req.path == path,
      action  = action
    ))

  def addRouteParamGET(path: String)(action: (HttpRequest, String) => Unit) = {
    val rule = HttpRoute(
      matcher = (req: HttpRequest) => {
        if (path == "/")
          req.method == "GET" &&  req.path.startsWith("/")
        else
          req.method == "GET" &&  req.path.startsWith(path)
      },
      action  = (req: HttpRequest) => action(req, req.path.stripPrefix(path))
    )
    this.addRoute(rule)
  }

  /** Add Http route to serve files from a directory */
  def addRouteDirContents(dirUrl: String, dirPath: String, showIndex: Boolean = true) = {
    this.addRoutePathGET(dirUrl){ req =>
      val index = new java.io.File(dirPath, "index.html")
      if (showIndex && index.isFile())
        req.sendRedirect(dirUrl + "/index.html")
      else
        req.sendDirListTransvResponse(dirPath, dirUrl)
    }
    this.addRouteParamGET(dirUrl){ (req: HttpRequest, file: String) =>
      req.sendDirFileResponse(dirPath, file)
    }
  }


  /** Add Http route to serve files from a directory */
  def addRouteDir(dirUrl: String, dirPath: String) = {
    this.addRoutePathGET(dirUrl){ req =>
      val index = new java.io.File(dirPath, "index.html")
      if (index.isFile())
        req.sendRedirect(dirUrl + "/index.html")
      else
        req.sendDirListResponse(dirPath, dirUrl)
    }
    this.addRouteParamGET(dirUrl){ (req: HttpRequest, file: String) =>
      req.sendDirFileResponse(dirPath, file)
    }
  }
  

  def addRouteDirsIndex(urlPaths: Seq[(String, String)]) = {
    val indexPage = urlPaths.foldLeft(""){ (acc, tpl) =>
      val (dirUrl, dirPath) = tpl
      acc + "\n" + s"Directory: <a href='${dirUrl}'>${dirUrl}</a></br></br>"
    }

    this.addRoutePathGET("/"){
      _.sendTextResponse(indexPage, headers = Map("Content-Type" -> "text/html"))
    }

    urlPaths foreach { case (dirUrl, dirPath) =>
      this.addRouteDirContents(dirUrl, dirPath)
    }
  }

  def addRouteRedirect(pred: String => Boolean, url: String) = {
    val rule = HttpRoute(
      matcher = (req: HttpRequest) => pred(req.path),
      action  = (req: HttpRequest) => req.sendRedirect(url)
    )
    this.addRoute(rule)
  }

  def addRouteDebug(path: String = "/debug") = {
    val rule = HttpRoute(
      matcher = (req: HttpRequest) => req.path.startsWith(path),
      action  = (req: HttpRequest) => req.sendDebugResponse()
    )
    this.addRoute(rule)
  }

  /** Accept client socket connection and try to parser HTTP request
      returning None for an invalid request message.
    */
  def parseRequest(client: Socket, verbose: Boolean = false): Option[HttpRequest] = {

    //val client: Socket = ssock.accept()

    println("Accepted client " + client)

    def getHeaders(sc: java.util.Scanner) = {
      var headers = Map[String, String]()

      var line: String = ""

      println("Getting headers")

      while({line = sc.nextLine(); line} != ""){

        println("Header Line = " + line)

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

    if (!sc.hasNextLine()){
      println("Error: Invalid http request. Client closed")
      None
    }
    else {

      println("Reading request line")
      val reqline = sc.nextLine()

      println("Request line = " + reqline)

      val Array(httpMethod, urlPath, httpVersion) = reqline.split(" ")
      val headers = getHeaders(sc)

      val req = HttpRequest(
        method    = httpMethod,
        path      = urlPath,
        headers   = headers,
        version   = httpVersion,
        address   = client.getInetAddress(),
        inpStream = client.getInputStream(),
        outStream = client.getOutputStream()
      )
      Some(req)
    }
  } //------ End of getClientRequest() ----- //


  def serveRequest(req: HttpRequest) = {
    val rule = routes.find(r => r.matcher(req))
    rule match {
      case Some(r) => r.action(req)
      case None    => req.send404Response(s"Error: resource ${req.path} not found")
    }
  }

  /** Run server in synchronous way, without threading. */
  def runSync(port: Int = 8080, host: String = "0.0.0.0") = {
    ssock.bind(new java.net.InetSocketAddress(host, port), 60)
    while (true) try {
      if (verbose) println("Server: waiting for client connection.")
      val client = this.ssock.accept()
      this.parseRequest(client) foreach { req =>
        if (verbose) println("Server: client has connected")
        this.serveRequest(req)
      }
    } catch {
      case ex: Throwable => ex.printStackTrace()
    }
  }

  /** Run server in async way with threading. */
  def run(port: Int = 8080, host: String = "0.0.0.0", timeout: Int = 2000) = {
    ssock.bind(new java.net.InetSocketAddress(host, port), 60)
    // ssock.setSoTimeout(timeout)
    while (true) try {
      if (verbose) println(s"${new java.util.Date()} - server waiting for connection.")

      val client = this.ssock.accept()

      Utils.withThread{
        this.parseRequest(client) foreach { req =>
          if (verbose) req.print()          
          this.serveRequest(req)
          println("Client request served")
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

