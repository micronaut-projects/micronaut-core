plugins {
    id("io.micronaut.build.internal.convention-library")
}

import org . apache . tools . ant . taskdefs . condition . Os

        micronautBuild {
            core {
                usesMicronautTestJunit()
                usesMicronautTestSpock()
            }
        }

tasks {
    test {
        maxHeapSize = "1G"
    }
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    annotationProcessor(projects.micronautGraal)

    api(projects.micronautHttpServer)
    api(projects.micronautCore)
    api(projects.micronautHttpNetty)
    api(libs.managed.netty.codec.http)
    implementation(libs.managed.reactor)
    compileOnly(projects.micronautJacksonDatabind)
    compileOnly(projects.micronautWebsocket)
    compileOnly(libs.managed.kotlin.stdlib)
    compileOnly(libs.managed.netty.transport.native.unix.common)
    compileOnly(libs.managed.netty.incubator.codec.http3)
    compileOnly(libs.brotli4j)

    testImplementation(libs.jmh.core)
    testAnnotationProcessor(libs.jmh.generator.annprocess)

    testCompileOnly(projects.micronautInjectGroovy)
    testCompileOnly(libs.jetbrains.annotations)

    testAnnotationProcessor(projects.micronautInjectJava)
    testAnnotationProcessor(platform(libs.test.boms.micronaut.validation))
    testAnnotationProcessor(libs.micronaut.validation.processor) {
        exclude(group = "io.micronaut")
    }

    testImplementation(platform(libs.test.boms.micronaut.validation))
    testImplementation(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.micronaut.validation.processor) { // For Groovy
        exclude(group = "io.micronaut")
    }

    testImplementation(projects.micronautInject)
    testImplementation(projects.micronautInjectJavaTest)
    testImplementation(projects.micronautHttpClient)
    testImplementation(libs.spotbugs)
    testImplementation(libs.managed.netty.incubator.codec.http3)
    testImplementation(libs.bcpkix)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautHttpTck)
// Add Micronaut Jackson XML after v4 Migration
//    testImplementation(libs.managed.micronaut.xml) {
//        exclude module:'micronaut-inject'
//        exclude module:'micronaut-http'
//        exclude module:'micronaut-bom'
//    }
    testImplementation(libs.managed.jackson.databind)

    // http impls for tests
    testImplementation(libs.vertx)
    testImplementation(libs.vertx.webclient)
    testImplementation(libs.httpcomponents.client)
    testImplementation(libs.httpcomponents.mime)
    testImplementation(libs.jetty.alpn.openjdk8.client)

    testImplementation(libs.managed.groovy.json)
    testImplementation(libs.managed.groovy.templates)

    testImplementation(libs.managed.netty.transport.native.epoll) {
        artifact {
            classifier = "linux-x86_64"
        }
    }
    testImplementation(libs.managed.netty.transport.native.kqueue) {
        artifact {
            classifier = if (Os.isArch("aarch64")) {
                "osx-aarch_64"
            } else {
                "osx-x86_64"
            }
        }
    }
    testImplementation(libs.managed.netty.tcnative.boringssl.static) {
        artifact {
            if (Os.isFamily("mac")) {
                classifier = if (Os.isArch("aarch64")) {
                    "osx-aarch_64"
                } else {
                    "osx-x86_64"
                }
            } else {
                classifier = "linux-x86_64"
            }
        }
    }
    testImplementation(libs.logback.classic)

    // Adding these for now since micronaut-test isnt resolving correctly ... probably need to upgrade gradle there too
    testImplementation(libs.junit.jupiter.api)
    testImplementation(projects.micronautWebsocket)

    testImplementation(libs.mimepull)

    testImplementation(libs.micronaut.test.junit5) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.junit.jupiter.api)
}

tasks.withType<Test>().configureEach {
    forkEvery = 100
    maxParallelForks = 4
    useJUnitPlatform()
}

//tasks.withType(Test).configureEach {
//    testLogging {
//        showStandardStreams = true
//        exceptionFormat = 'full'
//    }
//}

//compileTestGroovy.groovyOptions.forkOptions.jvmArgs = ['-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005']
//compileJava.options.fork = true
//compileJava.options.forkOptions.jvmArgs = ['-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005']
