plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    testImplementation(projects.httpServerNetty)
    testImplementation(projects.httpClient)
    testImplementation(projects.httpClientTck)
    testImplementation(libs.junit.platform.engine)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
