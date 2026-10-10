plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

dependencies {
    compileOnly(projects.micronautCore)
}
