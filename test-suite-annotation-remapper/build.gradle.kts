plugins {
    id("java")
    id("org.graalvm.buildtools.native")
}

description = "Test suite for definitions with added enum values"

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    testAnnotationProcessor(projects.testSuiteAnnotationRemapperVisitor)
    testAnnotationProcessor(projects.micronautInjectJava)
    testImplementation(projects.micronautHttpServerNetty)
    implementation(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautHttpClient)
    testImplementation(libs.logback.classic)
    testImplementation(libs.micronaut.test.junit5) {
        exclude(group = "io.micronaut")
    }
}

graalvmNative {
    toolchainDetection = false
    metadataRepository {
        enabled = true
    }
    binaries {
        configureEach {
            resources.autodetect()
        }
    }
}
