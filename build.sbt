import Tests._

lazy val commonSettings = Seq(
  name := "matrixsum",
  version := "0.1",
  organization := "SingularityKChen",
  scalaVersion := "2.12.1",
  crossScalaVersions := Seq("2.11.12", "2.12.4"),
  libraryDependencies ++= (Seq("chisel3","chisel-iotesters").map {
    dep: String => "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep)) }),
  libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "0.2-SNAPSHOT",
  scalacOptions ++= Seq("-deprecation","-unchecked","-Xsource:2.11"),// Provide a managed dependency on X if -DXVersion="" is supplied on the command line.
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases"),
    Resolver.mavenLocal
  )
)

def javacOptionsVersion(scalaVersion: String): Seq[String] = {
  Seq() ++ {
    // Scala 2.12 requires Java 8. We continue to generate
    //  Java 7 compatible code for Scala 2.11
    //  for compatibility with old clients.
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, scalaMajor: Long)) if scalaMajor < 12 =>
        Seq("-source", "1.7", "-target", "1.7")
      case _ =>
        Seq("-source", "1.8", "-target", "1.8")
    }
  }
}

def conditionalDependsOn(prj: Project): Project = {
  if (sys.props.contains("ROCKET_USE_MAVEN")) {
    prj.settings(Seq(
      libraryDependencies += "edu.berkeley.cs" %% "testchipip" % "1.0-020719-SNAPSHOT",
    ))
  } else {
    prj.dependsOn(testchipip)
  }
}

/**
  * It has been a struggle for us to override settings in subprojects.
  * An example would be adding a dependency to rocketchip on midas's targetutils library,
  * or replacing dsptools's maven dependency on chisel with the local chisel project.
  *
  * This function works around this by specifying the project's root at src/ and overriding
  * scalaSource and resourceDirectory.
  */
def freshProject(name: String, dir: File): Project = {
  Project(id = name, base = dir / "src")
    .settings(
      scalaSource in Compile := baseDirectory.value / "main" / "scala",
      resourceDirectory in Compile := baseDirectory.value / "main" / "resources"
    )
}

// Fork each scala test for now, to work around persistent mutable state
// in Rocket-Chip based generators
def isolateAllTests(tests: Seq[TestDefinition]) = tests map { test =>
      val options = ForkOptions()
      new Group(test.name, Seq(test), SubProcess(options))
  } toSeq


val defaultVersions = Map(
  "chisel3" -> "3.1.+",
  "chisel-iotesters" -> "1.2.+"
  )
val chipyardDir = file("/home/singularity/chipyard")

val rocketChipDir = file("/home/singularity/chipyard/generators/rocket-chip")

lazy val firesimDir = file("/home/singularity/chipyard/sims/firesim/sim")

lazy val firechip = (project in file("/home/singularity/chipyard/generators/firechip"))
  .dependsOn(boom, icenet, testchipip, sifive_blocks, sifive_cache, sha3, utilities, tracegen, midasTargetUtils, midas, firesimLib % "test->test;compile->compile")
  .settings(
    commonSettings,
    testGrouping in Test := isolateAllTests( (definedTests in Test).value )
  )
lazy val midas      = ProjectRef(firesimDir, "midas")
lazy val firesimLib = ProjectRef(firesimDir, "firesimLib")
lazy val tracegen = conditionalDependsOn(project in file("/home/singularity/chipyard/generators/tracegen"))
  .dependsOn(rocketchip, sifive_cache)
  .settings(commonSettings)

lazy val rocketchip = freshProject("rocketchip", rocketChipDir)
  .settings(commonSettings)
  .dependsOn(chisel, hardfloat, rocketMacros)

lazy val chisel  = (project in file("/home/singularity/chipyard/tools/chisel3"))

lazy val firrtl_interpreter = (project in file("/home/singularity/chipyard/tools/firrtl-interpreter"))
  .settings(commonSettings)

lazy val treadle = (project in file("tools/treadle"))
  .settings(commonSettings)

lazy val midasTargetUtils = ProjectRef(firesimDir, "targetutils")

 // Rocket-chip dependencies (subsumes making RC a RootProject)
lazy val hardfloat  = (project in rocketChipDir / "hardfloat")
  .settings(commonSettings).dependsOn(midasTargetUtils)

lazy val rocketMacros  = (project in rocketChipDir / "macros")
  .settings(commonSettings)

lazy val testchipip = (project in file("/home/singularity/chipyard/generators/testchipip"))
  .dependsOn(rocketchip)
  .settings(commonSettings)

lazy val chisel_testers = (project in file("/home/singularity/chipyard/tools/chisel-testers"))
  .dependsOn(chisel, firrtl_interpreter, treadle)
  .settings(
      commonSettings,
      libraryDependencies ++= Seq(
        "junit" % "junit" % "4.12",
        "org.scalatest" %% "scalatest" % "3.0.5",
        "org.scalacheck" %% "scalacheck" % "1.14.0",
        "com.github.scopt" %% "scopt" % "3.7.0"
      )
    )

lazy val utilities = conditionalDependsOn(project in file("/home/singularity/chipyard/generators/utilities"))
  .dependsOn(rocketchip, boom)
  .settings(commonSettings)

lazy val sha3 = (project in file("/home/singularity/chipyard/generators/sha3"))
  .dependsOn(rocketchip, chisel_testers, midasTargetUtils)
  .settings(commonSettings)

lazy val icenet = (project in file("/home/singularity/chipyard/generators/icenet"))
  .dependsOn(rocketchip, testchipip)
  .settings(commonSettings)

lazy val hwacha = (project in file("/home/singularity/chipyard/generators/hwacha"))
  .dependsOn(rocketchip)
  .settings(commonSettings)

lazy val example = conditionalDependsOn(project in file("/home/singularity/chipyard/generators/example"))
  .dependsOn(boom, hwacha, sifive_blocks, sifive_cache, utilities, sha3, matrixsum)
  .settings(commonSettings)

lazy val boom = (project in file("/home/singularity/chipyard/generators/boom"))
  .dependsOn(rocketchip)
  .settings(commonSettings)

lazy val tapeout = conditionalDependsOn(project in file("/home/singularity/chipyard/tools/barstools/tapeout/"))
  .dependsOn(chisel_testers, example)
  .settings(commonSettings)

lazy val mdf = (project in file("/home/singularity/chipyard/tools/barstools/mdf/scalalib/"))
  .settings(commonSettings)

lazy val sifive_blocks = (project in file("/home/singularity/chipyard/generators/sifive-blocks"))
  .dependsOn(rocketchip)
  .settings(commonSettings)

lazy val sifive_cache = (project in file("/home/singularity/chipyard/generators/sifive-cache")).settings(
    commonSettings,
    scalaSource in Compile := baseDirectory.value / "craft"
  ).dependsOn(rocketchip)

lazy val matrixsum = conditionalDependsOn(project in file("."))
//lazy val matrixsum = conditionalDependsOn(project in file("/home/singularity/chipyard/generators/matrixsum"))
  .dependsOn(rocketchip, chisel_testers, sifive_blocks, sifive_cache, utilities, midasTargetUtils)
  .settings(commonSettings)
