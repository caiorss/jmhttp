package jmhttp.optParse
// Command line option parsing development
//


trait CmdVal
case class CmdValStr  (var value: String)       extends CmdVal
case class CmdValBool (var value: Boolean)      extends CmdVal
case class CmdValInt  (var value: Int)          extends CmdVal
case class CmdValList (var value: List[String]) extends CmdVal

case class CmdOption(
  name:         String,
  shortName:    String,
  argName:      String,
  description:  String,
  value:        CmdVal 
)


class OptHandlingException(msg: String) extends Exception(msg) {
}


class OptSet(
  name: String         = "appname",
  description: String  = "What does this application do?",
  operandsDesc: String = "[OPERANDS]"
){
  import scala.collection.mutable.ListBuffer
  private val options  = ListBuffer[CmdOption]()
  private var operands = List[String]()

  def add(opt: CmdOption) =
    options.append(opt)

  def addOptionFlag(
    name:         String,
    description:  String,
    shortName:    String = null
  ) = {
    val o = CmdOption(
      name,
      shortName,
      null,
      description,
      CmdValBool(false)
    )
    this.add(o)
  }

  def addOptionStr(
    name:        String,
    description: String = "description",
    argName:     String = "arg",
    value:       String = "",
    shortName:   String = null,
  ) = {
    val o = CmdOption(
      name,
      shortName,
      argName,
      description,
      CmdValStr(value)
    )
    this.add(o)
  }

  def addOptionInt(
    name:        String,
    description: String = "description",
    argName:     String = "arg",
    value:       Int    = 0,
    shortName:   String = null,
  ) = {
    val o = CmdOption(
      name,
      shortName,
      argName,
      description,
      CmdValInt(value)
    )
    this.add(o)
  }


  def addOptionList(
    name:        String,
    description: String = "description",
    argName:     String = "arg",
    value:       List[String] = List(),
    shortName:   String = null,
  ) = {
    val o = CmdOption(
      name,
      shortName,
      argName,
      description,
      CmdValList(value)
    )
    this.add(o)
  }


  def getOptions() =
    options.toList

  def getOpt(name: String) =
    this.options
      .find(o => o.name == name || o.shortName == name)
      .map(_.value)

  def getOptAsStr(name: String) =
    this.getOpt(name).map(_.asInstanceOf[CmdValStr].value).get

  def getOptAsBool(name: String) =
    this.getOpt(name).map(_.asInstanceOf[CmdValBool].value).get

  def getOptAsInt(name: String) =
    this.getOpt(name).map(_.asInstanceOf[CmdValInt].value).get

  def getOptAsList(name: String) =
    this.getOpt(name).map(_.asInstanceOf[CmdValList].value).get

  def getOperands() =
    operands

  def showHelp() = {
    println(s"Usage: ${this.name} [OPTIONS] ... ${this.operandsDesc}")
    println(description)
    val table = options map { opt =>

      //println("option = " + opt.name + " " + opt.argName)

      val nameStr = "--" + opt.name

      val shortNameStr =
        if (opt.shortName != null)
          "-" + opt.shortName + ", "
        else
          ""
      val argNameStr =
        if (opt.argName != null)
          s"<${opt.argName}>"
        else
          ""

      if (opt.argName != null)
        (s"${shortNameStr}${nameStr}=${argNameStr}", opt.description)
      else
        (s"${shortNameStr}${nameStr}", opt.description)
    }
    this.printTupleAsTable(table, margin = 2)
  }

  def parse(args: List[String]) = {

    if (args == List("--help") || args == List("-h"))
    {
      this.showHelp()
      System.exit(0)
    }

    val validOptions = options.map(_.name) ++ options.map(_.shortName)
    this.operands = args filter (!_.startsWith("-"))

    val optargs: List[(String, String)] = args
      .filter( _.startsWith("-"))
      .map( s => s.split("=", 2) match {
        case Array(o)
            => {

              if (o.startsWith("--")
                && this.options.find(x => "--" + x.name == o).isEmpty)
                throw new OptHandlingException(s"Erro: invalid option '${o}'")

              if (!o.startsWith("--") && o.startsWith("-")
                && this.options.find(x => "-" + x.shortName == o).isEmpty)
                throw new OptHandlingException(s"Error: invalid short option '${o}'") 
              val optName = o.stripPrefix("-").stripPrefix("-")
              (optName, "true")
            }
        case Array(o, v)
            =>  {
              if (o.startsWith("--")
                && this.options.find(x => "--" + x.name == o).isEmpty)
                throw new OptHandlingException(s"Erro: invalid option '${o}'")

              if (!o.startsWith("--") && o.startsWith("-")
                && this.options.find(x => "-" + x.shortName == o).isEmpty)
                throw new OptHandlingException(s"Error: invalid short option '${o}'")
              val optName = o.stripPrefix("-").stripPrefix("-")
              (optName, v)
            }
      })

    // println("operands = " + operands)
    // println("optargs  = " + optargs)
    // println("Valid options = " + validOptions)

    // Check for invalid command line option 
    if ( optargs exists { case (o, v) => !validOptions.contains(o)})
      throw new OptHandlingException("Error: invalid command line option")

    for (opt <- options) opt.value match {
      case CmdValStr(_)
          =>
        optargs
          .find{ case (n, v) => n == opt.name || n == opt.shortName }
          .foreach{case (n, v) =>
            opt.value.asInstanceOf[CmdValStr].value = v
        }

      case CmdValBool(_)
          =>
        optargs
          .find{ case (n, v) => n == opt.name || n == opt.shortName }
          .foreach{case (n, v) =>
            opt.value.asInstanceOf[CmdValBool].value = true
        }

      case CmdValInt(_)
          =>
        try {
          optargs
            .find{ case (n, v) => n == opt.name || n == opt.shortName }
            .foreach{case (n, v) =>
              opt.value.asInstanceOf[CmdValInt].value = v.toInt
            }
        } catch {
          case ex: java.lang.NumberFormatException =>             
            throw new OptHandlingException(
              s"Error: option --${opt.name} or -${opt.shortName} expects an integer."
            )
        }

      case CmdValList(_)
          => {
            val xs = optargs
              .filter{ case (n, v) => n == opt.name || n == opt.shortName }
              .map{case (n, v) => v}
            opt.value.asInstanceOf[CmdValList].value = xs
          }
    }
  }

  /**  Pretty print a collection of tuples as a table.
   Parameters:
   @param rows     - Collection of tuples to be printed.
   @param title    - Tuple containing the titles of left and right side.
   @param line     - Flag that if set to true, prints a new line between each row.
   @param margin   - Margin from left side of screen as number of spaces.
   @param sep      - Number of spaces between left side and right side
   @param maxRside - Maximum number of characters to printed on right side.
   */
 private def printTupleAsTable(
   rows:   Seq[(String, String)],
   title:  (String, String) = ("", ""),
   line:   Boolean = false,
   margin: Int = 0,
   sep:    Int = 4,
   maxRside: Int = 100
 ) = {

   def printRow(wmax1: Int, clamp: Boolean = true) = (row: (String, String)) => {
     val (lside, rside) = row
     // print left margin
     for (a <- 0 to margin) print(' ')
     print(lside)
     // Print spaces
     for (a <- 0 to wmax1 - lside.length + sep) print(' ')
     if (rside.length <= maxRside) {
       println(rside)
     } else if (clamp){
       val dots = "..."
       println(rside.take(maxRside - dots.length) + dots)
     } else {
       println(rside.take(maxRside))
     }

     // Print line between rows
     if (line) println()
   }
   val (title1, title2) = title
   // Maximum length of left side column
   val wmax1 = title1.length max rows.map(row => row._1.length).max
   printRow(wmax1)(title)
   rows foreach printRow(wmax1)
 }

} // --- EoF class OptSet ----- //

