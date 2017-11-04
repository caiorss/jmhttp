package jmhttp.utils

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
      !ni.isLoopback()          // Exclude loopback interfaces
        && ni.isUp()            // Select active interface
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

  def joinPathsAsURls(list: String*) = {
    val file = list.foldLeft(null: java.io.File){(acc, x)
      =>  new java.io.File(acc, x)
    }
    val pathSep =  System.getProperty("path.separator")
    assert(pathSep != null, "Path separator not supposed to be null")
    // If path separator is ';', the system is Windows, otherwise it is Windows.
    if (pathSep == ";")
      file.getAbsolutePath().replace("\\", "/")
    else
      file.getAbsolutePath
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

  def getMimeType(file: String) =
    file.split("\\.", 2) match {
      case Array(_, ext)
          => mimeTypes get(ext) getOrElse "application/octet-stream"
      case _
          => "application/octet-stream"
    }

}
