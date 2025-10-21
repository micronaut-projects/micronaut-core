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
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

graalvmNative {
    toolchainDetection = false
    metadataRepository {
        enabled = true
    }
    binaries {
        configureEach {
            if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
                buildArgs.add("--initialize-at-build-time=org.junit.platform.suite.engine.IsSuiteClass")
                buildArgs.add("--initialize-at-build-time=org.junit.platform.suite.engine.IsPotentialTestContainer")
                buildArgs.add("--strict-image-heap")
            }
            resources.autodetect()
        }
    }
}
