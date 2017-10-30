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

  val mimeTypes = Map(

    //---- Common text files //
    "csv"  -> "text/csv",
    "json" -> "application/json",

    "vcard" -> "text/vcard",
    "vcf"   -> "text/vcf",


    "css"  -> "text/css",
    "html" -> "text/html",
    "js"   -> "application/x-javascript",
    "txt"  -> "text/plain",
    "sh"   -> "application/x-sh",
    "csh"  -> "application/x-csh",


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

    //--- Archives -------//
    "rar" -> "application/x-rar-compressed",
    "tar" -> "application/x-tar",
    "zip" -> "application/zip",
    "7z"  -> "application/x-7z-compressed",

    //--- Executable ----//
    "jar"  -> "application/java-archive",
    "war"  -> "application/java-archive",
    "ear"  -> "application/java-archive", 
    "mkpg" -> "application/vnd.apple.installer+xml",
    "jnlp" -> "application/x-java-jnlp-file",
    "rpm"  -> "application/x-redhat-package-manager",

    //-- Documents  -----─//
    "pdf"  -> "application/pdf",
    "epub" -> "application/epub+zip",
    "eps"  -> "application/postscript",
    "ps"   -> "application/postscript",
    "man"  -> "application/x-troff-man",

    "ppt"  -> "application/mspowerpoint",
    "odp"  -> "application/vnd.oasis.opendocument.presentation",
    "ods"  -> "application/vnd.oasis.opendocument.spreadsheet",

    //--- Misc -------------//
    "pem"  -> "application/x-x509-ca-cert"

  )

  def getMimeType(file: String) =
    file.split("\\.", 2) match {
      case Array(_, ext)
          => mimeTypes get(ext) getOrElse "application/octet-stream"
      case _
          => "application/octet-stream"
    }

}
