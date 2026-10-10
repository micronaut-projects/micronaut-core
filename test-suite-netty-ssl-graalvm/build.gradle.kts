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
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

graalvmNative {
    toolchainDetection = false
    metadataRepository {
        enabled = true
        // The metadata of these modules registers ReferenceCountUtil, depending on the version of the repository, which
        // would hide whether micronaut-http-netty registers the methods that its static initializer looks up (see CompressionTest)
        excludedModules.addAll("io.netty:netty-codec", "io.netty:netty-handler-proxy")
    }
    binaries {
        all {
            if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
                buildArgs.add("--initialize-at-build-time=org.junit.platform.commons.logging.LoggerFactory\$DelegatingLogger")
                buildArgs.add("--initialize-at-build-time=org.junit.platform.suite.engine.IsSuiteClass")
                buildArgs.add("--initialize-at-build-time=org.junit.platform.suite.engine.IsPotentialTestContainer")
                buildArgs.add("-H:+SharedArenaSupport")
            }
            resources.autodetect()
        }
    }
}
