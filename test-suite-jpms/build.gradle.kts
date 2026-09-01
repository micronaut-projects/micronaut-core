plugins {
    id("java")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    implementation(projects.micronautInject)
}

val mainModuleName = "io.micronaut.testsuite.jpms"
val mainClassName = "io.micronaut.testsuite.jpms.JpmsApplication"
val modulePath = sourceSets.main.get().runtimeClasspath

val jpmsTest = tasks.register<JavaExec>("jpmsTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs a dependency injection smoke test on the module path"
    dependsOn(tasks.named("classes"))

    mainModule.set(mainModuleName)
    mainClass.set(mainClassName)
    classpath = modulePath
}

val jlinkImageDirectory = layout.buildDirectory.dir("jpms-image")
val jlinkExecutable = tasks.named<JavaCompile>("compileJava").flatMap { task ->
    task.javaCompiler.map { compiler ->
        val javac = compiler.executablePath.asFile
        val executableName = if (javac.name.endsWith(".exe")) "jlink.exe" else "jlink"
        javac.resolveSibling(executableName)
    }
}
val jlinkImage = tasks.register<Exec>("jlinkImage") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Creates a linked runtime image containing the JPMS smoke test application"
    dependsOn(tasks.named("classes"))

    inputs.files(modulePath)
        .withPropertyName("modulePath")
        .withNormalizer(ClasspathNormalizer::class.java)
    inputs.file(jlinkExecutable)
        .withPropertyName("jlinkExecutable")
        .withPathSensitivity(PathSensitivity.NONE)
    outputs.dir(jlinkImageDirectory)

    doFirst {
        delete(jlinkImageDirectory)
        commandLine(
            jlinkExecutable.get(),
            "--module-path", modulePath.asPath,
            "--add-modules", mainModuleName,
            "--bind-services",
            "--strip-debug",
            "--no-header-files",
            "--no-man-pages",
            "--output", jlinkImageDirectory.get().asFile
        )
    }
}

tasks.named("check") {
    dependsOn(jpmsTest, jlinkImage)
}
