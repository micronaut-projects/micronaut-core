import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    id("io.micronaut.build.internal.convention-test-library")
    id("io.micronaut.build.internal.functional-test")
    id("java-test-fixtures")
}

micronautBuild {
    core {
        usesMicronautTestJunit()
        usesMicronautTestSpock()
    }
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    annotationProcessor(platform(libs.test.boms.micronaut.validation))
    annotationProcessor(libs.micronaut.validation.processor) {
        exclude(group = "io.micronaut")
    }

    api(projects.micronautCoreProcessor)

    testImplementation(projects.micronautContext)
    testImplementation(libs.managed.netty.codec.http)
    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautHttpClient)
    testImplementation(projects.micronautHttpClientJdk)
    testImplementation(platform(libs.test.boms.micronaut.validation))
    testImplementation(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.micronaut.validation.processor) { // For Groovy
        exclude(group = "io.micronaut")
    }
    testImplementation(projects.micronautInjectGroovy)
    testImplementation(projects.micronautInjectJava)
    testImplementation(projects.micronautInjectJavaTest)
    testImplementation(projects.micronautManagement)
    testImplementation(projects.micronautRuntime)
    testImplementation(projects.micronautInject)
    testImplementation(projects.micronautFunctionClient)
    testImplementation(projects.micronautFunctionWeb)
    testImplementation(libs.micronaut.session) {
        exclude(group = "io.micronaut")
    }

    // Adding these for now since micronaut-test isnt resolving correctly ... probably need to upgrade gradle there too
    testImplementation(libs.junit.jupiter.api)

    testImplementation(libs.jcache)
    testImplementation(libs.managed.groovy.json)
    testImplementation(libs.managed.groovy.templates)
    // tag::testcontainers-dependencies[]
    testImplementation(libs.testcontainers.spock)
    // end::testcontainers-dependencies[]
    testImplementation(libs.awaitility)
    testImplementation(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testAnnotationProcessor(projects.testSuiteHelper)
    testAnnotationProcessor(projects.micronautInjectJava)
    testAnnotationProcessor(projects.testSuite)
    testAnnotationProcessor(platform(libs.test.boms.micronaut.validation))
    testAnnotationProcessor(libs.micronaut.validation.processor) {
        exclude(group = "io.micronaut")
    }

    testRuntimeOnly(platform(libs.test.boms.micronaut.aws))
    testRuntimeOnly(libs.h2)
    testRuntimeOnly(libs.junit.vintage)
    testRuntimeOnly(libs.logback.classic)
    testRuntimeOnly(libs.aws.java.sdk.lambda)

    // needed for HTTP/2 tests
    testImplementation(platform(libs.boms.netty))
    testImplementation(libs.netty.tcnative)
    testImplementation(libs.netty.tcnative.boringssl)
    testImplementation(libs.netty.tcnative.boringssl) {
        artifact {
            classifier = if (Os.isFamily(Os.FAMILY_MAC)) {
                if (Os.isArch("aarch64")) {
                    "osx-aarch_64"
                } else {
                    "osx-x86_64"
                }
            } else {
                "linux-x86_64"
            }
        }
    }
    testImplementation(projects.micronautHttpNettyHttp3)
    testImplementation(libs.logbook.netty)
    testImplementation(libs.logback.classic)
    testImplementation(libs.bcpkix)

    testImplementation(libs.managed.reactor)

    testFixturesApi(libs.spock)
    testFixturesApi(libs.managed.groovy)
    testFixturesApi(libs.jetbrains.annotations)

    testImplementation(libs.javax.persistence)
    testImplementation(libs.jakarta.persistence)

    testCompileOnly(projects.micronautInjectJavaHelper2)

    testImplementation(platform(libs.test.boms.micronaut.rxjava2))
    testImplementation(platform(libs.test.boms.micronaut.rxjava3))

    testImplementation("io.micronaut.rxjava2:micronaut-rxjava2") {
        exclude(group = "io.micronaut")
    }
    testImplementation("io.micronaut.rxjava3:micronaut-rxjava3") {
        exclude(group = "io.micronaut")
    }
}

tasks {
    test {
        // Prevent scanning classes with missing classes
        exclude("**/classnotfound/**")
    }
}
