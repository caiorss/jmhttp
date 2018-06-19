package jmhttp.server

import java.net.{InetAddress, ServerSocket, Socket}
import jmhttp.utils.{Utils, ImageUtils}

class HttpResponse(outStream: java.io.OutputStream){

  private val out = new java.io.DataOutputStream(outStream)
  private val crlf = "\r\n"

  def writeLine(line: String = "") = {
    out.writeBytes(line + crlf)
  }

  def writeLines(lines: String*) = {
    lines foreach this.writeLine
  }

  def writeHtmlLine(line: String = "") = {
    out.writeBytes(line + "</br>\n" + crlf)
  }

  def writeText(text: String) = {
    out.writeBytes(text)
  }

  def writeStream(from: java.io.InputStream, size: Int = 1024) = {
    val to = this.out
    // number of bytes read
    var n = 0
    // Buffer with 1024 bytes or 1MB
    val buf = new Array[Byte](size)
    while( {n = from.read(buf) ; n} > 0 ){
      to.write(buf, 0, n)
    }
  }

  def close() = out.close()

}

case class HttpRequest(
  method:    String,
  path:      String,
  headers:   Map[String, String],
  version:   String,
  address:   InetAddress,
  inpStream: java.io.InputStream,
  outStream: java.io.OutputStream,
  logger:    java.util.logging.Logger
) {

  val httpVersion = "HTTP/1.0"

  type HttpHeaders = Map[String, String]

  override def toString() =
    s"HTTP Request: path = ${this.path} - method = ${this.method} - address = ${this.address}"

  def withResponse(
    status:    Int          = 200,
    statusMsg: String       = "OK",
    mimeType:  String       = "text/html",
    headers:   HttpHeaders  = Map[String, String]()
  )(fn: HttpResponse => Unit) = {
    val resp = new HttpResponse(this.outStream)

    // Write response line 
    resp.writeLine(s"${httpVersion} ${status} ${statusMsg}")

    // Write HTTP Headers 
    //-------------------------   
    resp.writeLine("Content-Type:   " + mimeType)
    headers foreach { case (k, v) =>
      resp.writeLine(s"${k}: ${v}")
    }

    // Empty line separating response line and header from body 
    resp.writeLine()

    fn(resp)
    resp.close()
  }

  def withResponseLen(
    status:    Int          = 200,
    statusMsg: String       = "OK",
    mimeType:  String       = "text/html",
    headers:   HttpHeaders  = Map[String, String](),
    contentLen: Long         = -1,
  )(fn: HttpResponse => Unit) = {
    val resp = new HttpResponse(this.outStream)
    // Write response line 
    resp.writeLine(s"${httpVersion} ${status} ${statusMsg}")
    // Write HTTP Headers 
    //-------------------------   
    resp.writeLine("Content-Type:   " + mimeType)
    if (contentLen > 0)
      resp.writeLine("Content-Length: " + contentLen)

    headers foreach { case (k, v) =>
      // println("Written header - " + s"${k}: ${v}")
      resp.writeLine(s"${k}: ${v}")
    }
    // Empty line separating response line and header from body 
    resp.writeLine()   
    fn(resp)
    resp.close()
  }

    

  /** Send response with http request parameters for debugging. */
  def sendDebugResponse() =
    withResponse(mimeType = "text/plain"){ resp =>
      resp.writeLine("Method =  " + method)
      resp.writeLine("Path   =  " + path)
      resp.writeLine()
      resp.writeLine("headers = ")
      headers foreach { case (k, v) =>
        resp.writeLine(s"${k}\t=\t${v}")
      }
    }
  

  def sendTextResponse(
    text: String,
    status: Int = 200,
    statusMsg: String = "Ok",
    mimeType: String = "text/plain",
    headers: HttpHeaders = Map[String, String]()
  ) = withResponseLen(
    status = status,
    statusMsg = statusMsg,
    mimeType = mimeType,
    contentLen = text.getBytes("UTF-8").length,
    headers = headers,
  ) { req =>
    req.writeText(text)
  }

  def send404Response(text: String) = {
    this.sendTextResponse(text, 404, "NOT FOUND")
  }

  def sendRedirect(url: String) =
    this.sendTextResponse(
      "Moved to " + url,
      302,
      "MOVED PERMANENTLY",
      headers = Map("Location" -> url)
    )

  def sendBasicAuth(user: String, passwd: String)(action: HttpRequest => Unit) = {
    val secret =
      java.util.Base64
        .getEncoder()
        .encodeToString((user + ":" + passwd).getBytes("UTF-8"))

    def denyAccess() =
      this.sendTextResponse(
        "Unauthorized Access",
        401,
        "UNATHORIZED",
        headers = Map("Www-Authenticate" -> "Basic realm=\"Fake Realm\"")
      )

    val auth = this.headers.get("Authorization")
    auth match {
      case None
          => denyAccess()
      case Some(a)
          =>
        if (a == "Basic " + secret)
          action(this)
        else
          denyAccess()        
    }
  }

  def sendFileResponse(
    file:     String,
    mimeType: String      = "application/octet-stream",
    headers:  HttpHeaders = Map[String, String]()
  ) = {
    logger.fine(s"Sending HTTP Response file: $file - mime type = $mimeType " )
    try {
      val inp = new java.io.FileInputStream(file)
      val fileSize = new java.io.File(file).length()
      withResponseLen(
        mimeType   = mimeType,
        contentLen = fileSize,
      ){ resp =>
        resp.writeStream(inp)
        inp.close()
      }     
    } catch {
      case ex: java.io.FileNotFoundException
          => this.send404Response("Error: file not found in the server.")
    }
  }


  def sendDirectoryNavListResponse(
    rootPath: String,
    dirPath: String,
    urlPath: String,
    showImage: Boolean = false
  ) = {
    def relativePath(root: String, path: String): String = {
      import java.nio.file.{Paths, Path}
      val rp = Paths.get(root)
      val p  = Paths.get(path)
      rp.relativize(p).toString
    }

    logger.fine(s"Directory navigation - rootPath = $rootPath, dirPath = $dirPath, urlPath = $urlPath ")

    val contents = new java.io.File(dirPath).listFiles
    val files = contents.filter(_.isFile).map(_.getName)
    val dirs  = contents.filter(_.isDirectory).map(_.getName)
    withResponseLen(){ resp =>
      val p =  Utils.urlBuilder(urlPath, relativePath(rootPath, dirPath))
      resp.writeLine(s"<h1>Contents of $p</h1>")
      resp.writeLine("<h2>Directories</h2>")
      dirs foreach { dir =>
        val url = Utils.urlBuilder(urlPath, dir)
        logger.fine(s"DEBUG urlPath = $urlPath ; dir = $dir ; url = ${url} ")
        resp.writeLine(s"<a href='$url'>$dir</a></br></br>")
      }
      resp.writeLine("<h2>Files</h2>")
      files foreach { file =>
        val url = Utils.urlBuilder(urlPath, file)
        resp.writeLine(s"<a href='$url'>${file}</a></br></br>")

        if(showImage && Utils.isImageFile(file)){
          //resp.writeLine(s"<img src='$url' style='max-height: 200px; max-width= 200px;' /></br></br>")
          val fileAbs = new java.io.File(dirPath, file).getPath()
          logger.fine("Image file served = " + fileAbs)
          val img = ImageUtils.scaleFitZoom(
            ImageUtils.readFile(fileAbs),
            400,
            400,
            1.0
          )
          val b64encode = ImageUtils.toBase64String(img)
          resp.writeLine(s"<img src='data:image/png;base64,$b64encode' style='max-height: 200px; max-width= 200px;' /></br></br>")
        }
      }
    }
  }


  def sendDirNavResponse(
    dirPath: String,
    urlPath: String,
    fileURL: String,
    mimeFn:  String => String = Utils.getMimeType,
    showIndex: Boolean = true,
    showImage: Boolean = false
  ) = {

    this.logger.fine(s"sendDirNavResponse (dirPath = $dirPath, urlPath = $urlPath, fileURL = $fileURL ...) ")

    // Secure against web server against Attacks Based On File and Path Names
    // See: https://www.w3.org/Protocols/rfc2616/rfc2616-sec15.html
    //
    val fileName = Utils.decodeURL(fileURL).replace("..", "")
    val file = new java.io.File(dirPath, fileName)

    this.logger.fine(s"sendDirNavResponse -> fileName = $fileName, file = $file")

    file match {
      case _ if !file.exists()
          => this.send404Response(s"Error 404: file or directory <${fileURL}> not found.")

      case _ if file.isFile()
          =>  this.sendFileResponse(file.getAbsolutePath, mimeFn(file.getAbsolutePath))

      case _ if file.isDirectory()
          => {
            val index = new java.io.File(file, "index.html")
            if (showIndex && index.isFile())
              this.sendRedirect(Utils.urlBuilder(Utils.urlBuilder(urlPath, fileName), "index.html"))
              //this.sendRedirect(new java.io.File(urlPath, fileName).toString + "/" + "index.html")
            else{
              val p = Utils.urlBuilder(urlPath, fileName)
              logger.fine(s"SendDirNavResponse - urlPath = $urlPath - fileName = $fileName")
              logger.fine("Listing directory " + p)
              this.sendDirectoryNavListResponse(
                rootPath = dirPath,
                dirPath  = file.getAbsolutePath,
                urlPath  = p,
                showImage
              )
            }
          }    
    }
  }


} //----  Eof case class HttpRequest ----- //

/** Generic http route
  * @param matcher - Predicate which matches the HTTP request
  * @param action  - Action that writes the response when the HTTP request is matched.
  **/
case class HttpRoute(
  matcher: HttpRequest => Boolean,
  action:  HttpRequest => Unit
)



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

  /** Socket server */
  private val ssock = if (tsl) {
    val ss = javax.net.ssl.SSLServerSocketFactory.getDefault()
    ss.createServerSocket()
  } else
    new ServerSocket()

  private val routes = ListBuffer[HttpRoute]()

  init()

  private def init(){
    
  }

  /** Add a generic route to the HTTP Server */
  def addRoute(route: HttpRoute) =
    routes.append(route)

  /** Add a route with get request /{route} GET, for instance http://192.168.0.1/books */
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

  def addRouteRedirect(pred: String => Boolean, url: String) = {
    val rule = HttpRoute(
      matcher = (req: HttpRequest) => pred(req.path),
      action  = (req: HttpRequest) => req.sendRedirect(url)
    )
    this.addRoute(rule)
  }

  def addRouteDirNav(
    dirPath: String,
    urlPath: String,
    showIndex: Boolean = true,
    showImage: Boolean = false
  ) = {
    this.addRouteParamGET(urlPath){ (req: HttpRequest, fileURL: String) =>
      // println("File URL = " + fileURL)
      logger.fine(s"Setting up route: addRouteDirNav(dirPath = $dirPath, urlPath = $urlPath )")
      req.sendDirNavResponse(dirPath, urlPath, fileURL, showIndex = showIndex, showImage = showImage)
    }
  }

  def addRouteDirsIndex(
    urlPaths: Seq[(String, String)],
    showIndex: Boolean = true,
    showImage: Boolean = false
  ) = {

    val indexPage = urlPaths.foldLeft(""){ (acc, tpl) =>
      val (dirUrl, dirPath) = tpl
      acc + "\n" + s"Directory: <a href='${dirUrl}'>${dirUrl}</a></br></br>"
    }

    this.addRoutePathGET("/"){
      val pageHeader = "<h1>Shared Directories</h1></br>\n"
      _.sendTextResponse(pageHeader + indexPage, mimeType = "text/html")
    }

    urlPaths foreach { case (dirUrl, dirPath) =>
      this.addRouteDirNav(dirPath, dirUrl, showIndex = showIndex, showImage)
    }
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
        inpStream = client.getInputStream(),
        outStream = client.getOutputStream(),
        logger    = this.logger
      )
      Some(req)
    }
  } //------ End of getClientRequest() ----- //


  def serveRequest(req: HttpRequest) = {
    val rule = routes.find(r => r.matcher(req))
    rule match {
      case Some(r)
          =>
        login match{
          case None
              => r.action(req)            
          case Some((user, pass))
              => req.sendBasicAuth(user, pass){ r.action }
        }

      case None
          => req.send404Response(s"Error: resource ${req.path} not found")
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

