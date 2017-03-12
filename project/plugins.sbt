ivyLoggingLevel := UpdateLogging.Quiet
scalacOptions in Compile ++= Seq("-feature", "-deprecation")
addSbtPlugin("com.fommil" % "sbt-sensible" % "1.1.9")

libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
