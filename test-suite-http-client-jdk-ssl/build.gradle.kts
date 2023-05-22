plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

description = "Test suite for the Java.net HTTP client with SSL where hostname resolution is disabled"

dependencies {
    testImplementation(projects.httpServerNetty)
    testImplementation(projects.httpClientJdk)
    testImplementation(libs.spock)
    testImplementation(libs.managed.reactor)
    testImplementation(projects.jacksonDatabind)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    systemProperty("jdk.httpclient.HttpClient.log", "all")
    systemProperty("jdk.internal.httpclient.disableHostnameVerification", "true")
}
