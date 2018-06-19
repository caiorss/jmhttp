package jmhttp.main

import jmhttp.server.HttpServer 
import jmhttp.utils.{Utils, NetDiscovery}
import com.sftool.optParser.{OptCommand, OptResult, OptParser}

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

  val commandDir = new OptCommand(
    name  = "dir",
    usage = "<directory>",
    helpFlag = true,
    desc  = "Share a single directory"
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
    desc        = "Authentication - default empty."
  ).addOpt(
    name        = "image",
    shortName   = "m",
    desc        = "Show images thumbnails in directory listing."
  ).addOpt(
    name        = "no-index",
    desc        = "Don't render index.html if available in directory listing."
  ).setAction{ res =>
    val port = res.getInt("port", 8080)
    val host = res.getStr("host", "0.0.0.0")    
    val path  = res.getOperandOrError(0, "Error: missing directory parameter.")
    server.addRouteDirNav(
      Utils.expandPath(path),
      "/",
      showIndex = ! res.getFlag("no-index"),
      showImage = res.getFlag("image")
    )
    server.run(port = port, host = host)
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
    server.addRouteDebug("/")
    server.run(port = port, host = host)
  }  

  val optHandler = new OptParser(
    program = "jmhttp",
    version = "1.4",
    brief   = "{program} {version} - Micro http server for file sharing at local network."
  ).add(commandDir)
   .add(commandEcho)

  def main(args: Array[String]) =
    optHandler.parse(args.toList)

} // ------ EoF Main object ------

