plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    testImplementation(projects.httpServerNetty)
    testImplementation(projects.httpClientJavanet)
    testImplementation(projects.httpClientTck)
    testImplementation(libs.junit.platform.engine)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    systemProperty("jdk.httpclient.HttpClient.log", "all")
}
