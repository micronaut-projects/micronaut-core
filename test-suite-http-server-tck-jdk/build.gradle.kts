plugins {
    id("io.micronaut.build.internal.convention-test-library")
}
dependencies {
    testImplementation(projects.httpServerNetty)
    testImplementation(projects.httpClientJdk)
    testImplementation(projects.httpServerTck)
    testImplementation(libs.junit.platform.engine)
}

tasks.withType(Test::class) {
    // systemProperty("jdk.httpclient.HttpClient.log", "all") // Uncomment to enable logging

    // These restricted headers are set in the server TCK
    systemProperty("jdk.httpclient.allowRestrictedHeaders", "Host,Connection,Content-Length")
}
