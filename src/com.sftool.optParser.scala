/** 
     - Author: Caio Rodrigues <caiorss [DOT] rodrigues [AT] gmail [DOT] com>

    This is free and unencumbered software released into the public domain.

    Anyone is free to copy, modify, publish, use, compile, sell, or
    distribute this software, either in source code form or as a compiled
    binary, for any purpose, commercial or non-commercial, and by any
    means.

    In jurisdictions that recognize copyright laws, the author or authors
    of this software dedicate any and all copyright interest in the
    software to the public domain. We make this dedication for the benefit
    of the public at large and to the detriment of our heirs and
    successors. We intend this dedication to be an overt act of
    relinquishment in perpetuity of all present and future rights to this
    software under copyright law.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
    EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
    MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
    IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
    OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
    ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
    OTHER DEALINGS IN THE SOFTWARE.  

 =============================================================== */

/**  This is a single-file library for command line option parsing inspired on the ideas 
  *  of C++ boost header-only boost libraries and also C++ single-file boost libraries.
  */
package com.sftool.optParser

/** Custom exception used to indicate command line option parsing errors.
  *
  * Note: This exception is thrown whenever there is an command line parsing error,
  * for instance:
  *  - Not allowed command line option.
  *
  *  - When it is expected an integer, for instance -n=10, but an
  *    invalid value is passed such as -n=p6sd.
  *
  *  - When more than one command line switch, such
  *    as -o=file1 -o=file2, are passed, but only one is expected.
  *
  */
class CommandLineParsingException(msg: String) extends Exception(msg){}

private object OptFun{
  /** Print Table of rows (list of strings) as table */
  def printTableOfRows(xs: Seq[List[String]], space: Int = 1, left: Int = 2) = {
    val sizes = xs.transpose.map{col => col.map(_.length).max}
    val n = sizes.length
    val leftSpace = " " * left
    for(row <- xs){
      print(leftSpace)
      for(i <- 0 to n - 1){
        val nspaces = sizes(i) - row(i).length + 2
        print(row(i) + " " * nspaces)
      }
      println()
    }
  }
}

/** Command line switch */
class OptSwitch(
  name:      String = "",
  shortName: String = "",
  argName:   String = "",  
  desc:      String = ""
){
  private var _name:      String = name 
  private var _shortName: String = shortName  
  private var _desc:      String = desc

  override def toString() =
    s"name = $name - shortName = $shortName"

  def getName() =
    _name

  def getShortName() =
    _shortName

  def getArgName() =
    argName 

  def setName(name: String) = {
    _name = name
    this 
  }

  def getDesc() =
    _desc

  def setDesc(desc: String) = {
    _desc = desc
    this 
  }
}

/** Result of command line parsing */ 
class OptResult(
  operands:   List[String],
  switches:   Map[String, List[String]],
  properties: Map[String, String]
){
  import java.io.File

  /** Try to get value of switch.
    *
    */
  def getValueOfSwitch[A](name: String, default: A)(parser: String => A): A = {
    val opt: Option[List[String]] = switches.get(name)
    opt match {
      case None
          => default
      case Some(List())
          => default
      case Some(List(value))
          => parser(value)
      case _
          =>
        throw new CommandLineParsingException(
          s"Error: command line switch <$name> expected to have only one value."
        )
    }
  }

  /** Get operands that are arguments without command line switches and 
      are also not Java properties (which starts with -D<prop>=<value>) 
    */
  def getOperands() = operands

  /** Get command line switches -<key>=<value> or -<flag> */
  def getSwitches() = switches

  /** Get java properties -D<name>=value */
  def getProperties() = properties

  def getOperand(index: Int, default: String, errorMsg: String = "") = {
    if(index >= operands.size)
      default
    else
      operands(index)
  }

  /** Try to get operand at specific position given by index or throw exception. */
  def getOperandOrError(index: Int, errorMsg: String = "") = {
    if(index >= operands.size || index < 0)
      throw new CommandLineParsingException (
        if (errorMsg == "") s"Error: expected operand <$index>" else errorMsg
      )
    operands(index)
  }

  /** Get operand as an existing file at a specific position given by the index. */
  def getOperandExistingFile(index: Int, errorMsg: String = "") = {
    val fileName = this.getOperandOrError(index, errorMsg)
    val file     = new File(fileName)
    if(!file.isFile())
      throw new CommandLineParsingException(s"Error: file <$fileName> does not exist.")
    file
  }

  def getListStr(name: String) = 
    switches.get(name) getOrElse List()

  /** Get command line switch as string.
    * Note: if the command line switch is not provided, returns a default value.
    */  
  def getStr(name: String, default: String): String = 
    this.getValueOfSwitch(name, default){ x => x}
  
  /** Get command line switch as integer.
    * Note: if the command line switch is not provided, returns a default value.
    */
  def getInt(name: String, default: Int): Int =
    this.getValueOfSwitch(name, default){ x =>
      try x.toInt
      catch {
        case ex: java.lang.NumberFormatException
            => throw new CommandLineParsingException(s"Error: switch <$name> expected number.")
      }
    }

  /** Get command line switch as Boolean.
    * Note: if the command line switch is not provided, returns a default value.
    */  
  def getFlag(name: String, default: Boolean = false) =
    this.getValueOfSwitch(name, default){ x =>
      if (x != "")
        throw new CommandLineParsingException(s"Error: switch $name is a flag.")
      true
    }


  /** Get command line switch value as file (File). */
  def getFile(name: String): Option[File] =
    this.getValueOfSwitch(name, None: Option[File]){ value =>
      Some(new File(value))
    }

  /** Try to get a file and throw an command line exception if doesn't exist. */
  def getExistingFile(name: String): Option[File] =
    this.getValueOfSwitch(name, None: Option[File]){ value =>
      val file = new File(value)
      if(!file.isFile())
        throw new CommandLineParsingException(s"Error: file <$file> does not exist.")
      Some(file)
    }

  /** Try to get a existing directory. Throws a command line exception if doesn't exist. */
  def getExistingDirectory(name: String): Option[File] =
    this.getValueOfSwitch(name, None: Option[File]){ value =>
      val file = new File(value)
      if(!file.isDirectory())
        throw new CommandLineParsingException(s"Error: directory <$file> does not exist.")
      Some(file)
    }
  

  override def toString() = {
    val sw = new java.io.StringWriter()
    val pw = new java.io.PrintWriter(sw)
    pw.println("Operands =  " + this.getOperands())
    pw.println("Arguments = " + this.getSwitches())
    sw.toString
  }

} /** End of class OptResult */


trait IOptCommand{
  def getCommandName():   String
  def getCommandDesc():   String
  def getCommandUsage():  String

  /** Show command help */
  def showHelp(program: String): Unit

  /** Parse command line and execute action */
  def parseRun(prorgram: String, argList: List[String]): Unit

} /* End of trait IOptCommand */

/** Pseudo command for grouping subcommands. */
class OptSeparator(name: String) extends IOptCommand{
  def getCommandName()  = s"\n[$name]\n"
  def getCommandDesc()  = ""
  def getCommandUsage() = ""
  def showHelp(program: String) = ()
  def parseRun(program: String, argList: List[String]) = ()
}

/** Sub command action that executes without any switch. */
class OptSimple(name: String, desc: String)(action: => Unit){
  def getCommandName()  = name
  def getCommandDesc()  = desc 
  def getCommandUsage() = ""
  def showHelp() = println(desc)
  def parseRun(argList: List[String]) =
    action 
}


/** Main command or subcommand like log from $ git log. 
    @param name     Subcommand name such as log from - $ git log
    @param usage    Short usage string similar to usage: [OPTIONS] [ARGS]
    @param desc     Brief one-line description about the subcommand / command. 
    @param longDesc Long description about the subcommand / command.
    @param example  Examples about the command.
    @param helpFlag If true, shows the command help when invoked without arguments. 
  */
class OptCommand (
  name:     String = "",  
  usage:    String = "",  
  desc:     String = "",  
  longDesc: String = "",
  example:  String = "",
  helpFlag: Boolean = false
  ) extends IOptCommand {

  import scala.collection.mutable.ListBuffer
  val switchMark = "-"
  val switchSeparator = "="
  private val options  = ListBuffer[OptSwitch]()
  private var operands = List[String]()
  private var _action = (res: OptResult) => println("results  = " + res)

  private def replaceTemplate(text: String, program: String): String = {
    text
      .replaceAll("\\{program\\}", program)
      .replaceAll("\\{command\\}", name)
      .replaceAll("\\{cmd\\}",     name)
      .replaceAll("\\{program-cmd\\}", s"$program $name")
  }

  def getCommandName()  = name
  def getCommandDesc()  = desc
  def getCommandUsage() = usage

  def addOpt(
    name:      String,
    shortName: String  = "",
    argName:   String  = "",
    desc:      String  = "",
    ) = {
    this.options.append(new OptSwitch(name, shortName, argName, desc))
    this
  }

  /** Set command / function to be called after results are parsed. */
  def setAction(action: OptResult => Unit) = {
    _action = action
    this
  }    

  def getOptions() =
    options.toList

  /** Print help information for the user. */
  def showHelp(program: String) = {
    val name  = this.getCommandName()
    val desc  = this.getCommandDesc()
    val usage = this.getCommandUsage()
    if(desc != "") {
      println(this.replaceTemplate(desc, program))
      println()
    }
    if(longDesc != "") println(this.replaceTemplate(longDesc, program))    
    if(name != "") println(s"USAGE: $$ $program $name $usage")

    if(!options.isEmpty){
      println() ; println("OPTIONS:")
    }
    
    /** Print options (Command line switches) */
    val rows = options.toList map {o =>
      val argName = o.getArgName()      
      List(
        "-" + o.getName + (if (argName != "") ("=" + argName) else "") + ", ",
        "-" + o.getShortName(),
        o.getDesc()
      )
    }
    OptFun.printTableOfRows(rows)
    if(example != "") {
      println()
      println("EXAMPLES:")
      println(this.replaceTemplate(example, program))
    }

  } // --- EoF func showHelp() ---- //

  /** Parse command line arguments */
  def parse(argList: List[String]): OptResult = {
    /* Java properties, aka switches like -Dserver.storage=/path */
    val properties =
      argList
        .filter(_.startsWith("-D"))
        .map{s => s.stripPrefix("-D").split("=", 2) match {
          case Array(key)         => (key, "")
          case Array(key, "")     => (key, "")            
          case Array(key, value)  => (key, value)
          case p => throw new RuntimeException("Error: Invalid property: " + p)
        }
      }.toMap


    /* Get command line switches of type -<switch>=<value> or -<switch>
       for instance,  -o=output.exe, -i=input.jar -flag
     */
    val switchesLST = argList
      .takeWhile(_ != "--")
      .filter(s => s.startsWith(switchMark) && !s.startsWith("-D"))
      .map { p => p.split(switchSeparator, 2) match {
        case Array(k, v) => (k.stripPrefix(switchMark), v)
        case Array(k)    => (k.stripPrefix(switchMark), "")
        case _           => null
      }
    }
    // println("switchesLST = " + switchesLST )
    var switches: Map[String, List[String]] = switchesLST
      .groupBy{case (k, v) => k}
      .map{case (k, xs) =>
        val name = options
          .find(n => n.getName() == k || n.getShortName() == k)
          .map(_.getName)
          .getOrElse(k)
        (name, xs map (_._2))
    }

    // Arguments after --
    val restArgs = argList.dropWhile(_ != "--")

    restArgs match {
      case List()      => () // ignore
      case List("--")  => () // Ignore
      case "--"::rest  => switches += "--" -> rest
      case _           => ()
    }

    val operands: List[String] =
      argList
        .takeWhile(_ != "--")
        .filter(!_.startsWith(switchMark))

    // Validate result
    val switchKeys = switches.map(_._1).toSet
    //println("switchKeys = " + switchKeys)
    val keys = (options.map(_.getName) ++ options.map(_.getShortName)).toSet ++ Set("--")
    val diff = switchKeys.diff(keys)

    if(!(diff.isEmpty || diff == keys))
      throw new CommandLineParsingException(
        "Error: invalid command line switche(s): " + diff.map(s => "<" + s + ">" ).mkString(", ")
      )

    new OptResult(operands, switches, properties)

  } // -- EoF fun. parse ---- //


  def parseRun(program: String, argList: List[String]): Unit =
    try argList match {
      case List()
          => if(this.helpFlag)
            this.showHelp(program)
          else
            _action(this.parse(argList))
      case _
          =>
        _action(this.parse(argList))
    } catch {
      case ex: CommandLineParsingException => {
        println(ex.getMessage())
        System.exit(1)
      }
    }


} // ---- End of class OptCommand ---- // 


/** Command line parser with sub-commands or services similar to git and busybox.  
  * @param program - Application name. 
  * @param version - Application version string. 
  * @param brief   - Single-line brief description about what the application does.
  * @param usage   - Single-line usage string.
  * @param license - Application license, example: GPL-v3.0, BSD, Public Domain .... 
  */
class OptParser(
  program:     String,
  version:     String = "",
  brief:       String = "",
  usage:       String = "",
  license:     String = "",
  ){
  import scala.collection.mutable.{Map, ListBuffer}
  private val commands = ListBuffer[String]()
  private val parsers = Map[String, IOptCommand]()

  private def replaceTemplate(text: String): String = {
    text
      .replaceAll("\\{program\\}", program)
      .replaceAll("\\{version\\}", version)
      .replaceAll("\\{license\\}", license)
  }  

  /** Add sub command. */
  def add(opt: IOptCommand) = {
    commands.append(opt.getCommandName())
    parsers += opt.getCommandName() -> opt
    this 
  }  

  /** Show user help. */
  def showHelp() = {
    // Print program description 
    println(this.replaceTemplate(brief))
    // Print usage
    if(usage == "")
      println(s"Usage: $$ $program [COMMAND] [OPTIONS] [<ARGS> ...]")
    else
      println(this.replaceTemplate(usage))
    println()
    //-- Print sub commands --- //
    println("Commands:\n")
    val rows = commands.toList map {name =>
      val c = parsers(name)
      List(c.getCommandName(), c.getCommandDesc())
    }
    OptFun.printTableOfRows(rows)
  }

  /** Parse command line arguments. */
  def parse(args: List[String]) =
    args match {
      case List()
          => {           
            showHelp()
            System.exit(0)
          }

      case List("-h") | List("-help")
          => {           
            this.showHelp()
            System.exit(0)
          }        

      // Show sub-command help 
      case List(command, h) if (h == "-help" || h == "-h" )
          => parsers.get(command) match {
            case Some(cmd) =>
              cmd.showHelp(program)
            case None => {
              println(s"Error: invalid command <$command>")
              System.exit(1)
            }
          }

      case command::rest
        => parsers.get(command) match {
          case Some(cmd)
              => cmd.parseRun(program, rest)
          case None
              => {
                println(s"Error: invalid command: $command")
                System.exit(1)
              }
        }
    } /* -- End of .parse()  -*/

}

