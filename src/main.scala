package jmhttp.main

import jmhttp.server.HttpServer
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
    helpFlag: Boolean = true
  )(handler: OptResult => Unit) = {
    val cmd = new OptCommand(
      name  = name,
      usage = usage,
      helpFlag = true,
      desc  = desc
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
      shortName   = "im",
      desc        = "Show images thumbnails in directory listing."
    ).addOpt(
      name        = "no-index",
      shortName   = "ni",
      desc        = "Don't render index.html if available in directory listing."
    ).setAction{ res =>
      val port = res.getInt("port", 8080)
      val host = res.getStr("host", "0.0.0.0")
      handler(res)
      server.run(port = port, host = host)
    }
    cmd
  }

  val commandDir =
    makeServerShareCommand(
      server,
      name = "dir",
      usage = "<DIRECTORY",
      desc = "Share single directory."){ res =>
      val path  = res.getOperandOrError(index = 0, errorMsg = "Error: missing directory parameter.")
      server.addRouteDirNav(
               Utils.expandPath(path),
               "/",
                showIndex = ! res.getFlag("no-index"),
                showImage = res.getFlag("image")
               )
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

