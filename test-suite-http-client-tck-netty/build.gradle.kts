plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

dependencies {
    testImplementation(projects.httpServerNetty)
    implementation(projects.jacksonDatabind)
    testImplementation(projects.httpClient)
    testImplementation(projects.httpClientTck)
    testImplementation(libs.junit.platform.engine)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
