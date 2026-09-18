import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

// Runs the test-suite sources with the bean definitions, introspections and proxies written as Java source by
// sourcegen-generator-java, compiled by javac in a later round, instead of being written as bytecode.
// The sources are shared rather than depending on :test-suite, whose jar holds the bytecode generated definitions of
// the same classes.
val testSuiteDir = rootProject.layout.projectDirectory.dir("test-suite")

// The processor path carries the Java source generator in place of the sourcegen bytecode writer: without a bytecode
// writer the processors write what they generate as Java source, which javac compiles in a later round
listOf(configurations.annotationProcessor, configurations.testAnnotationProcessor).forEach { configuration ->
    configuration {
        exclude(group = "io.micronaut.sourcegen", module = "micronaut-sourcegen-bytecode-writer")
    }
}

sourceSets {
    main {
        java.setSrcDirs(listOf(testSuiteDir.dir("src/main/java")))
        resources.setSrcDirs(listOf(testSuiteDir.dir("src/main/resources")))
    }
    test {
        java.setSrcDirs(listOf(testSuiteDir.dir("src/test/java")))
        groovy.setSrcDirs(listOf(testSuiteDir.dir("src/test/groovy"), testSuiteDir.dir("src/testFixtures/groovy")))
        resources.setSrcDirs(listOf(testSuiteDir.dir("src/test/resources")))
    }
}

micronautBuild {
    core {
        usesMicronautTestJunit()
        usesMicronautTestSpock()
    }
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    annotationProcessor(mnSourcegen.micronaut.sourcegen.generator.java)
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

    testImplementation(libs.junit.jupiter.api)

    testImplementation(libs.jcache)
    testImplementation(libs.managed.groovy.json)
    testImplementation(libs.managed.groovy.templates)
    testImplementation(libs.testcontainers.spock)
    testImplementation(libs.awaitility)
    testImplementation(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testAnnotationProcessor(projects.testSuiteHelper)
    testAnnotationProcessor(projects.micronautInjectJava)
    testAnnotationProcessor(mnSourcegen.micronaut.sourcegen.generator.java)
    testAnnotationProcessor(projects.testSuiteJavaSourcegen)
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

    // test-suite's test fixtures, compiled here with the tests
    testImplementation(libs.spock)
    testImplementation(libs.managed.groovy)
    testImplementation(libs.jetbrains.annotations)

    testImplementation(libs.javax.persistence)
    testImplementation(libs.jakarta.persistence)

    testCompileOnly(projects.micronautInjectJavaHelper2)

    testImplementation(platform(libs.test.boms.micronaut.rxjava3))

    testImplementation("io.micronaut.rxjava3:micronaut-rxjava3") {
        exclude(group = "io.micronaut")
    }
    testImplementation("io.micronaut.rxjava3:micronaut-rxjava3-http-client") {
        exclude(group = "io.micronaut")
    }

    testImplementation(libs.junit.jupiter.params)
}

tasks {
    test {
        // Prevent scanning classes with missing classes
        exclude("**/classnotfound/**")
        // The shared tests resolve files relative to test-suite
        workingDir = testSuiteDir.asFile
    }
}
