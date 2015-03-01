import sbt.Keys._
import sbt._
import scoverage.ScoverageSbtPlugin

object InventoryBuild extends Build {
    
  lazy val commonResolvers = Seq(
    DefaultMavenRepository,
    Resolver.typesafeRepo("releases"),
    "Conjars Repo" at "http://conjars.org/repo"
  )

  lazy val commonSettings: Seq[Setting[_]] = Seq (
    version := "0.1",
    scalaVersion := "2.11.5",
    crossScalaVersions := Seq("2.11.5", "2.10.4"),
    organization := "com.pragmasoft",
    scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8", "-feature"),
    resolvers ++= commonResolvers
  ) ++   net.virtualvoid.sbt.graph.Plugin.graphSettings

  implicit class PumpedProject(val project: Project) extends AnyVal {
    def testReportConfig =
      project
        .disablePlugins(plugins.JUnitXmlReportPlugin)
        .settings(
          (testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/test-reports", "-oD"),
          (testOptions in Test) += Tests.Argument(TestFrameworks.Specs2, "junitxml", "console")
        )
  }

  val akkaVersion = "2.3.2"
  val logbackVersion = "1.1.2"

  val akka = Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
  )


  val akkaTest = Seq(
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
  )

  val logback = Seq(
    "ch.qos.logback" % "logback-classic" % logbackVersion
  )

  val scalaTest = Seq(
    "org.scalatest" %% "scalatest" % "2.2.2" % "test"
  )


  val rootClasspath =
    (akka map { _ % "provided" } )++
    scalaTest ++
    akkaTest ++
    (logback map { _ % "test"})

  val root =
      Project(id = "akka-components", base = file("."))
        .settings( commonSettings: _* )
        .settings( libraryDependencies ++= rootClasspath  )
        .settings(
          ScoverageSbtPlugin.ScoverageKeys.coverageMinimum := 75,
          ScoverageSbtPlugin.ScoverageKeys.coverageFailOnMinimum := true,
          fork := true,
          publishMavenStyle := true
        )
        .testReportConfig
    

}


