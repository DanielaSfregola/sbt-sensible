ivyLoggingLevel := UpdateLogging.Quiet
scalacOptions in Compile ++= Seq("-feature", "-deprecation")

libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value

addSbtPlugin("com.dwijnand" % "sbt-dynver" % "1.1.0")

addSbtPlugin("io.get-coursier" % "sbt-coursier-java-6" % "1.0.0-M12-1")
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.6.0")
