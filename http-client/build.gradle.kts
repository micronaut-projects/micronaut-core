import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    id("io.micronaut.build.internal.convention-library")
}

micronautBuild {
    core {
        usesMicronautTestJunit()
        usesMicronautTestSpock()
    }
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    api(projects.micronautContext)
    api(projects.micronautHttpClientCore)
    api(projects.micronautWebsocket)
    api(projects.micronautHttpNetty)
    api(libs.managed.netty.handler.proxy)

    compileOnly(projects.micronautHttpNettyHttp3)
    testImplementation(projects.micronautHttpNettyHttp3)

    testAnnotationProcessor(platform(libs.test.boms.micronaut.validation))
    testAnnotationProcessor(libs.micronaut.validation.processor) {
        exclude(group = "io.micronaut")
    }
    testAnnotationProcessor(projects.micronautInjectJava)

    testCompileOnly(projects.micronautInjectGroovy)
    testImplementation(projects.micronautInject)

    testImplementation(platform(libs.test.boms.micronaut.validation))
    testImplementation(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.micronaut.validation.processor) { // For Groovy
        exclude(group = "io.micronaut")
    }

    implementation(libs.managed.reactor)

    testImplementation(projects.micronautRetry)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(libs.wiremock)
    testImplementation(libs.logback.classic)
    testImplementation(libs.bcpkix)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.awaitility)
    testImplementation(libs.managed.reactor.test)

    testRuntimeOnly(libs.managed.netty.tcnative.boringssl.static) {
        artifact {
            classifier = if (Os.isArch("aarch64")) {
                "osx-aarch_64"
            } else {
                "osx-x86_64"
            }
        }
    }
}

//tasks.withType(Test).configureEach {
//    jvmArgs('-Dio.netty.leakDetection.level=paranoid')
//    testLogging {
//        showStandardStreams = true
//    }
//    beforeTest {
//        System.out.println("STARTING: ${it.className}.$it.name")
//        System.out.flush()
//    }
//    afterTest {
//        System.out.println("FINISHED: ${it.className}.$it.name")
//        System.out.flush()
//    }
//}
