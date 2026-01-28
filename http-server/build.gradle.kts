plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    api(projects.micronautHttp)
    api(projects.micronautRouter)

    compileOnly(projects.micronautWebsocket)
    compileOnly(projects.micronautJacksonDatabind)
    compileOnly(libs.managed.kotlinx.coroutines.core)
    compileOnly(libs.managed.kotlinx.coroutines.reactor)
    compileOnly(libs.micronaut.runtime.groovy)
    implementation(libs.managed.reactor)
    annotationProcessor(projects.micronautInjectJava)

    testImplementation(libs.managed.netty.codec.http)

    testAnnotationProcessor(projects.micronautInjectJava)
    testAnnotationProcessor(platform(libs.test.boms.micronaut.validation))
    testAnnotationProcessor(libs.micronaut.validation.processor) {
        exclude(group = "io.micronaut")
    }
    testImplementation(platform(libs.test.boms.micronaut.validation))
    testImplementation(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.micronaut.test.junit5) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
}

//compileTestGroovy.groovyOptions.forkOptions.jvmArgs = ['-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005']
