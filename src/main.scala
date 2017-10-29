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

    val server = new HttpServer(port = 8080, verbose = true)

    println(args)

    for (a <- args) a.split(":") match {
      case Array(url, path) => server.addRouteDirContents("/" + url, path)
    }

    server.runSync()
  }
}

