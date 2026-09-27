plugins {
    id("io.micronaut.build.internal.convention-library")
}

description = "An API gateway built on the functional routes and the raw HTTP client"

micronautBuild {
    binaryCompatibility {
        // a new module: nothing to compare with before its first release
        enabled = false
    }
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)

    api(projects.micronautRouter)
    api(projects.micronautHttpServer)
    api(projects.micronautHttpClientCore)
    api(projects.micronautRetry)
    api(projects.micronautDiscoveryCore)

    // the read-only gateway endpoint, when the management module is present
    compileOnly(projects.micronautManagement)

    testAnnotationProcessor(projects.micronautInjectJava)
    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(projects.micronautHttpClient)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautManagement)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.logback.classic)
    testImplementation(libs.micronaut.test.netty.leak)
    testImplementation(libs.managed.reactor)
    testRuntimeOnly(libs.managed.snakeyaml)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // the leak extension fails a test class that leaks a buffer
    systemProperty("io.netty.leakDetection.level", "paranoid")
    systemProperty("io.netty.leakDetection.targetRecords", "100")
    systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
    maxHeapSize = "1G"
}
