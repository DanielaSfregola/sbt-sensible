organization := "com.fommil"

name := "sbt-sensible"
sbtPlugin := true

sonatypeGithub := ("fommil", "sbt-sensible")
licenses := Seq(Apache2)

addSbtPlugin("io.get-coursier" % "sbt-coursier-java-6" % "1.0.0-M12-1")
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.6.0")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")

scriptedSettings
scriptedBufferLog := false
scriptedLaunchOpts := Seq(
  "-Dplugin.version=" + version.value
)
