plugins {
    id("io.micronaut.build.internal.convention-test-library")
}
dependencies {
    testImplementation(projects.httpServerNetty)
    testImplementation(projects.httpClientJavanet)
    testImplementation(projects.httpServerTck)
    testImplementation(libs.junit.platform.engine)
}
