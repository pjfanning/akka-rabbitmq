organization := "com.github.pjfanning"
name := "akka-rabbitmq"

scalaVersion := "2.12.8"

crossScalaVersions := Seq("2.12.8", "2.13.0")

licenses := Seq(("Apache License, Version 2.0", url("http://www.apache.org/licenses/LICENSE-2.0")))
homepage := Some(new URL("https://github.com/pjfanning/akka-rabbitmq"))

def akka(scalaVersion: String) = {
  val version = "2.5.+"

  def libs(xs: String*) = xs.map(x => "com.typesafe.akka" %% s"akka-$x" % version)

  libs("actor").map(_ % "provided") ++ libs("testkit").map(_ % "test")
}

libraryDependencies ++= {
  akka(scalaVersion.value) ++
  Seq(
    "com.rabbitmq" % "amqp-client" % "5.7.1",
    "com.typesafe" % "config" % "1.3.3" % "test",
    "org.specs2" %% "specs2-mock" % "4.5.1" % "test"
  )
}

