package jmhttp.main

import jmhttp.server.HttpServer 
import jmhttp.utils.{Utils, NetDiscovery}
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
      name       = "browser",
      shortName  = "b",
      description = "Open server url in default web browser."
    )

    parser.addOptionFlag(
      name = "multiple",
      shortName = "m",
      description = "Share multiple directories specified by url1:/dir1, url2:/dir2 ...")

    parser.addOptionStr(
      name       = "loglevel",
      shortName  = null,
      argName    = "level",
      value      = "INFO",
      description = "Set application log level. [OFF | ALL | FINE | INFO. (Default value INFO)"
    )

    parser.addOptionFlag(
      name       = "no-index",
      shortName  = null,
      description = "Don't render index.html if available in directory listing."
    )    

    parser.addOptionFlag(
      name       = "zeroconf",
      shortName  = null,
      description = "Announce server at local network through bounjour/zeroconf protocol."
    )    


    try parser.parse(args.toList)
    catch {
      case ex: jmhttp.optParse.OptHandlingException
          => {
            println(ex.getMessage())
            System.exit(1)
          }            
    }

    val port       = parser.getOptAsInt  ("port")
    val host       = parser.getOptAsStr  ("host")
    val browserOpt = parser.getOptAsBool ("browser")
    val multiple   = parser.getOptAsBool ("multiple")
    val logLevel   = parser.getOptAsStr  ("loglevel")
    val noIndex    = parser.getOptAsBool ("no-index")
    val zeroconf   = parser.getOptAsBool ("zeroconf")

    if (parser.getOperands().isEmpty)
    {
      parser.showHelp()
      System.exit(0)
    }

    import java.util.logging.{
      Logger, Level => LogLevel,
      ConsoleHandler, LogManager, FileHandler 
    }

    // Set the logging format 
    System.setProperty(
      "java.util.logging.SimpleFormatter.format",
      "[%1$tF %1$tT] [%4$s] - %2$s\n - %5$s %6$s%n\n"
      // "[%1$tF %1$tT] [%4$-7s] %5$s %n"
    )
    

    //val formatter  = new java.util.logging.SimpleFormatter()
    val logger = Logger.getLogger("jmhttp")
    logger.setUseParentHandlers(false)
    val handler = new ConsoleHandler()
    
    logger.addHandler(handler)

    logLevel match {

      case "OFF" => {
        handler.setLevel(LogLevel.OFF)
        logger.setLevel(LogLevel.OFF)
      }

      case "ALL" => {
        handler.setLevel(LogLevel.ALL)
        logger.setLevel(LogLevel.ALL)
      }

      case "INFO" => {
        handler.setLevel(LogLevel.INFO)
        logger.setLevel(LogLevel.INFO)
      }

      case "FINE" => {
        handler.setLevel(LogLevel.INFO)
        logger.setLevel(LogLevel.INFO)
      }

      case _ => {
        println("Error: invalid log setting")
        System.exit(1)
      }
    }

    // val fhandler = new FileHandler("jmhttp.log")
    // logger.addHandler(fhandler)


    val server = new HttpServer(logger)

    server.addRouteDebug("/echo")

    if (!multiple){
      val operands = parser.getOperands()
      val path     = operands.head
      exitIfFalse(operands.size > 1,  "Error: this mode expects only one operand.")
      exitIfFalse(!dirExists(path),  s"Error: directory ${path} doesn't exist.")
      server.addRouteDirNav(parser.getOperands().head, "/", showIndex = !noIndex)
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


    if(zeroconf){
      val intf = NetDiscovery.getActiveInterface()
      intf foreach NetDiscovery.registerService(
        serviceType = "_http._tcp.local",
        serviceName =  "jmhttp server",
        servicePort =  port,
        serviceDesc = "micro server for file sharing."
      )
    }

    server.run(port = port, host = host)

  } //-----EoF main() function ----

} // ------ EoF Main object ------

