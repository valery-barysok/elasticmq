import sbt._
import Keys._

object Resolvers {
  val elasticmqResolvers = Seq(
    "Sonatype releases" at "http://oss.sonatype.org/content/repositories/releases/",
    "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
    "SotwareMill Public Releases" at "http://tools.softwaremill.pl/nexus/content/repositories/releases/",
    "JBoss Releases" at "https://repository.jboss.org/nexus/content/groups/public")
}

object BuildSettings {
  import Resolvers._
  import ls.Plugin._

  val buildSettings = Defaults.defaultSettings ++ Seq (
    organization  := "org.elasticmq",
    version       := "0.5-SNAPSHOT",
    scalaVersion  := "2.9.1",
    resolvers     := elasticmqResolvers,
    publishTo     <<= (version) { version: String =>
      val nexus = "http://tools.softwaremill.pl/nexus/content/repositories/"
      if (version.trim.endsWith("SNAPSHOT"))  Some("softwaremill-public-snapshots" at nexus + "snapshots/")
      else                                    Some("softwaremill-public-releases"  at nexus + "releases/")
    },
    credentials   += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    publishMavenStyle := true,
    parallelExecution := false,
    scalacOptions += "-unchecked",
    homepage      := Some(new java.net.URL("http://www.elasticmq.org")),
    licenses      := ("Apache2", new java.net.URL("http://www.apache.org/licenses/LICENSE-2.0.txt")) :: Nil
  ) ++ net.virtualvoid.sbt.graph.Plugin.graphSettings ++ lsSettings ++ Seq (
    (LsKeys.tags in LsKeys.lsync) := Seq("elasticmq", "messaging", "guaranteed messaging", "replication", "aws",
      "amazon", "sqs", "embedded", "message queue", "distributed message queue"),
    (externalResolvers in LsKeys.lsync) := Seq(
      "softwaremill-public-releases" at "http://tools.softwaremill.pl/nexus/content/repositories/releases"),
    (description in LsKeys.lsync) :=
      "Message queue server with a Java, Scala and Amazon SQS compatible interfaces. " +
        "Supports guaranteed messaging via queue and message replication. Can run embedded (great for testing " +
        "applications which use SQS), storing data in-memory or in a database, or as a stand-alone server."
  )
}

object Dependencies {
  val squeryl       = "org.squeryl"               %% "squeryl"              % "0.9.5-RC1"
  val h2            = "com.h2database"            % "h2"                    % "1.3.156"
  val c3p0          = "c3p0"                      % "c3p0"                  % "0.9.1.2"
  val jodaTime      = "joda-time"                 % "joda-time"             % "1.6.2" // when available use https://github.com/jorgeortiz85/scala-time
  val netty         = "org.jboss.netty"           % "netty"                 % "3.2.4.Final"

  val slf4s         = "com.weiglewilczek.slf4s"   %% "slf4s"                % "1.0.7"
  val logback       = "ch.qos.logback"            % "logback-classic"       % "1.0.0"
  val jclOverSlf4j  = "org.slf4j"                 % "jcl-over-slf4j"        % "1.6.1"
  val log4jOverSlf4j = "org.slf4j"                % "log4j-over-slf4j"      % "1.6.1"
  val julToSlf4j    = "org.slf4j"                 % "jul-to-slf4j"          % "1.6.1"

  val scalatest     = "org.scalatest"             %% "scalatest"            % "1.6.1"
  val mockito       = "org.mockito"               % "mockito-core"          % "1.7"
  val awaitility    = "com.jayway.awaitility"     % "awaitility-scala"      % "1.3.3"

  val apacheHttp    = "org.apache.httpcomponents" % "httpclient"            % "4.1.1" exclude ("commons-logging", "commons-logging")

  val amazonJavaSdk = "com.amazonaws"             % "aws-java-sdk"          % "1.2.15" exclude ("commons-logging", "commons-logging")

  val mysqlConnector = "mysql"                    % "mysql-connector-java"  % "5.1.12"

  val jgroups       = "org.jgroups"               % "jgroups"               % "3.1.0.Alpha2" exclude ("log4j", "log4j")

  val jsr305        = "com.google.code.findbugs"  % "jsr305"                % "1.3.9"

  val common = Seq(slf4s, jsr305)
  val httpTesting = Seq(apacheHttp % "test", jclOverSlf4j % "test")
}

object ElasticMQBuild extends Build {
  import Dependencies._
  import BuildSettings._

  lazy val root: Project = Project(
    "root",
    file("."),
    settings = buildSettings
  ) aggregate(commonTest, api, spi, core, storageDatabase, replication, rest)

  lazy val commonTest: Project = Project(
    "common-test",
    file("common-test"),
    settings = buildSettings ++ Seq(libraryDependencies := Seq(scalatest, mockito, awaitility, logback))
  )

  lazy val api: Project = Project(
    "api",
    file("api"),
    settings = buildSettings ++ Seq(libraryDependencies := Seq(jodaTime))
  )

  lazy val spi: Project = Project(
    "spi",
    file("spi"),
    settings = buildSettings ++ Seq(libraryDependencies := common)
  ) dependsOn(api, commonTest % "test")

  lazy val core: Project = Project(
    "core",
    file("core"),
    settings = buildSettings
  ) dependsOn(api, spi, commonTest % "test")

  lazy val storageDatabase: Project = Project(
    "storage-database",
    file("storage-database"),
    settings = buildSettings ++ Seq(libraryDependencies := Seq(squeryl, h2, c3p0, log4jOverSlf4j, mysqlConnector % "test") ++ common)
  ) dependsOn(api, spi, core % "test->test")

  lazy val replication: Project = Project(
    "replication",
    file("replication"),
    settings = buildSettings ++ Seq(libraryDependencies := Seq(jgroups, julToSlf4j) ++ common)
  ) dependsOn(api, spi, core % "test->test", storageDatabase % "test->test")

  lazy val rest: Project = Project(
    "rest",
    file("rest"),
    settings = buildSettings
  ) aggregate(restCore, restSqs, restSqsTestingAmazonJavaSdk)

  lazy val restCore: Project = Project(
    "rest-core",
    file("rest/rest-core"),
    settings = buildSettings ++ Seq(libraryDependencies := Seq(netty) ++ common ++ httpTesting)
  ) dependsOn(commonTest % "test")

  lazy val restSqs: Project = Project(
    "rest-sqs",
    file("rest/rest-sqs"),
    settings = buildSettings ++ Seq(libraryDependencies := Seq(mysqlConnector % "test") ++ common ++ httpTesting)
  ) dependsOn(api, restCore, core % "test", commonTest % "test")

  lazy val restSqsTestingAmazonJavaSdk: Project = Project(
    "rest-sqs-testing-amazon-java-sdk",
    file("rest/rest-sqs-testing-amazon-java-sdk"),
    settings = buildSettings ++ Seq(libraryDependencies := Seq(amazonJavaSdk) ++ common)
  ) dependsOn(restSqs % "test->test")

  lazy val server: Project = Project(
    "server",
    file("server"),
    settings = buildSettings ++ CustomTasks.distributionSettings
  ) dependsOn(core, storageDatabase, replication, restSqs, commonTest % "test")
}

object CustomTasks {
  implicit def str2pimped(s: String) = new {
    def bold = scala.Console.BOLD + s + scala.Console.RESET
    def green = scala.Console.GREEN + s + scala.Console.RESET
  }

  // Main settings & tasks
  val distributionName = SettingKey[String]("distribution-name", "Name of the distribution directory")
  val distributionDirectory = SettingKey[File]("distribution-directory", "The distribution directory")
  val distributionLibDirectory = SettingKey[File]("distribution-lib-directory", "The distribution library directory")
  val distributionBinDirectory = SettingKey[File]("distribution-bin-directory", "The distribution binary directory")
  val distributionConfDirectory = SettingKey[File]("distribution-conf-directory", "The distribution configuration directory")

  val distributionClean = TaskKey[Unit]("distribution-clean", "Remove previous distribution.")
  val distribution = TaskKey[Unit]("distribution", "Create a distribution containing startup script and all jars.")

  // Helper tasks
  val distributionCopyExternalDependencies = TaskKey[Set[File]]("distribution-copy-external-dependencies", "Copies the external dependencies to the distribution directory")
  val distributionCopyInternalDependencies = TaskKey[Set[File]]("distribution-copy-internal-dependencies", "Copies the internal dependencies to the distribution directory")
  val distributionCopyBin = TaskKey[Set[File]]("distribution-copy-bin", "Copy binaries (scripts).")
  val distributionCopyConf = TaskKey[Set[File]]("distribution-copy-conf", "Copy configuration.")
  val distributionCopyDocs = TaskKey[Set[File]]("distribution-copy-docs", "Copy documentation.")

  val projectDependenciesClosure = TaskKey[Set[ProjectRef]]("project-dependency-closure", "Calculates the closure of the project's dependencies, including transitive ones.")

  val distributionSettings = Seq(
    distributionName <<= (name, version) { (n, v) => n + "-" + v },

    distributionDirectory <<= (target, distributionName) { (t, n) => t / "distribution" / n },

    distributionLibDirectory <<= (distributionDirectory) { (dd) => dd / "lib" },

    distributionBinDirectory <<= (distributionDirectory) { (dd) => dd / "bin" },

    distributionConfDirectory <<= (distributionDirectory) { (dd) => dd / "conf" },

    distributionCopyExternalDependencies <<= (distributionLibDirectory, externalDependencyClasspath in Compile) map { (dld, edc) =>
      IO.copy(edc.files.map(f => (f, dld / f.getName)))
    },

    projectDependenciesClosure <<= (thisProjectRef, state) map { (thr, s) =>
      val structure = Project.structure(s)

      def isCompileConfiguration(configuration: Option[String]): Boolean = {
        configuration.map(_.contains("compile->compile")).getOrElse(true)
      }

      def projectWithTransitiveDependencies(root: ProjectRef, acc: Set[ProjectRef]): Set[ProjectRef] = {
        val dependencies = Project.getProject(root, structure).toList.flatMap(_.dependencies
          // We only want compile dependencies
          .filter(cpDep => isCompileConfiguration(cpDep.configuration))
          .map(_.project))

        dependencies.foldLeft(acc)((newAcc, dep) => {
          if (newAcc.contains(dep)) newAcc else projectWithTransitiveDependencies(dep, newAcc + dep)
        })
      }

      projectWithTransitiveDependencies(thr, Set(thr))
    },

    distributionCopyInternalDependencies <<= (thisProjectRef, state, distributionLibDirectory, projectDependenciesClosure) map { (thr, s, dld, pdc) =>
      val structure = Project.structure(s)

      val packageTaskKey = (packageBin in Compile).task

      println("1 " + structure.data)
      println("2 " + pdc.flatMap(p => (packageTaskKey in p).get(structure.data)))

      val packageAllTask = pdc.flatMap(pr => (packageTaskKey in pr).get(structure.data)).toList

      pdc.map(pr => {
        val packageTaskOption = (packageTaskKey in pr).get(structure.data)
        packageTaskOption.map(packageTask => packageTask.map(file => println("result: " + file)))
      })

      packageAllTask.map { jars =>
        println("jars = " + jars)
        jars.map(f => println("f = " + f))
      }

      Set()
    },

    distributionCopyBin <<= (distributionBinDirectory, resourceDirectory in Compile) map { (dbd, rd) =>
      IO.copy(rd.listFiles().filter(_.getName.startsWith("run")).map(script => (script, dbd / script.getName)))
    },

    distributionCopyConf <<= (distributionConfDirectory) map { (dcd) =>
      Set()
    },

    distributionCopyDocs <<= (distributionDirectory) map { (dd) =>
      Set()
    },

    distributionClean <<= (distributionDirectory) map { (dd) => IO.delete(dd) },

    distribution <<= (streams, version, distributionDirectory,
      distributionCopyBin, distributionCopyConf, distributionCopyDocs,
      distributionCopyExternalDependencies, distributionCopyInternalDependencies) map { (s, v, dd, _, _, _, _, _) =>
      s.log.info("ElasticMQ distribution for version " + v.green + " created successfully in: " + dd.getPath)
    }
  )
}
