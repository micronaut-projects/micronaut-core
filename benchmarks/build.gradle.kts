import java.util.zip.ZipEntry
import java.util.zip.ZipFile

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
    // FormDemuxerBenchmark decodes forms like the server does
    api(libs.managed.netty.contrib.multipart.core)

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
val jmhHumanOutput = providers.gradleProperty("jmh.humanOutput")
    .map(layout.projectDirectory::file)
// Benchmark switches read by BenchOptions in the forked JMH JVM. A -D on the Gradle command line
// only reaches Gradle itself, so they are exposed as -P properties and forwarded as JVM arguments:
// -Pjmh.dateHeader=true, -Pjmh.accessLog=true
val jmhBenchSwitches = listOf(
    "jmh.dateHeader" to "micronaut.bench.date-header",
    "jmh.accessLog" to "micronaut.bench.access-log"
).mapNotNull { (property, systemProperty) ->
    providers.gradleProperty(property).orNull?.let { "-D$systemProperty=$it" }
}

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
    humanOutputFile.set(jmhHumanOutput)
    jvmArgsAppend.addAll(jmhBenchSwitches)
    duplicateClassesStrategy = DuplicatesStrategy.WARN
}

// The fat jar keeps every copy of a duplicated entry, so a service loader would only see the first
// META-INF/services file of each name. The service files are merged into one file per service.
val mergedServices = layout.buildDirectory.dir("jmh-merged-services")
val mergeJmhServices = tasks.register("mergeJmhServices") {
    val classpath = configurations.named("jmhRuntimeClasspath")
    val jmhOutput = sourceSets.named("jmh").map { it.output }
    val mainOutput = sourceSets.named("main").map { it.output }
    inputs.files(classpath, jmhOutput, mainOutput)
    outputs.dir(mergedServices)
    doLast {
        val services = sortedMapOf<String, LinkedHashSet<String>>()
        fun add(name: String, text: String) {
            val lines = services.getOrPut(name) { LinkedHashSet() }
            text.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }.forEach(lines::add)
        }
        (jmhOutput.get().files + mainOutput.get().files + classpath.get().files).forEach { root ->
            if (root.isDirectory) {
                root.resolve("META-INF/services").listFiles()?.filter { it.isFile }?.forEach { add(it.name, it.readText()) }
            } else if (root.isFile && root.name.endsWith(".jar")) {
                val zip = ZipFile(root)
                try {
                    for (entry: ZipEntry in zip.entries().toList()) {
                        val name: String = entry.name
                        if (!entry.isDirectory && name.startsWith("META-INF/services/") && name.indexOf('/', "META-INF/services/".length) < 0) {
                            add(name.substringAfterLast('/'), zip.getInputStream(entry).bufferedReader().readText())
                        }
                    }
                } finally {
                    zip.close()
                }
            }
        }
        val dir = mergedServices.get().asFile.resolve("META-INF/services")
        dir.deleteRecursively()
        dir.mkdirs()
        services.forEach { (name, lines) -> dir.resolve(name).writeText(lines.joinToString("\n", postfix = "\n")) }
    }
}

tasks {
    processJmhResources {
        duplicatesStrategy = DuplicatesStrategy.WARN
    }

    named<Jar>("jmhJar") {
        isZip64 = true
        manifest.attributes["Multi-Release"] = "true"
        dependsOn(mergeJmhServices)
        val mergedDir = mergedServices.get().asFile
        // the merged files replace every copy of the service files of the classpath
        eachFile {
            if (path.startsWith("META-INF/services/") && !file.toPath().startsWith(mergedDir.toPath())) {
                exclude()
            }
        }
        from(mergedServices)
    }
}

listOf("spotlessJavaCheck", "checkstyleMain", "checkstyleJmh").forEach {
    tasks.named(it) {
        enabled = false
    }
}
