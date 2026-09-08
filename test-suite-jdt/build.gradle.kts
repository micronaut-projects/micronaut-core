plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

micronautBuild {
    core {
        usesMicronautTestSpock()
    }
}

configurations {
    // The Eclipse JDT batch compiler itself.
    create("ecj")
    // Compile classpath and annotation processor path used when compiling `src/jdt/java` with ECJ.
    create("jdtCompileClasspath")
    create("jdtAnnotationProcessor")
}

dependencies {
    // The Eclipse JDT compiler, used as a `javax.tools.JavaCompiler` so that the Micronaut
    // annotation processors run against JDT's `javax.lang.model` implementation instead of javac's.
    api(libs.ecj)

    api(projects.micronautInjectJava)
    api(projects.micronautInjectJavaTest)
    api(projects.micronautCoreProcessor)
    api(libs.managed.groovy)
    api(libs.managed.groovy.json)
    api(libs.spock) {
        exclude(module = "groovy-all")
    }

    testImplementation(projects.micronautContext)
    testImplementation(projects.micronautInject)
    testImplementation(projects.micronautRuntime)
    testImplementation(projects.micronautInjectTestUtils)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(libs.jakarta.persistence)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)

    add("ecj", libs.ecj)

    add("jdtCompileClasspath", projects.micronautContext)
    add("jdtCompileClasspath", projects.micronautInject)
    add("jdtCompileClasspath", projects.micronautRuntime)

    add("jdtAnnotationProcessor", projects.micronautInjectJava)
}

/**
 * Compiles `src/jdt/java` with the Eclipse JDT batch compiler, running the Micronaut annotation
 * processors, so that the tests below run against bean definitions that were produced by JDT
 * rather than javac.
 */
val jdtClassesDir = layout.buildDirectory.dir("classes/jdt")
val jdtGeneratedSourcesDir = layout.buildDirectory.dir("generated/sources/jdt")

val compileJdt = tasks.register<JavaExec>("compileJdt") {
    description = "Compiles src/jdt/java with the Eclipse JDT compiler and the Micronaut annotation processors"
    group = "build"

    val sourceDir = layout.projectDirectory.dir("src/jdt/java")
    val compileClasspath = configurations["jdtCompileClasspath"]
    val processorPath = configurations["jdtAnnotationProcessor"]

    inputs.dir(sourceDir).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(compileClasspath, processorPath)
    outputs.dir(jdtClassesDir)
    outputs.dir(jdtGeneratedSourcesDir)

    classpath = configurations["ecj"]
    mainClass.set("org.eclipse.jdt.internal.compiler.batch.Main")

    doFirst {
        val out = jdtClassesDir.get().asFile
        val generated = jdtGeneratedSourcesDir.get().asFile
        out.deleteRecursively()
        generated.deleteRecursively()
        out.mkdirs()
        generated.mkdirs()
        args = listOf(
            "-" + java.toolchain.languageVersion.map { it.asInt() }.getOrElse(JavaVersion.current().majorVersion.toInt()),
            "-nowarn",
            "-proc:full",
            "-encoding", "UTF-8",
            "-classpath", compileClasspath.asPath,
            "-processorpath", processorPath.asPath,
            "-d", out.absolutePath,
            "-s", generated.absolutePath,
            sourceDir.asFile.absolutePath
        )
    }
}

tasks.named<Test>("test") {
    dependsOn(compileJdt)
}

// The classes produced by ECJ are on the test runtime classpath only: the tests must see the
// bean definitions JDT generated, not ones javac would produce from the same sources.
dependencies {
    testRuntimeOnly(files(jdtClassesDir))
}

tasks.named("compileTestGroovy") {
    dependsOn(compileJdt)
}

tasks.named<GroovyCompile>("compileTestGroovy") {
    classpath += files(jdtClassesDir)
}

tasks.named<JavaCompile>("compileTestJava") {
    dependsOn(compileJdt)
    classpath += files(jdtClassesDir)
}
