// Copyright 2015 - 2016 Sam Halliday
// License: http://www.apache.org/licenses/LICENSE-2.0
package fommil

import java.util.concurrent.atomic.AtomicLong

import scala.util.Properties

import com.typesafe.sbt.SbtScalariform._
import scalariform.formatter.preferences._
import sbt._
import sbt.IO._
import sbt.Keys._

/**
 * A bunch of sensible sbt defaults used by https://github.com/fommil
 */
object SensiblePlugin extends AutoPlugin {
  override def requires = sbtdynver.DynVerPlugin
  override def trigger = allRequirements

  val autoImport = SensibleSettings
  import autoImport._

  private val JavaSpecificFlags = sys.props("java.version").substring(0, 3) match {
    case "1.6" | "1.7" => List("-XX:MaxPermSize=256m")
    case _             => List("-XX:MaxMetaspaceSize=256m")
  }

  override val buildSettings = Seq(
    maxErrors := 1,
    fork := true,
    cancelable := true,
    sourcesInBase := false,

    javaOptions += s"-Dsbt.sensible.root=${(baseDirectory in ThisBuild).value.getCanonicalFile}",

    // WORKAROUND https://github.com/dwijnand/sbt-dynver/issues/23
    version := {
      val v = version.value
      if (!v.contains("+")) v
      else v + "-SNAPSHOT"
    },

    concurrentRestrictions := {
      val limited = Properties.envOrElse("SBT_TASK_LIMIT", "4").toInt
      Seq(Tags.limitAll(limited))
    }
  )

  override val projectSettings = scalariformSettings ++ Seq(
    ivyLoggingLevel := UpdateLogging.Quiet,
    conflictManager := ConflictManager.strict,

    // makes it really easy to use a RAM disk
    target := {
      sys.env.get("SBT_VOLATILE_TARGET") match {
        case None       => target.value
        case Some(base) => file(base) / target.value.getCanonicalPath.replace(':', '_')
      }
    },
    javaOptions ++= {
      sys.env.get("SBT_VOLATILE_TARGET") match {
        case None => Nil
        case Some(base) =>
          val tmpdir = s"$base/java.io.tmpdir"
          file(tmpdir).mkdirs()
          s"-Djava.io.tmpdir=$tmpdir" :: Nil
      }
    },

    javaOptions += s"-Dsbt.sensible.name=${name.value}",
    javaOptions in Compile += s"-Dlogback.configurationFile=${(baseDirectory in ThisBuild).value}/logback-main.xml",

    ScalariformKeys.preferences := FormattingPreferences().setPreference(AlignSingleLineCaseStatements, true),

    resources in Compile ++= {
      // automatically adds legal information to jars, but are
      // lower-cased https://github.com/fommil/sbt-sensible/issues/5
      val orig = (resources in Compile).value
      val base = baseDirectory.value
      val root = (baseDirectory in ThisBuild).value

      def fileWithFallback(name: String): File = {
        if ((base / name).exists) base / name
        else if ((root / name).exists) root / name
        else throw new IllegalArgumentException(s"legal file $name must exist")
      }

      Seq(fileWithFallback("LICENSE"), fileWithFallback("NOTICE"))
    },

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
          case _             => Nil
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

    // must be project-level because of crazy ivy...
    libraryDependencies ++= sensibleTestLibs(Test),

    dependencyOverrides ++= Set(
      // scala-lang is always used during transitive ivy resolution (and potentially thrown out...)
      "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      "org.scala-lang" % "scala-library" % scalaVersion.value,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.scala-lang" % "scalap" % scalaVersion.value,
      // user may have a different scala provider...
      scalaOrganization.value % "scala-compiler" % scalaVersion.value,
      scalaOrganization.value % "scala-library" % scalaVersion.value,
      scalaOrganization.value % "scala-reflect" % scalaVersion.value,
      scalaOrganization.value % "scalap" % scalaVersion.value
    ) ++ logback
  ) ++ inConfig(Test)(sensibleTestSettings) ++ inConfig(Compile)(sensibleCrossPath)

}

object SensibleSettings {
  def shapeless = Def.setting {
    val plugins = CrossVersion.partialVersion(scalaVersion.value).collect {
      case (2, 10) => compilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.patch)
    }.toList

    "com.chuusai" %% "shapeless" % "2.3.2" :: plugins
  }

  def sensibleTestLibs(config: Configuration) = Seq(
    // janino 3.0.6 is not compatible and causes http://www.slf4j.org/codes.html#replay
    "org.codehaus.janino" % "janino" % "2.7.8" % config,
    "org.scalatest" %% "scalatest" % "3.0.1" % config
  ) ++ logback.map(_ % config)

  // WORKAROUND https://github.com/sbt/sbt/issues/2534
  // don't forget to also call testLibs
  def sensibleTestSettings = sensibleCrossPath ++ Seq(
    parallelExecution := true,

    javaOptions ~= (_.filterNot(_.startsWith("-Dlogback.configurationFile"))),
    javaOptions += s"-Dlogback.configurationFile=${(baseDirectory in ThisBuild).value}/logback-${configuration.value}.xml",

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

    // and don't forget `export SCALACTIC_FILE_PATHNAMES=true`
    testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oF", "-W", "120", "60"),
    testFrameworks := Seq(TestFrameworks.ScalaTest, TestFrameworks.JUnit)
  )

  private val logbackVersion = "1.7.24"
  val logback = Seq(
    "ch.qos.logback" % "logback-classic" % "1.2.1",
    "org.slf4j" % "slf4j-api" % logbackVersion,
    "org.slf4j" % "jul-to-slf4j" % logbackVersion,
    "org.slf4j" % "jcl-over-slf4j" % logbackVersion
  )

  // used for unique gclog naming
  private val forkCount = new AtomicLong()

  // WORKAROUND https://github.com/sbt/sbt/issues/2819
  private[fommil] def sensibleCrossPath = Seq(
    unmanagedSourceDirectories += {
      val dir = scalaSource.value
      val Some((major, minor)) = CrossVersion.partialVersion(scalaVersion.value)
      file(s"${dir.getPath}-$major.$minor")
    }
  )

}
