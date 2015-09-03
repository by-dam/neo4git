name := "neo4git"

version := "0.1"

//scalaVersion := "2.11.6"
scalaVersion := "2.10.5"

resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven"

libraryDependencies += "com.typesafe.akka" % "akka-stream-experimental_2.10" % "1.0-M5"
libraryDependencies += "com.typesafe.akka" % "akka-http-experimental_2.10" % "1.0-M5"
libraryDependencies += "com.typesafe.akka" % "akka-http-core-experimental_2.10" % "1.0-M5"
//libraryDependencies += "com.typesafe.akka" % "akka-stream-experimental_2.11" % "1.0-M5"
//libraryDependencies += "com.typesafe.akka" % "akka-http-experimental_2.11" % "1.0-M5"
//libraryDependencies += "com.typesafe.akka" % "akka-http-core-experimental_2.11" % "1.0-M5"
libraryDependencies += "org.eclipse.jgit" % "org.eclipse.jgit" % "[3.7,)"
libraryDependencies += "org.neo4j" % "neo4j" % "2.2.1"
libraryDependencies += "org.neo4j" % "neo4j-cypher" % "2.2.1"
    
