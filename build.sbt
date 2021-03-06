enablePlugins(DockerPlugin)

// common settings
name := "wikipedia-spark"
version := "0.0.1"
scalaVersion := "2.11.8"
organization := "asgard"

// sbt spark plugin practice:
// the name of your Spark Package
spName := "asgard/wikipedia-json"
// the Spark Version your package depends on.
sparkVersion := "2.1.1"
// sparkComponents += "mllib" // creates a dependency on spark-mllib.
sparkComponents += "sql"

libraryDependencies ++= Seq(
  "com.databricks" % "spark-xml_2.11" % "0.4.1",
  "com.github.scopt" %% "scopt" % "3.4.0",
  "org.xerial.snappy" % "snappy-java" % "1.1.4"
  )

unmanagedBase := baseDirectory.value / "lib"

assemblyMergeStrategy in assembly := {
  case PathList("org", "w3c", xs @ _*)         => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}


dockerfile in docker := {
  // The assembly task generates a fat JAR file
  val artifact: File = assembly.value
  val artifactTargetPath = s"/home/wikipedia-spark/${artifact.name}"
  val log4jConfig: File = new File("log4j.properties")
  new Dockerfile {
    from("asgard/pyspark3-alpine")
    add(artifact, artifactTargetPath)
    env("JAR", artifactTargetPath)
    add(log4jConfig, "/usr/spark/conf/log4j.properties")
  }
}

imageNames in docker := Seq(
  // Sets the latest tag
  ImageName(s"${organization.value}/${name.value}:latest"),

  // Sets a name with a tag that contains the project version
  ImageName(
    namespace = Some(organization.value),
    repository = name.value,
    tag = Some("v" + version.value)
  )
)