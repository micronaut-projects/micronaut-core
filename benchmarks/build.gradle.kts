plugins {
    id("io.micronaut.build.internal.convention-base")
    id("io.micronaut.build.internal.convention-python")
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
    // the access logger's ConnectionMetadata resolves the QUIC channel class when it is initialized
    api(projects.micronautHttpNettyHttp3)
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
val jmhPoolSizes = providers.gradleProperty("jmh.poolSizes")
    .map { it.split(",").map(String::trim).filter(String::isNotEmpty) }
// -Pjmh.benchmarkParameters='mode=STATIC;warm=false|true;fixture=shop' narrows a benchmark's @Param matrix
val jmhBenchmarkParameters = providers.gradleProperty("jmh.benchmarkParameters")
    .map { spec ->
        spec.split(";").map(String::trim).filter(String::isNotEmpty).associate { assignment ->
            val (name, values) = assignment.split("=", limit = 2)
            name.trim() to values.split("|").map(String::trim).filter(String::isNotEmpty)
        }
    }
val jmhHumanOutput = providers.gradleProperty("jmh.humanOutput")
    .map(layout.projectDirectory::file)
// Benchmark switches read by BenchOptions in the forked JMH JVM. A -D on the Gradle command line
// only reaches Gradle itself, so they are exposed as -P properties and forwarded as JVM arguments:
// -Pjmh.dateHeader=true, -Pjmh.accessLog=true
val jmhBenchSwitches = listOf(
    "jmh.dateHeader" to "micronaut.bench.date-header",
    "jmh.accessLog" to "micronaut.bench.access-log",
    // -Pjmh.pythonSampler=/path/histogram.txt profiles the Python side of the compilation pipeline
    "jmh.pythonSampler" to "micronaut.python.cpusampler"
).mapNotNull { (property, systemProperty) ->
    providers.gradleProperty(property).orNull?.let { "-D$systemProperty=$it" }
} + (if (jmhIncludes.any { it.contains("PythonPipelineBenchmark") }) listOf(
    // the stage timings PythonPipelineBenchmark reports; a JMH -jvmArgsAppend replaces the @Fork ones,
    // and no other benchmark pays for the instrumentation
    "-Dmicronaut.python.timings=true"
) else emptyList())

jmh {
    includes = jmhIncludes
    fork = jmhFork
    iterations = jmhIterations
    warmupIterations = jmhWarmupIterations
    profilers = jmhProfilers
    providers.gradleProperty("jmh.warmupTime").orNull?.let(warmup::set)
    providers.gradleProperty("jmh.timeOnIteration").orNull?.let(timeOnIteration::set)
    jmhPoolSizes.orNull?.let { sizes ->
        benchmarkParameters.put("poolSize", objects.listProperty(String::class.java).value(sizes))
    }
    jmhBenchmarkParameters.orNull?.forEach { (name, values) ->
        benchmarkParameters.put(name, objects.listProperty(String::class.java).value(values))
    }
    humanOutputFile.set(jmhHumanOutput)
    jvmArgsAppend.addAll(jmhBenchSwitches)
    duplicateClassesStrategy = DuplicatesStrategy.WARN
}

tasks {
    processJmhResources {
        duplicatesStrategy = DuplicatesStrategy.WARN
    }

    named<Jar>("jmhJar") {
        isZip64 = true
        manifest.attributes["Multi-Release"] = "true"
        // the GraalPy virtual filesystems stay in the module jars on the classpath: a copy in the
        // benchmark jar is a second instance GraalPy refuses to choose between
        exclude("GRAALPY-VFS/**")
    }
}

listOf("spotlessJavaCheck", "checkstyleMain", "checkstyleJmh").forEach {
    tasks.named(it) {
        enabled = false
    }
}
