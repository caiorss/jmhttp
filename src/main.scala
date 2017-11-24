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
          =>  ("/" + k, Utils.expandPath(v))
      case _
          => throw new IllegalArgumentException("Error: invalid shared directory, expected <url>:<path> string")
    }

  def main(args: Array[String]){

    val parser = new OptSet(
      name         = "jmhttp",
      version      = "v1.1",
      description  = "A micro Java/Scala http server to share files in the local network",
      operandsDesc = "[[DIRECTORY] | [URL:DIRECTORY] [URL:DIRECTORY] ...]",
      exampleText = """
Examples:

  Share single directory /home/user/Documents at default port 8080
  listening all hosts.

   > $ jmhttp /home/user/Documents
  or
   > $ jmhttp ~/Documents

  Share single address and open server's address with system's default web browser.

  > $ jmhttp --browser ~/Documents
  or
  > $ jmhttp -b ~/Documents

  Share single directory using port 9090 - The server can be accessed at url
  http://localhost:9090 or http://<server addr>:9090

   > $ jmhttp --port=9090 /home/user/Documents
   > $ jmhttp -p=9090 /home/user/Documents

  Share multiple directories using port 8090 and announcing server
  through mDNS multicast DNS Discovery service, aka Apple's Bounjour(®)
  or Zeroconf. It will make the directory Documents available at
  http:<addr>:8090/docs and ~/Pictures at http:<addr>:8090/pics. Note:
  the flag (-m) or (--multiple) enables serving multiple directories.

  > $ jmthtp -p=8090 --zeroconf -m docs:~/Documents pics:~/Pictures

  Share multiple directories with tsl/ssl (Transport Socket Layer/
  Secure Socket Layer) encryption. It changes the server's URL to
  https://<serveraddr>:8080. It is no longer http://...

  > $ jmhttp -p=8080 --tsl=CertificateFile.jks:password -m docs:~/Documents pics:~/Pictures

  The certificate can be generated using:

  > $ keytool -gerkey -keyalg RSA -alias sec_server \
    -keystore CertificateFile.jks \
    -storepass chargeit -validity 1000000 -keysize 2048

"""
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

    parser.addOptionStrOpt(
      name        = "tsl",
      shortName   = null,
      argName     = "<key store>:<password>",
      description = "Enable TLS/SSL. If it enabled use https:<addr>:<port> to connect.",
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
      description = "Publish server at local network with bounjour/zeroconf protocol."
    )    



    try parser.parse(args.toList)
    catch {
      case ex: jmhttp.optParse.OptHandlingException
          => {
            println(ex.getMessage())
            System.exit(1)
          }            
    }

    val port       = parser.getOptAsInt    ("port")
    val host       = parser.getOptAsStr    ("host")
    val browserOpt = parser.getOptAsBool   ("browser")
    val multiple   = parser.getOptAsBool   ("multiple")
    val logLevel   = parser.getOptAsStr    ("loglevel")
    val tslConf    = parser.getOptAsStrOpt ("tsl")
    val noIndex    = parser.getOptAsBool   ("no-index")
    val zeroconf   = parser.getOptAsBool   ("zeroconf")

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

    tslConf foreach { conf =>
      conf.split(":") match { 
        case Array(keystore, passwd)
            => {
              System.setProperty("javax.net.ssl.keyStore", Utils.expandPath(keystore))
              System.setProperty("javax.net.ssl.keyStorePassword", passwd)
            }
        case _
            => {
              println("Error: Malformed TSL certificate configuration.")
              println("Expected <keystore>:<password>")
              System.exit(1)
            }
      }
    }
   

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


    val server = new HttpServer(logger, !tslConf.isEmpty)

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
        .map{ addr => if(tslConf.isEmpty)
          s"http://${addr}:${port}"
        else
          s"https://${addr}:${port}"
      }

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
        serviceType = if(tslConf.isEmpty) 
          "_.https._tcp.local"
          else
            "_http._tcp.local",
        serviceName =  "jmhttp server",
        servicePort =  port,
        serviceDesc = "micro server for file sharing."
      )
    }

    server.run(port = port, host = host)

  } //-----EoF main() function ----

} // ------ EoF Main object ------

