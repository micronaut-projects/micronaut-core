plugins {
    id("io.micronaut.build.internal.convention-base")
    id("me.champeau.jmh") version "0.7.3"
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
    api(projects.micronautInjectPython)
    api(projects.micronautContextPython)
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
}

val jmhIncludes = providers.gradleProperty("jmh.includes")
    .map { it.split(",").map(String::trim).filter(String::isNotEmpty) }
    .getOrElse(listOf("io.micronaut.http.server.StartupBenchmark"))
val jmhFork = providers.gradleProperty("jmh.fork").map(String::toInt).getOrElse(1)
val jmhIterations = providers.gradleProperty("jmh.iterations").map(String::toInt).getOrElse(10)
val jmhWarmupIterations = providers.gradleProperty("jmh.warmupIterations").map(String::toInt).getOrElse(5)
val jmhProfilers = providers.gradleProperty("jmh.profilers")
    .map { it.split(",").map(String::trim).filter(String::isNotEmpty) }
    .getOrElse(emptyList())
val jmhHumanOutput = providers.gradleProperty("jmh.humanOutput")
    .map(layout.projectDirectory::file)

jmh {
    includes = jmhIncludes
    fork = jmhFork
    iterations = jmhIterations
    warmupIterations = jmhWarmupIterations
    profilers = jmhProfilers
    humanOutputFile.set(jmhHumanOutput)
    duplicateClassesStrategy = DuplicatesStrategy.WARN
}

tasks {
    processJmhResources {
        duplicatesStrategy = DuplicatesStrategy.WARN
    }

    named<Jar>("jmhJar") {
        isZip64 = true
        manifest.attributes["Multi-Release"] = "true"
        exclude("GRAALPY-VFS/**")
    }
}

listOf("spotlessJavaCheck", "checkstyleMain", "checkstyleJmh").forEach {
    tasks.named(it) {
        enabled = false
    }
}
