import DexItDockerKeys._

enablePlugins(DexItDockerPlugin, ItTestPlugin)

description := "DEX integration tests"

libraryDependencies ++= Dependencies.itTest ++ Dependencies.silencer

docker := docker.dependsOn(LocalProject("waves-integration-it") / docker).value
inTask(docker)(
  Seq(
    imageNames := Seq(ImageName("com.wavesplatform/dex-it:latest")),
    exposedPorts += 6886,
    additionalFiles ++= Seq(
      (Test / sourceDirectory).value / "container" / "wallet"
    ) ++ sbt.IO.listFiles((Test / resourceDirectory).value / "nodes")
  )
)
