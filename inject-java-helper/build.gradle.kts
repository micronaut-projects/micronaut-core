plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

dependencies {
    // We need some Jar that is not going to be on the inject-java test classpath
    compileOnly(libs.vertx)

    api(projects.micronautCore)
    api(projects.micronautContext)
}
