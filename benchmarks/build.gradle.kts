plugins {
    id("io.micronaut.build.internal.convention-base")
    id("me.champeau.jmh") version "0.7.2"
}

val typeCheckTestSourceSet = sourceSets.create("typeCheckTest") {
    compileClasspath += sourceSets.get("jmh").output
    runtimeClasspath += sourceSets.get("jmh").output
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    jmhAnnotationProcessor(projects.micronautInjectJava)
    jmhAnnotationProcessor(libs.jmh.generator.annprocess)

    annotationProcessor(platform(libs.test.boms.micronaut.validation))
    annotationProcessor(libs.micronaut.validation.processor) {
        exclude(group = "io.micronaut")
    }

    compileOnly(platform(libs.test.boms.micronaut.validation))
    compileOnly(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }

    api(projects.micronautInject)
    api(projects.micronautInjectJavaTest)
    api(projects.micronautHttpServer)
    api(projects.micronautHttpServerNetty)
    api(projects.micronautHttpClient)
    api(projects.micronautJacksonDatabind)
    api(projects.micronautRouter)
    api(projects.micronautRuntime)
    api(projects.micronautCoreReactive)

    api(platform(libs.test.boms.micronaut.validation))
    api(libs.managed.reactor)
    api(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }

    jmh(libs.jmh.core)

    "typeCheckTestImplementation"(libs.junit.jupiter)
    "typeCheckTestImplementation"(libs.micronaut.test.type.pollution)
    "typeCheckTestImplementation"(libs.bytebuddy.agent)
    "typeCheckTestImplementation"(libs.bytebuddy)
    "typeCheckTestRuntimeOnly"(libs.junit.platform.engine)
}

configurations {
    get("typeCheckTestImplementation").extendsFrom(jmhImplementation.get(), implementation.get())
    get("typeCheckTestRuntimeOnly").extendsFrom(jmhRuntimeOnly.get(), runtimeOnly.get())
}

jmh {
    includes = listOf("io.micronaut.http.server.StartupBenchmark")
    duplicateClassesStrategy = DuplicatesStrategy.WARN
}

tasks {
    processJmhResources {
        duplicatesStrategy = DuplicatesStrategy.WARN
    }
}

val typeCheckTest = tasks.register<Test>("typeCheckTest") {
    description = "Runs type check tests."
    group = "verification"

    testClassesDirs = typeCheckTestSourceSet.output.classesDirs
    classpath = typeCheckTestSourceSet.runtimeClasspath

    useJUnitPlatform()
}

tasks {
    check {
        dependsOn(typeCheckTest)
    }
}

listOf("spotlessJavaCheck", "checkstyleMain", "checkstyleJmh").forEach {
    tasks.named(it) {
        enabled = false
    }
}
