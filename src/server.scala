package jmhttp.server

import java.net.{InetAddress, ServerSocket, Socket}
import jmhttp.utils.Utils

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
  outStream: java.io.OutputStream
) {

  val httpVersion = "HTTP/1.0"

  type HttpHeaders = Map[String, String]

  def print() =
    println(s"\n${new java.util.Date()} - path = ${this.path} - method = ${this.method} - address = ${this.address}")

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
      println("Written header - " + s"${k}: ${v}")
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


  def sendFileResponse(
    file:     String,
    mimeType: String      = "application/octet-stream",
    headers:  HttpHeaders = Map[String, String]()
  ) = {
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


  def sendDirectoryNavListResponse(rootPath: String, dirPath: String, urlPath: String) = {
    def relativePath(root: String, path: String): String = {
      import java.nio.file.{Paths, Path}
      val rp = Paths.get(root)
      val p  = Paths.get(path)
      rp.relativize(p).toString
    }
    val contents = new java.io.File(dirPath).listFiles
    val files = contents.filter(_.isFile).map(_.getName)
    val dirs  = contents.filter(_.isDirectory).map(_.getName)
    withResponseLen(){ resp =>
      resp.writeLine(s"<h1>Contents of ${urlPath}/${relativePath(rootPath, dirPath)}</h1>")
      resp.writeLine("<h2>Directories</h2>")
      dirs foreach { dir =>
        if (urlPath == "/")
          resp.writeLine(s"<a href='/${dir}'>${dir}</a></br></br>")
        else
          resp.writeLine(s"<a href='${urlPath}/${dir}'>${dir}</a></br></br>")
      }
      resp.writeLine("<h2>Files</h2>")
      files foreach { file =>
        if (urlPath == "/")
          resp.writeLine(s"<a href='/${file}'>${file}</a></br></br>")
        else
          resp.writeLine(s"<a href='${urlPath}/${file}'>${file}</a></br></br>")
      }
    }
  }


  def sendDirNavResponse(
    dirPath: String,
    urlPath: String,
    fileURL: String,
    mimeFn:  String => String = Utils.getMimeType,
    showIndex: Boolean = true 
  ) = {

    // Secure against web server against Attacks Based On File and Path Names
    // See: https://www.w3.org/Protocols/rfc2616/rfc2616-sec15.html
    //
    val fileName = Utils.decodeURL(fileURL).replace("..", "")
    val file = new java.io.File(dirPath, fileName)

    // println("-----------------------------")
    // println("dirPath = " + dirPath)
    // println("urlPath = " + urlPath)
    // println("fileURL = " + fileURL)
    // println("file    = " + file)
    // println("-----------------------------")

    file match {
      case _ if !file.exists()
          => this.send404Response(s"Error: file or directory ${fileURL} not found.")

      case _ if file.isFile()
          =>  this.sendFileResponse(file.getAbsolutePath, mimeFn(file.getAbsolutePath))

      case _ if file.isDirectory()
          => {
            val index = new java.io.File(file, "index.html")
            if (showIndex && index.isFile())
              this.sendRedirect(new java.io.File(urlPath, fileName).toString + "/" + "index.html")              
            else            
              this.sendDirectoryNavListResponse(
                dirPath,
                file.getAbsolutePath,
                new java.io.File(urlPath, fileName).toString
              )
          }    
    }
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

  def addRouteRedirect(pred: String => Boolean, url: String) = {
    val rule = HttpRoute(
      matcher = (req: HttpRequest) => pred(req.path),
      action  = (req: HttpRequest) => req.sendRedirect(url)
    )
    this.addRoute(rule)
  }

  def addRouteDirNav(dirPath: String, urlPath: String) = {
    this.addRouteParamGET(urlPath){ (req: HttpRequest, fileURL: String) =>
      println("File URL = " + fileURL)
      req.sendDirNavResponse(dirPath, urlPath, fileURL)
    }
  }

  def addRouteDirsIndex(urlPaths: Seq[(String, String)]) = {

    val indexPage = urlPaths.foldLeft(""){ (acc, tpl) =>
      val (dirUrl, dirPath) = tpl
      acc + "\n" + s"Directory: <a href='${dirUrl}'>${dirUrl}</a></br></br>"
    }

    this.addRoutePathGET("/"){
      val pageHeader = "<h1>Shared Directories</h1></br>\n"
      _.sendTextResponse(pageHeader + indexPage, mimeType = "text/html")
    }

    urlPaths foreach { case (dirUrl, dirPath) =>
      this.addRouteDirNav(dirPath, dirUrl)
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

