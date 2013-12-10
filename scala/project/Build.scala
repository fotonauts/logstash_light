import sbt._
import sbt.Keys._

import sbtassembly.Plugin._
import AssemblyKeys._

import spray.revolver.RevolverPlugin._

object MyBuild extends Build
{
    val resolverSettings = Seq(
            resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
            resolvers += "sonatype rels" at "https://oss.sonatype.org/content/groups/scala-tools/",
            resolvers += "sonatype snaps" at "https://oss.sonatype.org/content/repositories/snapshots/",
            resolvers += "java m2" at "http://download.java.net/maven/2",
            resolvers += "repo.novus rels" at "http://repo.novus.com/releases/",
            resolvers += "repo.novus snaps" at "http://repo.novus.com/snapshots/",
            resolvers += "repo.elasticsearch rels" at "http://oss.sonatype.org/content/repositories/releases/",
            resolvers += "repo.elasticsearch snaps" at "http://oss.sonatype.org/content/repositories/snapshots/",
            resolvers += "gphat" at "https://raw.github.com/gphat/mvn-repo/master/releases/"
      )

    val commonsDeps = Seq(
      "redis.clients"                   % "jedis"                      % "1.5.2",
      "joda-time"                       % "joda-time"                  % "2.3",
      "org.joda"                        % "joda-convert"               % "1.2",
      "com.fasterxml.jackson.core"      % "jackson-core"               % "2.3.0",
      "com.fasterxml.jackson.core"      % "jackson-databind"           % "2.3.0",
      "com.fasterxml.jackson.module"    % "jackson-module-scala_2.10"  % "2.3.0",
      "wabisabi"                        %% "wabisabi"                  % "2.0.8",
      "org.elasticsearch"               % "elasticsearch"              % "0.90.5"
    )

    lazy val commonsSettings = Defaults.defaultSettings ++ assemblySettings ++ Revolver.settings ++ resolverSettings ++
        net.virtualvoid.sbt.graph.Plugin.graphSettings ++
        Seq(
                test in assembly := {},
                // javaOptions += "-agentpath:/usr/local/lib/libyjpagent.so=port=29206",
                scalacOptions ++= Seq("-Xfatal-warnings",  "-deprecation", "-feature"),
                fork in Test := true,
                testOptions += Tests.Argument("-oF"),
                initialCommands in console := """
                    import com.fotopedia.femtor._
                    import com.mongodb.casbah.Imports._
                    import org.bson.types.ObjectId
                """,
                mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) => {
                    case "about.html"     => MergeStrategy.discard
                    case PathList("org", "apache", "lucene", _*) => MergeStrategy.first
                    case x => old(x)
                  }
                },
                scalaVersion := "2.10.0"
           )

    lazy val LogstashLight = Project("LogstashLight", file("."),
        settings = commonsSettings ++
            Seq(libraryDependencies ++= commonsDeps,
                target := file("target-standalone")
            )
    )

}
