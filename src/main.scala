package jmhttp.main

import jmhttp.server.HttpServer 
import jmhttp.utils.Utils
import jmhttp.optParse.OptSet 

object Main{

  def exitIfFalse(cond: Boolean, msg: String, exitCode: Int = 1) =
    if(cond) {
      println(msg)
      System.exit(exitCode)
    }

  def dirExists(file: String) =
    new java.io.File(file).isDirectory()
  

  def parseOperand(oper: String) =
    oper.split(":", 2) match {
      case Array(k, v)
          =>  ("/" + k, v)
      case _
          => throw new IllegalArgumentException("Error: invalid shared directory, expected <url>:<path> string")
    }

  def main(args: Array[String]){

    val parser = new OptSet(
      name         = "jmhttp",
      version      = "v1.0",
      description  = "A micro Java/Scala http server to share files in the local network",
      operandsDesc = "[[DIRECTORY] | [URL:DIRECTORY] [URL:DIRECTORY] ...]"
    )

    parser.addOptionInt(
      name       = "port",
      shortName  = "p",
      argName    = "port",
      value      = 8080,
      description = "Port number that the Http server will listen to. Default 8080"
    )

    parser.addOptionStr(
      name       = "host",
      shortName  = null,
      argName    = "hostname",
      value      = "0.0.0.0",
      description = "Host name that the server listens to. Default value 0.0.0.0"
    )

    parser.addOptionFlag(
      name       = "verbose",
      description = "Increase server logging verbosity."
    )

    parser.addOptionFlag(
      name       = "browser",
      shortName  = "b",
      description = "Open server url in default web browser."
    )

    parser.addOptionFlag(
      name = "multiple",
      shortName = "m",
      description = "Share multiple directories specified by url1:/dir1, url2:/dir2 ...")

    try parser.parse(args.toList)
    catch {
      case ex: jmhttp.optParse.OptHandlingException
          => {
            println(ex.getMessage())
            System.exit(1)
          }            
    }

    val port       = parser.getOptAsInt("port")
    val host       = parser.getOptAsStr("host")
    val verbosity  = parser.getOptAsBool("verbose")
    val browserOpt = parser.getOptAsBool("browser")
    val multiple   = parser.getOptAsBool("multiple")

    if (parser.getOperands().isEmpty)
    {
      parser.showHelp()
      System.exit(0)
    }

    val server = new HttpServer(verbose = verbosity)

    server.addRouteDebug("/echo")

    if (!multiple){
      val operands = parser.getOperands()
      val path     = operands.head
      exitIfFalse(operands.size > 1,  "Error: this mode expects only one operand.")
      exitIfFalse(!dirExists(path),  s"Error: directory ${path} doesn't exist.")
      server.addRouteDirNav(parser.getOperands().head, "/")
    }
    else
      try server.addRouteDirsIndex(parser.getOperands() map parseOperand)
      catch {
        case ex: java.lang.IllegalArgumentException
            =>{
              println(ex.getMessage())
              System.exit(0)
            }
      }

    val serverURL =
      Utils.getLocalAddress()
        .map{ addr => s"http://${addr}:${port}"}


    serverURL match {
      case Some(url)
          => println("Server running at: " + url)
      case None
          => println("Error: Server address in local network not found")
    }

    // Open website in the browser 500 ms delay after the server
    // start.
    //
    if (browserOpt)
      Utils.runDealy(500){
        serverURL foreach Utils.openUrl
      }
  

    server.run(port = port, host = host)

  } //-----EoF main() function ----

} // ------ EoF Main object ------

