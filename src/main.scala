package jmhttp.main

import jmhttp.server.HttpServer 
import jmhttp.utils.Utils
import jmhttp.optParse.OptSet 

object Main{

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
      operandsDesc = "URL:DIRECTORY [URL:DIRECTORY] ..."
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
      description = "Open server url in default web browser."
    )

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

    if (parser.getOperands().isEmpty)
    {
      parser.showHelp()
      System.exit(0)
    }

    val server = new HttpServer(verbose = verbosity)

    server.addRouteDebug("/echo")

    try server.addRouteDirsIndex(parser.getOperands() map parseOperand)
    catch {
      case ex: java.lang.IllegalArgumentException
          =>{
            println(ex.getMessage())
            System.exit(0)
          }
    }

    // Open website in the browser 500 ms delay after the server
    // start.
    //
    if (browserOpt)
      Utils.runDealy(500){
        Utils
          .getLocalAddress()
          .foreach { url =>
          Utils.openUrl("http://" + url + ":" + port)
        }
      }

    server.run(port = port, host = host)

  } //-----EoF main() function ----

} // ------ EoF Main object ------

