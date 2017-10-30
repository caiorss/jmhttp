package jmhttp.main

import jmhttp.server.HttpServer 
import jmhttp.utils.Utils

object Main{

  def readFile(file: String) = {
    val src = scala.io.Source.fromFile(file)
    val txt = src.mkString
    src.close()
    txt
  }

  def main(args: Array[String]){

    val server = new HttpServer(verbose = true)

    server.addRouteDebug("/echo")

    args.toList match {

      case "--dirlist"::rest 
          => {
            val urlPaths = for {
              a <- rest 
              Array(url, path) = a.split(":", 2)
            } yield ("/" + url, path)

            println(urlPaths)    
            server.addRouteDirsIndex(urlPaths)

          }

      case List("--dir", path)
          => server.addRouteDirContents("/", path)

      case _
          => System.exit(0)
    }

    // Open website in the browser 500 ms delay after the server start.
    //
    Utils.runDealy(500){
      Utils
        .getLocalAddress()
        .foreach { url =>
        Utils.openUrl("http://" + url + ":8080")
      }
    }

    server.run()

  } //-----EoF main() function ----

} // ------ EoF Main object ------

