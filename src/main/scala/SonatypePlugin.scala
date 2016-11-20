// Copyright 2015 - 2016 Sam Halliday
// License: http://www.apache.org/licenses/LICENSE-2.0
package fommil

import sbt._
import Keys._

import com.typesafe.sbt.pgp.PgpKeys

object SonatypeKeys {

  val sonatypeGithub = settingKey[(String, String)](
    "The (user, repository) that hosts the project on github.com/user/repository"
  )

  /**
   * Not technically keys, but are useful license definitions that can
   * be used. MIT and BSD and niche licenses are intentionally not
   * listed to discourage their use.
   *
   * At least one license must exist in the standard sbt `licenses`
   * setting. The contents of the URL should be present in a LICENSE
   * file on the repo and also in the resources directory of each
   * module (a symbolic link is enough to ensure this). Project owners
   * should also seriously consider including a NOTICE file in the
   * root of the project (and resource directories) containing a list
   * of contributors that must retain attribution rights.
   *
   * Listed in decreasing order of copyleft.
   *
   * For a quick whirlwind tour of licensing, see
   * http://fommil.github.io/scalasphere16/
   */
  val GPL3 = ("GPL 3.0" -> url("http://www.gnu.org/licenses/gpl-3.0.en.html"))
  val LGPL3 = ("LGPL 3.0" -> url("http://www.gnu.org/licenses/lgpl-3.0.en.html"))
  val MPL2 = ("MPL 2.0" -> "https://www.mozilla.org/en-US/MPL/2.0/")
  val Apache2 = ("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

}

/**
 * Zero magic support for publishing to sonatype, assuming the project
 * is published on github.
 *
 * If the version string contains "SNAPSHOT", it goes to snapshots,
 * otherwise to staging. Staged releases require a manual login to
 * oss.sonatype.org followed by Repository => Close => (wait for it)
 * => Release
 *
 * The environment variables `SONATYPE_USERNAME` and
 * `SONATYPE_PASSWORD` will be read for credentials.
 *
 * Snapshot releases can be made using `sbt publish` but staged
 * releases must be made with `sbt publishSigned` (and may require the
 * user to provide their GPG credentials manually).
 */
object SonatypeSupport extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  val autoImport = SonatypeKeys
  import autoImport._

  override lazy val projectSettings = Seq(
    PgpKeys.useGpgAgent := true,
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    homepage := Some(url(s"http://github.com/${sonatypeGithub.value._1}/${sonatypeGithub.value._2}")),
    publishTo := {
      val v = version.value
      val nexus = "https://oss.sonatype.org/"
      if (v.contains("SNAP")) Some("snapshots" at nexus + "content/repositories/snapshots")
      else Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    credentials ++= {
      for {
        username <- sys.env.get("SONATYPE_USERNAME")
        password <- sys.env.get("SONATYPE_PASSWORD")
      } yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)
    }.toSeq,
    pomExtra := (
      <scm>
        <url>git@github.com:{ sonatypeGithub.value._1 }/{ sonatypeGithub.value._2 }.git</url>
        <connection>scm:git:git@github.com:{ sonatypeGithub.value._1 }/{ sonatypeGithub.value._2 }.git</connection>
      </scm>
      <developers>
        <developer>
          <id>{ sonatypeGithub.value._1 }</id>
        </developer>
      </developers>
    )
  )
}
