val akkaVersion = "2.3.11"
scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka"         %% "akka-persistence-experimental" % akkaVersion,
  "org.iq80.leveldb"          %  "leveldb"                       % "0.7",
  "org.fusesource.leveldbjni" %  "leveldbjni-all"                % "1.7"
)

updateOptions := updateOptions.value.withConsolidatedResolution(true)
enablePlugins(JavaAppPackaging)

