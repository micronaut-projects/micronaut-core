plugins {
    id("java")
    id("org.graalvm.buildtools.native")
}

dependencies {
    implementation(projects.micronautHttpServerTck)
    implementation(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(projects.micronautHttpClient)
    testImplementation(libs.junit.platform.engine)
    testImplementation(libs.logback.classic)
    testImplementation(platform(libs.test.boms.micronaut.validation))
    testImplementation(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }
    testRuntimeOnly(libs.bcpkix)
}

configurations {
    nativeImageTestClasspath {
        exclude(module = "groovy-test")
    }
}

tasks {
    test {
        useJUnitPlatform()
        systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
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
