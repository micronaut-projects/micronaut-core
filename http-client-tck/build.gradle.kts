plugins {
    id("io.micronaut.build.internal.convention-library")
}
dependencies {
    annotationProcessor(project(":inject-java"))
    api("org.junit.jupiter:junit-jupiter:5.9.1")
    implementation(libs.managed.reactor)
    implementation(project(":http-server-netty"))
    implementation(project(":http-client-core"))
}
tasks.named<Test>("test") {
    useJUnitPlatform()
}
