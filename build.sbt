organization := "com.fommil"

name := "sbt-sensible"
sbtPlugin := true

sonatypeGithub := ("fommil", "sbt-sensible")
licenses := Seq(Apache2)

addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-M15-2")
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.6.0")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "1.1.1")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "1.1")

scriptedSettings
scriptedBufferLog := false
scriptedLaunchOpts := Seq(
  "-Dplugin.version=" + version.value
)
