// tag::gradle[]
plugins {
    scala
}

dependencies {
    val scalaVersion = "3.3.8"
    val micronautScalaVersion = "1.0.0-SNAPSHOT"
    implementation("org.scala-lang:scala3-library_3:$scalaVersion")
    scalaCompilerPlugins("io.micronaut.scala:micronaut-inject-scala_3.3.8:$micronautScalaVersion")
    runtimeOnly("io.micronaut.scala:micronaut-inject-scala_3.3.8:$micronautScalaVersion")
}

tasks.withType<org.gradle.api.tasks.scala.ScalaCompile>().configureEach {
    scalaCompileOptions.additionalParameters.add("-release:25")
    scalaCompileOptions.additionalParameters.add("-Yexplicit-nulls")
}
// end::gradle[]
