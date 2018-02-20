package jmhttp.utils

/** General utility functions */
object Utils{

  val imageExtensionList = List(".png", ".jpeg", ".jpg", ".tiff", ".bmp")

  /**  Return true if file is an image file */
  def isImageFile(file: String) =
    imageExtensionList.exists(ext => file.endsWith(ext))


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
    java.net.URLDecoder.decode(url.replace("+", "%2B"),
    "UTF-8").replace("%2B", "+")


  def urlBuilder(path: String, segment: String) =
    path match {
      case _ if (path == null || path.isEmpty() )
        => "/" + segment
      case _ if (path.endsWith("/"))
          => path + segment
      case _
          => path + "/" + segment
    }

  /** Check if running on Windows */
  def osIsWindows() =
    System.getProperty("os.name").toLowerCase.startsWith("windows")

  /** Expand path to absolute path. 
      -> Expand dot (.) into current directory or $(pwd) on Unix, 
      -> Expand tilde (~) into user home directory. 
      -> Relative paths are expanded to absolute path. 
    ,*/
  def expandPath(path: String) = {
    val p1 = path
      .replace("^\\.", System.getProperty("user.dir"))
      .replace("~", System.getProperty("user.home"))
    val p2 = if(osIsWindows())
      p1.replace("\\", "/")
    else
      p1
    new java.io.File(p2).getAbsolutePath()
  }


  /** Try get IP address of current machine rejecting loopback,
    * inactive, and virtual network interfaces.
    */
  def getLocalAddress(): Option[String] = {
    import java.net.InetAddress
    import java.net.NetworkInterface
    import collection.JavaConverters._
    def getInterfaceAddress(net: NetworkInterface) = {
      net.getInterfaceAddresses()
        .asScala
        .toSeq
        .find(inf => inf.getBroadcast() != null)
        .map(_.getAddress().getHostAddress())
    }
    val interfaces = NetworkInterface
      .getNetworkInterfaces()
      .asScala
      .toSeq
    interfaces.filter(ni =>
           !ni.getName.startsWith("vboxnet") // Exclude virtualbox virutal interfaces
        && !ni.isLoopback()                  // Exclude loopback interfaces
        && ni.isUp()                         // Select active interface
        && ni.getHardwareAddress() != null
        && getInterfaceAddress(ni).isDefined
    )
      .map(ni => getInterfaceAddress(ni).get)
      .headOption
  } // ----- EOF function getLocalAddress() -------- //


  /** Open some URL with default system's browser. */
  def openUrl(uri: String){
    import java.awt.Desktop
    import java.io.IOException
    import java.net.URI
    import java.net.URISyntaxException
    val u = new URI(uri)
    val desktop = Desktop.getDesktop()
    desktop.browse(u)
  }


  def runDealy(delay: Long)(action: => Unit) = {
    val timer = new java.util.Timer()
    val task = new java.util.TimerTask{
      def run() = action
    }
    // time X 1000 milliseconds
    timer.schedule(task, delay)
  }


  val mimeTypes = Map(

    //--- Programming Source Codes, markups and data files.
    //
    // Those formats will be viewed in the browser instead of being
    // downloaded.
    // 
    "c"          -> "text/plain",  // C source code 
    "h"          -> "text/plain",  // C header file 
    "cpp"        -> "text/plain",  // C++ file 
    "hpp"        -> "text/plain",  // C++ header 
    "scala"      -> "text/plain",  // Scala code
    "java"       -> "text/plain",  // Java code
    "pom"        -> "text/plain",  // java POM xml 
    "properties" -> "text/plain",  // Java property file

    "py"         -> "text/plain",  // Python code
    "rb"         -> "text/plain",  // Ruby code
    "m"          -> "text/plain",  // Matlab/Octave code
    "hs"         -> "text/plain",  // Haskell code
    "fsx"        -> "text/plain",  // F# / Fsharp script
    "fs"         -> "text/plain",  // F# / Fsharp code
    "cs"         -> "text/plain",  // C# / Code 
    "desktop"    -> "text/plain",  // *.desktop Linux file 
    "sh"         -> "text/plain",  // Unix shell script file 
    "csh"        -> "text/plain",  // BSD shell script 
    "ml"         -> "text/plain",  // OCaml code 
    "el"         -> "text/plain",  // Emacs/Elisp source code
    "org"        -> "text/plain",  // Emacs/Elisp org-mode file.
    "md"         -> "text/plain",  // Markdown file
    "rst"        -> "text/plain",
    "tex"        -> "text/plain",
    "csv"        -> "text/plain",
    "conf"       -> "text/plain",
    "config"     -> "text/plain",
    "ini"        -> "text/plain",
    "log"        -> "text/plain",

    //---- Common text files //

    "json"       -> "application/json",
    "vcard"      -> "text/vcard",
    "vcf"        -> "text/vcf",


    "css"        -> "text/css",
    "html"       -> "text/html",
    "js"         -> "application/x-javascript",
    "txt"        -> "text/plain",

    // -- Images -------- //
    "gif"        -> "image/gif",
    "ico"        -> "image/x-icon",
    "jfif"       -> "image/jpeg",
    "jpg"        -> "image/jpeg",
    "jpeg"       -> "image/jpeg",
    "jpeg"       -> "image/jpeg",
    "png"        -> "image/png",
    "qti"        -> "image/x-quicktime",
    "tif"        -> "image/tiff",
    "tiff"       -> "image/tiff",

    //--- Audio -----------//
    "mp3"        -> "audio/mpeg",
    "ogg"        -> "audio/vorbis, application/ogg",

    //-- Video -----------//
    "mp4"        -> "video/mp4",
    "flv"        -> "video/x-flv",
    "3gp"        -> "video/3gpp",
    "avi"        -> "video/x-msvideo",
    "wmv"        -> "video/x-ms-wmv",
    "ogv"        -> "video/ogg",
    "webm"       -> "video/webm",
    "mov"        -> "video/quicktime",
    "movie"      -> "video/x-sgi-movie",

    //--- Archives -------//
    "rar"        -> "application/x-rar-compressed",
    "tar"        -> "application/x-tar",
    "zip"        -> "application/zip",
    "7z"         -> "application/x-7z-compressed",

    //--- Executable ----//
    "jar"        -> "application/java-archive",
    "war"        -> "application/java-archive",
    "ear"        -> "application/java-archive", 
    "mkpg"       -> "application/vnd.apple.installer+xml",
    "jnlp"       -> "application/x-java-jnlp-file",
    "rpm"        -> "application/x-redhat-package-manager",

    //-- Documents  -----─//
    "pdf"        -> "application/pdf",
    "epub"       -> "application/epub+zip",
    "eps"        -> "application/postscript",
    "ps"         -> "application/postscript",
    "man"        -> "application/x-troff-man",

    ".doc"       ->  "application/msword",
    "docx"       ->    "application/vnd.openxmlformats-officedocument.wordprocessingml.documen",
    "docm"       ->    "application/vnd.ms-word.document.macroEnabled.12",
    "dotm"       ->    "application/vnd.ms-word.template.macroEnabled.12",
    "xls"        ->     "application/vnd.ms-excel",
    "xlt"        ->     "application/vnd.ms-excel",
    "xla"        ->     "application/vnd.ms-excel",

    "xlsx"       ->    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "xltx"       ->    "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
    "xlsm"       ->    "application/vnd.ms-excel.sheet.macroEnabled.12",
    "xltm"       ->    "application/vnd.ms-excel.template.macroEnabled.12",
    "xlam"       ->    "application/vnd.ms-excel.addin.macroEnabled.12",
    "xlsb"       ->    "application/vnd.ms-excel.sheet.binary.macroEnabled.12",

    "ppt"        ->     "application/vnd.ms-powerpoint",
    "pot"        ->     "application/vnd.ms-powerpoint",
    "pps"        ->     "application/vnd.ms-powerpoint",
    "ppa"        ->     "application/vnd.ms-powerpoint",
    "pptx"       ->    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "potx"       ->    "application/vnd.openxmlformats-officedocument.presentationml.template",
    "ppsx"       ->    "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
    "ppam"       ->    "application/vnd.ms-powerpoint.addin.macroEnabled.12",
    "pptm"       ->    "application/vnd.ms-powerpoint.presentation.macroEnabled.12",
    "potm"       ->    "application/vnd.ms-powerpoint.template.macroEnabled.12",
    "ppsm"       ->    "application/vnd.ms-powerpoint.slideshow.macroEnabled.12",

    "odp"        -> "application/vnd.oasis.opendocument.presentation",
    "ods"        -> "application/vnd.oasis.opendocument.spreadsheet",

    //--- Misc -------------//
    "pem"        -> "application/x-x509-ca-cert"

  )

  def getMimeType(file: String) = {
    val fname = new java.io.File(file).getName()
    val ext   = fname.split("\\.").last.toLowerCase
    mimeTypes get(ext) getOrElse "application/octet-stream"  
  }

} /* ------- End of object Utils ----------- */


/** 
    Wrapper over jmDNS open source libary to make the server
    discoverable through Bounjour/Zeroconf protocol at local network.
*/
object NetDiscovery {

  import javax.jmdns.ServiceInfo
  import javax.jmdns.JmDNS
  import java.net.InetAddress
  import java.net.NetworkInterface
  import javax.jmdns.ServiceEvent
  import javax.jmdns.ServiceListener
  import collection.JavaConverters._

  /** Try get name of active network interface. */
  def getActiveInterface(): Option[String] = {
    val interfaces = NetworkInterface
      .getNetworkInterfaces()
      .asScala
      .toSeq
    interfaces.find(ni =>
      !ni.getName.startsWith("vboxnet") 
        && !ni.isLoopback()             
        && ni.isUp()                    
        && ni.getHardwareAddress() != null
    ).map(_.getName())
  } // ----- EOF function getLocalAddress() -------- //


  def registerService (  
    serviceType:  String,
    serviceName:  String,
    servicePort:  Int,
    serviceDesc:  String) = (interface: String) => {

    def getInterfaceAddress(net: NetworkInterface) =
      net.getInterfaceAddresses()
        .asScala
        .toSeq
        .find(inf => inf.getBroadcast() != null)
        .map(_.getAddress())

    val addr = Option(NetworkInterface.getByName(interface))
      .flatMap(getInterfaceAddress _)
    val jm = addr map JmDNS.create
    val info = ServiceInfo.create(serviceType, serviceName, servicePort, serviceDesc);
    jm match {
      case Some(x: javax.jmdns.JmDNS) => x.registerService(info)
      case None    => throw new java.io.IOException("Error: Network interface not found.")
    }
  }


}
