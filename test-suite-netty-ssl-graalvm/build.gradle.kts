plugins {
    id("java")
    id("org.graalvm.buildtools.native")
}

description = "Test suite for Bouncy Castle self signed certificate in native image"

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    testAnnotationProcessor(projects.micronautInjectJava)
    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(projects.micronautHttpClient)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(libs.bcpkix)
    testImplementation(libs.logback.classic)
    testImplementation(libs.micronaut.test.junit5) {
        exclude(group="io.micronaut")
    }
}

graalvmNative {
    toolchainDetection = false
    metadataRepository {
        enabled = true
    }
    binaries {
        all {
            resources.autodetect()
        }
    }
}
