package jmhttp.main

import jmhttp.server.HttpServer 

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

    server.run()
  }
}

