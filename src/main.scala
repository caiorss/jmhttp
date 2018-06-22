package jmhttp.main

import jmhttp.http.HttpServer
import jmhttp.utils.{NetDiscovery, Utils}
import com.sftool.optParser.{OptCommand, OptParser, OptResult}
import jmhttp.main.Main.server

object Main{

  val logger = {
    import java.util.logging.{Logger, ConsoleHandler, Level => LogLevel, FileHandler}
    //val formatter  = new java.util.logging.SimpleFormatter()
    val lg = Logger.getLogger("jmhttp")
    lg.setUseParentHandlers(false)
    val handler = new ConsoleHandler()
    handler.setLevel(LogLevel.INFO)
    lg.setLevel(LogLevel.INFO)
    lg.addHandler(handler)
    lg
  }

  val server = new HttpServer(
    logger,
    tsl = false
    // login = serverLogin
  )

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
          =>  {
            val p = Utils.expandPath(v)
            println("path = " + p)
            ("/" + k, p)
          }
      case _
          => throw new IllegalArgumentException("Error: invalid shared directory, expected <url>:<path> string")
    }


  def makeServerShareCommand(
    server:   HttpServer,
    name:     String,
    usage:    String,
    desc:     String,
    example:  String = "",
    helpFlag: Boolean = true
  )(handler: OptResult => Unit) = {
    val cmd = new OptCommand(
      name  = name,
      usage = usage,
      helpFlag = true,
      desc  = desc,
      example = example
    ).addOpt(
      name        = "port",
      shortName   = "p",
      argName     = "<PORT>",
      desc        = "Port that server will listen to. Default 8080"
    ).addOpt(
      name        = "host",
      argName     = "<HOST>",
      desc        = "Host that server will listen to. Default 0.0.0.0 - All addresses."
    ).addOpt(
      name        = "auth",
      desc        = "Authentication - default empty.",
      argName     = "<USER>:<PASSWORD>"
    ).addOpt(
      name        = "tls",
      argName     = "<KEY STORE>:<PASSWORD>",
      desc        = "Enable TLS (Transport Layer Security)/SSL. It encrypts connection."
    ).addOpt(
      name        = "image",
      shortName   = "im",
      desc        = "Show images thumbnails in directory listing."
    ).addOpt(
      name        = "no-index",
      shortName   = "ni",
      desc        = "Don't render index.html if available in directory listing."
    ).addOpt(
      name        = "publish",
      desc        = "Publish server with multicast DNS (aka mdns, Zeroconf protocol.)",
    ).setAction{ res =>
      val port = res.getInt("port", 8080)
      val host = res.getStr("host", "0.0.0.0")
      val tlsFlag = false
      val mdns = res.getFlag("publish")
      val auth = Some(res.getStr("auth", "")) flatMap { s =>
        s.split(":") match {
          case Array(user, pass)
              => Some(user, pass)
          case _
              => None
        }
      }
      server.setLogin(auth)
      handler(res)

      val tls = res.getStr("tsl", "").split(":") match {
        case Array(keystore, passwd)
            => {
              println("Set TSL OK.")
              System.setProperty("javax.net.ssl.keyStore", Utils.expandPath(keystore))
              System.setProperty("javax.net.ssl.keyStorePassword", passwd)
              server.enableTSL()
              true
            }
        case _
            => {
              println("TSL not set.")
              false
            }
      }

      val serverURL =
        Utils.getLocalAddress()
          .map{ addr =>
          if(!tls)
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

      // Blocks main thread - listening and handling client sockets
      if(mdns) Utils.withThread{
        val intf = NetDiscovery.getActiveInterface()
        intf foreach NetDiscovery.registerService(
          serviceType = if(tls)
            "_.https._tcp.local"
          else
            "_http._tcp.local",
          serviceName =  "jmhttp server",
          servicePort =  port,
          serviceDesc = "Micro Http server for file sharing."
        )
      }
      server.run(port = port, host = host)
    }
    cmd
  }

  val commandDir =
    makeServerShareCommand(
      server,
      name = "dir",
      usage = "<DIRECTORY",
      desc = "Share single directory.",
      example =
        """
    Share single directory /home/user/Documents at default port 8080
    listening all hosts. Note: (~) tilde is replaced by /home/<user> on Linux,
    /User/<user> on OSX and C:\\User\\<user> on Windows.

    > $ jmhttp dir C:\\Users\\user\\Documents  (Windows)
    > $ jmhttp dir /Users/dummy/documents
      or
    > $ jmhttp ~/Documents

    Share single directory with authentication:

    It will serve the user home directory (~) tilde or /home/<username> on Linux,
    /Users/<username> on MacOSX and C:\\Users\\<username> on Windows with
    authentication requesting username john and password pxjmnf.
    The server will run on the port 8000.

    Note: Even with authentication is still not safe against network sniffers
    such as WireShark. To make the server secure, in addition to authentication,
    it is necessary to use TSL (Transport Secure Layer) option.
    The server can be opened in the web browser at the URL http://localhost:8080
    or http://127.0.0.1:8080 or http://computerIP:8080. When the server is using
    SSL/TLS the server URL is https://<address>:<port>

    > $ jmhttp dir -p=8000 -publish -image -auth=john:pxjmnf tls=cert.jks:pass ~

    Explanation:
        + -p=8000             - Set server port to 8000
        + -publish            - Publish server address on Local network using multicast 
                                DNS or (Zeroconf / Bounjour)
        + -auth=john:pxjmnf   - Basic authentication (user john, password pxmnf)
        + -image              - Show images in the directory listening
        + -tls=cert.jks:pass  - Use the TSL certificate cert.jks to encrypt the connection.
                                <pass> is the certificate's password.

    Note: All command line parameters with (-) dash are optional.


    A TSL/SLL certificate can be generated on Linux or OSX using:

    $ keytool -genkeypair -keyalg RSA -alias sec_server \
       -keystore cert.jks \
       -storepass chargeit -validity 1000000 -keysize 2048

        """
    ){ res =>
      val path  = res.getOperandOrError(index = 0, errorMsg = "Error: missing directory parameter.")
      server.addRouteDirNav(
               Utils.expandPath(path),
               "/",
                showIndex = ! res.getFlag("no-index"),
                showImage = res.getFlag("image")
               )
    }

  val commandMDir =
    makeServerShareCommand(
      server,
      name    = "mdir",
      usage   = "<URL1:DIRECTORY1> [<URL2:DIRECTORY2> ...]",
      desc    = "Share multiple directories.",
      example =
        """
    Share multiple directories using port 8090 and announcing server
    through mDNS multicast DNS Discovery service, aka Apple's Bounjour(®)
    or Zeroconf. It will make the directory Documents available at
    http:<addr>:8090/docs and ~/Pictures at http:<addr>:8090/pics.

    > $ jmthtp -p=8090 -publish docs:~/Documents pics:~/Pictures

    Share multiple directories with tsl/ssl (Transport Layer Security/
    Secure Socket Layer) encryption. It changes the server's URL to
    https://<serveraddr>:8080. It is no longer http://...

    > $ jmhttp -p=8080 --tls=cert.jks:password -m docs:~/Documents pics:~/Pictures

   To generate the certificate use:

   $ keytool -genkeypair -keyalg RSA -alias sec_server \
       -keystore cert.jks \
       -storepass chargeit -validity 1000000 -keysize 2048
        """
    ){ res =>
      try server.addRouteDirsIndex(
        res.getOperands() map parseOperand ,
        showIndex = ! res.getFlag("no-index"),
        showImage = res.getFlag("image")
      ) catch {
        case ex: java.lang.IllegalArgumentException
            => {
              println(ex.getMessage())
              System.exit(0)
            }
      }
    }

  val commandEcho = new OptCommand(
    name  = "echo",
    desc  = "Run echo server for debugging purposes"
  ).addOpt(
    name = "port",
    shortName = "p",
    desc = "Port that server will listen to. Default 8080"
  ).addOpt(
    name = "host",
    desc = "Host that server will listen to. Default 0.0.0.0 - All addresses."
  ).addOpt(
    name = "auth",
    desc = "Authentication - default empty."
  ).setAction{ res =>
    println("Running command echo")
    val port = res.getInt("port", 8080)
    val host = res.getStr("host", "0.0.0.0")
    server.addRouteEcho("/")
    server.run(port = port, host = host)
  }  

  val optHandler = new OptParser(
    program = "jmhttp",
    version = "1.4",
    brief   = "{program} {version} - Micro http server for file sharing at local network."
  ).add(commandDir)
   .add(commandMDir)
   .add(commandEcho)

  def main(args: Array[String]) = {
    // Set the logging format 
    System.setProperty(
      "java.util.logging.SimpleFormatter.format",
      "[%1$tF %1$tT] [%4$s] - %2$s\n - %5$s %6$s%n\n"
      // "[%1$tF %1$tT] [%4$-7s] %5$s %n"
    )
    optHandler.parse(args.toList)
  }

} // ------ EoF Main object ------

