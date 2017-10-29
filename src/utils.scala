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
