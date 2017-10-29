
import java.net.{ServerSocket}



object Utils{

    def getAllFiles(path: String) : Array[java.nio.file.Path] = {
      import java.nio.file.{Files, Paths, Path}
      val root = Paths.get(path)
      Files.walk(root)
        .filter(_.toFile.isFile)
        .toArray
        .map(p => root relativize p.asInstanceOf[java.nio.file.Path])
    }


  def withThread(action: => Unit) = {
    val th = new Thread(() => action)
    th.start()
    th
  }

  def encodeURL(url: String) =
    java.net.URLEncoder.encode(url, "UTF-8")

  def decodeURL(url: String) =
    java.net.URLDecoder.decode(url, "UTF-8")

  val mimeTypes = Map(

    //---- Common text files //
    "css"  -> "text/css",
    "html" -> "text/html",
    "js"   -> "application/x-javascript",
    "txt"  -> "text/plain",
    "sh"   -> "application/x-sh",

    // -- Images -------- //
    "gif"  -> "image/gif",
    "ico"  -> "image/x-icon",
    "jfif" -> "image/jpeg",
    "jpg"  -> "image/jpeg",
    "jpeg" -> "image/jpeg",
    "jpeg" -> "image/jpeg",
    "png"  -> "image/png",
    "qti"  -> "image/x-quicktime",
    "tif"  -> "image/tiff",
    "tiff" -> "image/tiff",

    //--- Audio -----------//
    "mp3"  -> "audio/mpeg",
    "ogg"  -> "audio/vorbis, application/ogg",

    //-- Video -----------//
    "mp4" -> "video/mp4",
    "flv" -> "video/x-flv",
    "3gp" -> "video/3gpp",
    "avi" -> "video/x-msvideo",
    "wmv" -> "video/x-ms-wmv",
    "ogv" -> "video/ogg",
    "webm" -> "video/webm",
    "mov" -> "video/quicktime",
    "movie" -> "video/x-sgi-movie",


    //-- Documents  -----─//
    "pdf"  -> "application/pdf",
    "ptt"  -> "application/mspowerpoint"
  )

  def getMimeType(file: String) =
    file.split("\\.", 2) match {
      case Array(_, ext)
          => mimeTypes get(ext) getOrElse "application/octet-stream"
      case _
          => "application/octet-stream"
    }

}


type HttpHeaders = Map[String, String]

case class HttpRequest(
  method:    String,
  path:      String,
  headers:   Map[String, String],
  version:   String,
  inpStream: java.io.InputStream,
  outStream: java.io.OutputStream,
) {

  val httpVersion = "HTTP/1.0"


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


  def sendDirFileResponse(dirpath: String, fileURL: String, mimeFn: String => String = Utils.getMimeType) = {
    val file  = Utils.decodeURL(fileURL)
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
      out.writeBytes(s"<a href='${urlPath}/${file}'>${file}</a></br></br>" + crlf)
    }
    out.close()
  }




} //----  Eof case class HttpRequest ----- //


case class HttpRoute(
  val matcher: HttpRequest => Boolean,
  val action:  HttpRequest => Unit
)

class HttpServer(port: Int, verbose: Boolean = false){
  import scala.collection.mutable.ListBuffer

  private val ssock  = new ServerSocket(port)
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
      matcher = (req: HttpRequest) => req.method == "GET" && req.path.startsWith(path + "/"),
      action  = (req: HttpRequest) => action(req, req.path.stripPrefix(path + "/"))
    )
    this.addRoute(rule)
  }

  /** Add Http route to serve files from a directory */
  def addRouteDirContents(dirUrl: String, dirPath: String) = {
    this.addRoutePathGET(dirUrl){
      _.sendDirListTransvResponse(dirPath, dirUrl)
    }
    this.addRouteParamGET(dirUrl){ (req: HttpRequest, file: String) =>
      req.sendDirFileResponse(dirPath, file)
    }
  }

  def addRouteRedirect(pred: String => Boolean, url: String) = {
    val rule = HttpRoute(
      matcher = (req: HttpRequest) => pred(req.path),
      action  = (req: HttpRequest) => req.sendRedirect(url)
    )
    this.addRoute(rule)
  }

  /** Accept client socket connection and try to parser HTTP request. */
  def getRequest(verbose: Boolean = false) = {
    val client = ssock.accept()
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

    if (!sc.hasNextLine())
      throw new IllegalArgumentException("Error: empty http request line.")

    val reqline = sc.nextLine()
    if (verbose) println("Request line = " + reqline)
    val Array(httpMethod, urlPath, httpVersion) = reqline.split(" ")
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


  def serveRequest(req: HttpRequest) = {
    val rule = routes.find(r => r.matcher(req))
    rule match {
      case Some(r) => r.action(req)
      case None    => req.send404Response(s"Error: resource ${req.path} not found")
    }
  }

  /** Run server in synchronous way, without threading. */
  def runSync() = while (true) try {
    if (verbose) println("Server: waiting for client connection.")
    val req = this.getRequest()
    if (verbose) println("Server: client has connected")
    this.serveRequest(req)
  } catch {
    case ex: Throwable => ex.printStackTrace()
  }

  /** Run server in async way with threading. */
  def run() = while (true)  {
    if (verbose) println("Server: waiting for client connection.")
    val req    = this.getRequest()
    if (verbose) println("Server: client has connected")
    Utils.withThread{ this.serveRequest(req)}
  }


} // ----- Eof class HttpServer ----

