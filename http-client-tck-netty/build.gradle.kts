plugins {
    id("io.micronaut.build.internal.convention-library")
}
dependencies {
    testImplementation(project(":http-client-tck"))
    testImplementation(project(":runtime"))
    testImplementation(project(":http-client"))
    testImplementation(project(":jackson-databind"))
}
tasks.named<Test>("test") {
    useJUnitPlatform()
}
