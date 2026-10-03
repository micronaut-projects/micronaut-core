plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

dependencies {
    testImplementation(libs.spock)
    testImplementation(projects.micronautContext)
    testImplementation(libs.logback.classic)
}

tasks.withType<Test>().configureEach {
    // The module directory is the working directory of the tests. Its logback.xml is part of what they
    // test, and it is not on the classpath
    inputs.file("logback.xml").withPropertyName("workingDirectoryLogbackXml").withPathSensitivity(PathSensitivity.RELATIVE)
}
