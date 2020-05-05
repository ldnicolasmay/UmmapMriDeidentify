name := "UmmapMriDeidentify"

version := "0.1"

scalaVersion := "2.13.1"

// https://mvnrepository.com/artifact/info.picocli/picocli
libraryDependencies += "info.picocli" % "picocli" % "4.2.0"

// https://mvnrepository.com/artifact/org.zeroturnaround/zt-zip
libraryDependencies += "org.zeroturnaround" % "zt-zip" % "1.14"

libraryDependencies += "org.scalactic" %% "scalactic" % "3.1.1"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.1" % "test"
