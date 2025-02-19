plugins {
    id("io.micronaut.build.internal.convention-library")
    alias(libs.plugins.managed.kotlin.jvm)
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    annotationProcessor(projects.micronautGraal)
    api(projects.micronautContext)
    api(projects.micronautCoreReactive)
    api(projects.micronautContextPropagation)
    implementation(libs.managed.reactor)
    compileOnly(libs.managed.kotlinx.coroutines.core)
    compileOnly(libs.managed.kotlinx.coroutines.reactor)

    compileOnly(libs.managed.jackson.annotations)

    testCompileOnly(projects.micronautInjectGroovy)
    testAnnotationProcessor(projects.micronautInjectJava)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautInject)
    testImplementation(projects.micronautRuntime)
    testImplementation(libs.logback.classic)
    testImplementation(libs.jazzer.junit)
    testImplementation(libs.jazzer.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.micronaut.test.junit5) {
        exclude(group= "io.micronaut")
    }
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "17"
    }
}

//compileJava.options.fork = true
//compileJava.options.forkOptions.jvmArgs = ['-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005']

spotless {
    java {
        targetExclude("**/io/micronaut/http/uri/QueryStringDecoder.java")
    }
}
