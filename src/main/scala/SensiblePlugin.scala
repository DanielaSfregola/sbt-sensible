// Copyright 2015 - 2016 Sam Halliday
// License: http://www.apache.org/licenses/LICENSE-2.0
package fommil

import java.util.concurrent.atomic.AtomicLong

import scala.util.Properties

import com.typesafe.sbt.SbtScalariform._
import sbt._
import sbt.IO._
import sbt.Keys._

/**
 * A bunch of sensible sbt defaults used by https://github.com/fommil
 */
object SensiblePlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  private val JavaSpecificFlags = sys.props("java.version").substring(0, 3) match {
    case "1.6" | "1.7" => List("-XX:MaxPermSize=256m", "-XX:+UseConcMarkSweepGC")
    case _ => List("-XX:MaxMetaspaceSize=256m", "-XX:+UseG1GC", "-XX:+UseStringDeduplication")
  }

  override val buildSettings = Seq(
    maxErrors := 1,
    fork := true,
    cancelable := true,

    concurrentRestrictions := {
      val limited = Properties.envOrElse("SBT_TASK_LIMIT", "4").toInt
      Seq(Tags.limitAll(limited))
    }
  )

  override val projectSettings = Seq(
    ivyLoggingLevel := UpdateLogging.Quiet,
    conflictManager := ConflictManager.strict,

    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-feature",
      "-deprecation",
      "-language:postfixOps",
      "-language:implicitConversions",
      "-Xlint",
      "-Yno-adapted-args",
      "-Ywarn-dead-code",
      "-Xfuture"
    ) ++ {
        CrossVersion.partialVersion(scalaVersion.value) match {
          case Some((2, 12)) => Seq("-Ywarn-unused-import")
          case Some((2, 11)) => Seq("-Yinline-warnings", "-Ywarn-unused-import")
          case Some((2, 10)) => Seq("-Yinline-warnings")
          case _ => Nil
        }
      } ++ {
        // fatal warnings can get in the way during the DEV cycle
        if (sys.env.contains("CI")) Seq("-Xfatal-warnings")
        else Nil
      },

    javacOptions ++= Seq(
      "-Xlint:all", "-Xlint:-options", "-Xlint:-path", "-Xlint:-processing"
    ) ++ {
        if (sys.env.contains("CI")) Seq("-Werror")
        else Nil
      },

    javaOptions ++= JavaSpecificFlags ++ Seq("-Xss2m", "-Dfile.encoding=UTF8"),

    dependencyOverrides ++= Set(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      "org.scala-lang" % "scala-library" % scalaVersion.value,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.scala-lang" % "scalap" % scalaVersion.value
    ) ++ logback
  ) ++ inConfig(Test)(testSettings) ++ scalariformSettings

  // WORKAROUND https://github.com/sbt/sbt/issues/2534
  def testSettings = Seq(
    parallelExecution := true,

    // must be in Compile because of crazy ivy...
    libraryDependencies in Compile ++= testLibs(configuration.value),

    javaOptions += "-Dlogback.configurationFile=${(baseDirectory in ThisBuild).value}/logback-test.xml",

    testForkedParallel := true,
    testGrouping := {
      val opts = ForkOptions(
        bootJars = Nil,
        javaHome = javaHome.value,
        connectInput = connectInput.value,
        outputStrategy = outputStrategy.value,
        runJVMOptions = javaOptions.value,
        workingDirectory = Some(baseDirectory.value),
        envVars = envVars.value
      )
      definedTests.value.map { test =>
        Tests.Group(test.name, Seq(test), Tests.SubProcess(opts))
      }
    },

    javaOptions ++= {
      if (sys.env.get("GC_LOGGING").isEmpty) Nil
      else {
        val base = (baseDirectory in ThisBuild).value
        val config = configuration.value
        val n = name.value
        val count = forkCount.incrementAndGet() // subject to task evaluation
        val out = { base / s"gc-$config-$n.log" }.getCanonicalPath
        Seq(
          // https://github.com/fommil/lions-share
          s"-Xloggc:$out",
          "-XX:+PrintGCDetails",
          "-XX:+PrintGCDateStamps",
          "-XX:+PrintTenuringDistribution",
          "-XX:+PrintHeapAtGC"
        )
      }
    },

    testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oF"),
    testFrameworks := Seq(TestFrameworks.ScalaTest, TestFrameworks.JUnit)
  )
  // used for unique gclog naming
  private val forkCount = new AtomicLong()

  private val logbackVersion = "1.7.21"
  private val logback = Seq(
    "ch.qos.logback" % "logback-classic" % "1.1.7",
    "org.slf4j" % "slf4j-api" % logbackVersion,
    "org.slf4j" % "jul-to-slf4j" % logbackVersion,
    "org.slf4j" % "jcl-over-slf4j" % logbackVersion
  )

  def testLibs(config: Configuration) = Seq(
    // janino 3.0.6 is not compatible and causes http://www.slf4j.org/codes.html#replay
    "org.codehaus.janino" % "janino" % "2.7.8" % config,
    "org.scalatest" %% "scalatest" % "3.0.0" % config
  ) ++ logback.map(_ % config)

}
