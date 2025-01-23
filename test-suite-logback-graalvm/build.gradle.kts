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
