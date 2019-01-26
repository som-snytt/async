import ScalaModulePlugin._

scalaModuleSettings

scalaVersionsByJvm in ThisBuild := {
  val v212 = "2.12.6"
  //val v213 = "2.13.0-M5"
  val v213 = "2.13.0-pre-e823c80-SNAPSHOT"

  val allFalse = List(v212 -> false, v213 -> false)
  Map(
    8 -> List(v212 -> true, v213 -> true),
    9 -> allFalse,
    10 -> allFalse,
    11 -> allFalse
  )
}

name := "scala-async"
repoName := "async"

version := "0.9.8-SNAPSHOT"

libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided"
libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value % "test" // for ToolBox
libraryDependencies += "junit" % "junit" % "4.12" % "test"
libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test"

enableOptimizer
testOptions += Tests.Argument(TestFrameworks.JUnit, "-q", "-v", "-s")
scalacOptions in Test ++= Seq("-Yrangepos")

parallelExecution in Global := false

// Uncomment to disable test compilation.
// (sources in Test) ~= ((xs: Seq[File]) => xs.filter(f => Seq("TreeInterrogation", "package").exists(f.name.contains)))

description := "An asynchronous programming facility for Scala that offers a direct API for working with Futures."
homepage := Some(url("http://github.com/scala/async"))
startYear := Some(2012)
pomExtra := (
  <issueManagement>
    <system>GitHub</system>
    <url>https://github.com/scala/async/issues</url>
  </issueManagement>
  <developers>
    <developer>
      <id>phaller</id>
      <name>Philipp Haller</name>
      <timezone>+1</timezone>
      <url>http://github.com/phaller</url>
    </developer>
    <developer>
      <id>retronym</id>
      <name>Jason Zaugg</name>
      <timezone>+10</timezone>
      <url>http://github.com/retronym</url>
    </developer>
  </developers>
  )
OsgiKeys.exportPackage := Seq(s"scala.async.*;version=${version.value}")

commands += testDeterminism

def testDeterminism = Command.command("testDeterminism") { state =>
  val extracted = Project.extract(state)
  println("Running test:clean")
  val (state1, _) = extracted.runTask(clean in Test in LocalRootProject, state)
  println("Running test:compile")
  val (state2, _) = extracted.runTask(compile in Test in LocalRootProject, state1)
  val testClasses = extracted.get(classDirectory in Test)
  val baseline: File = testClasses.getParentFile / (testClasses.getName + "-baseline")
  baseline.mkdirs()
  IO.copyDirectory(testClasses, baseline, overwrite = true)
  IO.delete(testClasses)
  println("Running test:compile")
  val (state3, _) = extracted.runTask(compile in Test in LocalRootProject, state2)

  import java.nio.file.FileVisitResult
  import java.nio.file.{Files, Path}
  import java.nio.file.SimpleFileVisitor
  import java.nio.file.attribute.BasicFileAttributes
  import java.util

  def checkSameFileContents(one: Path, other: Path): Unit = {
    Files.walkFileTree(one, new SimpleFileVisitor[Path]() {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        val result: FileVisitResult = super.visitFile(file, attrs)
        // get the relative file name from path "one"
        val relativize: Path = one.relativize(file)
        // construct the path for the counterpart file in "other"
        val fileInOther: Path = other.resolve(relativize)
        val otherBytes: Array[Byte] = Files.readAllBytes(fileInOther)
        val thisBytes: Array[Byte] = Files.readAllBytes(file)
        if (!(util.Arrays.equals(otherBytes, thisBytes))) {
          throw new AssertionError(file + " is not equal to " + fileInOther)
        }
        return result
      }
    })
  }
  println("Comparing: " + baseline.toPath + " and " + testClasses.toPath)
  checkSameFileContents(baseline.toPath, testClasses.toPath)
  checkSameFileContents(testClasses.toPath, baseline.toPath)

  state3
}
