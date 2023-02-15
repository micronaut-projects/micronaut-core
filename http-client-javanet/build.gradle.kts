plugins {
    id("io.micronaut.build.internal.convention-library")
}

micronautBuild {
    core {
        usesMicronautTestSpock()
    }
}

dependencies {
    annotationProcessor(project(":inject-java"))
    implementation(project(":http-client-core"))
    implementation(libs.managed.reactor)
    testImplementation(projects.jacksonDatabind)
    testImplementation(projects.httpServerNetty)
    testImplementation(libs.bcpkix)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    systemProperty("jdk.httpclient.HttpClient.log", "all")
    // We have to fork the JVM because the JDK HttpClient relies on system properties which are then cached...
    setForkEvery(1)
}
