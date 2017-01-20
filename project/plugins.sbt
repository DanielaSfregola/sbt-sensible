ivyLoggingLevel := UpdateLogging.Quiet
scalacOptions in Compile ++= Seq("-feature", "-deprecation")
addSbtPlugin("com.fommil" % "sbt-sensible" % "1.1.5")

libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
