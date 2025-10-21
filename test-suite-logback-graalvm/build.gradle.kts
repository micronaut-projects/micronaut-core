plugins {
    id("java")
    id("org.graalvm.buildtools.native")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    testImplementation(projects.micronautContext)
    testImplementation(libs.logback.classic)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

graalvmNative {
    toolchainDetection = false
    metadataRepository {
        enabled = true
    }
    binaries {
        all {
            if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
                buildArgs.add("--initialize-at-build-time=org.junit.platform.suite.engine.IsSuiteClass")
                buildArgs.add("--initialize-at-build-time=org.junit.platform.suite.engine.IsPotentialTestContainer")
                buildArgs.add("--strict-image-heap")
            }
            resources.autodetect()
        }
    }
}
