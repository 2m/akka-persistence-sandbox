val akkaVersion = "2.5.2"
scalaVersion := "2.12.2"

libraryDependencies ++= Seq(
  "com.typesafe.akka"         %% "akka-persistence" % akkaVersion,
  "org.iq80.leveldb"          %  "leveldb"                       % "0.7",
  "org.fusesource.leveldbjni" %  "leveldbjni-all"                % "1.8",

  "com.github.dnvriend" %% "akka-persistence-jdbc" % "2.5.1.0",
  "org.postgresql"       % "postgresql"            % "9.4.1208"
)

enablePlugins(JavaAppPackaging)
