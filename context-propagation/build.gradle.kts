plugins {
    id("io.micronaut.build.internal.convention-library")
    alias(libs.plugins.managed.kotlin.jvm)
    alias(libs.plugins.managed.kotlin.kapt)
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    annotationProcessor(projects.micronautGraal)

    api(projects.micronautContext)
    api(projects.micronautInject)
    api(projects.micronautAop)

    compileOnly(projects.micronautCoreReactive)
    compileOnly(libs.managed.reactor)

    compileOnly(platform(libs.test.boms.micronaut.reactor))
    compileOnly("io.micrometer:context-propagation")

    compileOnly(platform(libs.test.boms.micronaut.rxjava2))
    compileOnly(platform(libs.test.boms.micronaut.rxjava3))
    compileOnly(platform(libs.test.boms.micronaut.reactor))

    compileOnly("io.micronaut.rxjava2:micronaut-rxjava2-http-client") {
        exclude(group = "io.micronaut")
    }
    compileOnly("io.micronaut.rxjava3:micronaut-rxjava3-http-client") {
        exclude(group = "io.micronaut")
    }

    testAnnotationProcessor(projects.micronautInjectJava)
    testImplementation("io.micrometer:context-propagation")

    testImplementation(platform(libs.test.boms.micronaut.rxjava2))
    testImplementation(platform(libs.test.boms.micronaut.rxjava3))
    testImplementation(platform(libs.test.boms.micronaut.reactor))

    testImplementation(projects.micronautInjectGroovy)
    testImplementation(projects.micronautRuntime)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautCoreReactive)
    testImplementation(projects.micronautInjectJavaTest)
    testImplementation(projects.micronautHttpClient)
    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(libs.managed.snakeyaml)

    testImplementation("io.micronaut.rxjava2:micronaut-rxjava2-http-client") {
        exclude(group = "io.micronaut")
    }
    testImplementation("io.micronaut.rxjava3:micronaut-rxjava3-http-client") {
        exclude(group = "io.micronaut")
    }
    testImplementation("io.micronaut.reactor:micronaut-reactor-http-client") {
        exclude(group = "io.micronaut")
    }
}

// Kotlin
dependencies {
    kapt(projects.micronautInjectJava)
    kaptTest(projects.micronautInjectJava)

    compileOnly(libs.managed.kotlin.stdlib.jdk8)
    compileOnly(libs.managed.kotlinx.coroutines.core)

    testImplementation(libs.managed.kotlin.stdlib.jdk8)
    testImplementation(libs.managed.kotlinx.coroutines.core)
    testImplementation(libs.managed.kotlinx.coroutines.reactor)
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "17"
        }
    }
}

tasks {
    compileTestKotlin {
        kotlinOptions {
            jvmTarget = "17"
        }
    }
}
