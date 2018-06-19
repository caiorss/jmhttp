import sbt.Package.ManifestAttributes

// Scala runtime and distribution version
scalaVersion := "2.12.6"

// import sbt.project

//============ Application Specific Settings ===============
// Application/Program name 
name                 := "jmhttp"
// Application version 
version              := "1.4"
// Project description
description          := "Micro Http server for file and static html sharing at local network."
// Main class points to the main program entry point:
mainClass in Compile := Some("jmhttp.main.Main")
// DO NOT add scala version to the produced artifacts
crossPaths := false

// Set contents of manifest file => META-INF/MANIFEST.INF
// Reference: https://github.com/sbt/sbt-assembly/issues/80
packageOptions in assembly := Seq(ManifestAttributes(
  ("Main-Class",             "jmhttp.main.Main"),
  ("Built-By",               "dummy team"),
  ("Implementation-Title",   "JmHttp Server"),
  ("Implementation-Version", "1.4"))
)

// mergeStrategy in assembly := { case _ => MergeStrategy.first }

//============= Dependencies ================================================
//
//
libraryDependencies ++= Seq(
  // Java -dependency format:
  //------------------------------------
  // <groupID> % <artifactID> % <version>
  //"org.codehaus.groovy" % "groovy-all" % "2.4.15"
  "javax.jmdns" % "jmdns" % "3.4.1"


  // Scala dependency format:
  //------------------------------------
  // <groupID> %% <artifactID> % <version>
)

//============= Overrides project layout =====================================
//   Original Scala Layout      ---  This layout  (Scala-only project)
//   ./src/main/*.files.scala         src/*.scala 
//   ./src/tests                      test/*.tests.scala
//
// Reference: 
//  + https://stackoverflow.com/questions/15476256/how-do-i-specify-a-custom-directory-layout-for-an-sbt-project
//  + https://stackoverflow.com/questions/10131340/changing-scala-sources-directory-in-sbt
//-------------------------------------------------------------------------
// Move classes from src/main/*.scala to ./src/*.scala
scalaSource in Compile := { (baseDirectory in Compile)(_ / "src") }.value
//-------------------------------------------------------------------------
// Move tests from src/tests to ./test 
scalaSource in Test := { (baseDirectory in Test)(_ / "test") }.value
// Move resource directory from ./src/main/resources to ./resources
resourceDirectory in Compile := baseDirectory.value / "resources"


initialize := { System.setProperty("gui.designmode", "true") }


//============= Customs SBT tasks ===================== //
//
//

/** Copy Uber jar to current directory (./)
  * Usage: $ sbt copyUber 
  * 
  * References:
  * + https://stackoverflow.com/questions/47872758/how-can-i-make-a-task-depend-on-another-task
  * + https://www.scala-sbt.org/1.x/docs/Migrating-from-sbt-013x.html#Migrating+from+sbt+0.12+style
  * + http://blog.bstpierre.org/writing-simple-sbt-task
  * + https://www.scala-sbt.org/1.0/docs/Custom-Settings.html
  * 
  *******************************************************************/
val copyUber = TaskKey[Unit]("copyUber", "Run produced uber jar")
copyUber := {
  import java.io.File
  val inpFile = new File(assembly.value.getPath)
  // val outFile = new File(inpFile.getName)
  val outFile = name.value + "-uber.jar"
  val inch = java.nio.channels.Channels.newChannel(
    new java.io.FileInputStream(inpFile))
  val fos = new java.io.FileOutputStream(outFile)
  fos.getChannel().transferFrom(inch, 0, java.lang.Long.MAX_VALUE)
    inch.close()
  println("Created  = " + outFile)
}


//================ Plugins =========================//

// Proguard 
//  -> See:
//  + https://github.com/sbt/sbt-proguard/issues/27
//  + https://www.scala-sbt.org/sbt-native-packager/recipes/custom.html
//  + https://github.com/sbt/sbt-proguard/issues/2
//  + https://stackoverflow.com/questions/29055544/sbt-proguard-issue-with-java-1-8
//  + https://stackoverflow.com/questions/39655207/how-to-obfuscate-fat-scala-jar-with-proguard-and-sbt
//  + https://ask.helplib.com/java/post_1389823
//  + https://dvcs.w3.org/hg/read-write-web/file/aa9074df0635/project/build.scala
//  + 
// 
enablePlugins(SbtProguard)
// ProguardKeys.proguardVersion in Proguard := "5.2.1"
// javaOptions in (Proguard, ProguardKeys.proguard) := Seq("-Xmx2G")
// javaOptions in (Proguard, ProguardKeys.proguard) += Seq("-Xss1G")
exportJars := true
proguardMerge in Proguard := false
proguardOptions in Proguard ++= Seq(
  "-dontnote", "-dontwarn", "-ignorewarnings",
  "-dontoptimize",
  // "-dontobfuscate",

  // Reference: http://stackoverflow.org.cn/front/ask/view?ask_id=127610
  "-optimizationpasses 5",
  "-optimizations !code/allocation/variable",


  "-keepclasseswithmembers public class * { public static void main(java.lang.String[]); }",
  "-keep class scala.collection.SeqLike { public protected *;} ",

  // "-keep interface java8.** { *; }",
  // "-keep class java8.** { *; }",

  // "-keepattributes Signature",
  // "-keepattributes *Annotation*",
  // "-keepattributes InnerClasses",
  // "-keepattributes SourceFile",
  // "-keepattributes LineNumberTable",

  //----------------------------------

  // "-keepattributes Annotation", 
  // "-keepattributes EnclosingMethod,InnerClasses,Signature",
  // "-keep class scala.ScalaObject { *; }",
  // "-keep class scala.Symbol { *; }",

)

proguardMerge in Proguard := true
proguardOptions in Proguard += ProguardOptions.keepMain("jmhttp.main.Main")
