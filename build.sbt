name := "akka-stream-workshop"

version := "1.0.0"

scalaVersion := "2.12.2"

libraryDependencies ++= Seq(
  "ch.qos.logback"              % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging"   % "3.7.1",

  "com.typesafe.akka"            %% "akka-stream" % "2.5.3",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.8.9",

  "org.scalatest" %% "scalatest" % "3.0.3" % Test
)
