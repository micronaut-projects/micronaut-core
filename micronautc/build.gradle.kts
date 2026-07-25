import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskAction

plugins {
    id("application")
    id("org.graalvm.buildtools.native")
    id("io.micronaut.build.internal.convention-library")
}

val generatePgoProfileTaskNames = setOf(
    "generateMicronautcPgoProfile",
    "copyMicronautcPgoProfile",
    "runMicronautcPgoTraining"
)
val pgoProfileGenerationRequested = gradle.startParameter.taskNames.any { taskName ->
    generatePgoProfileTaskNames.any(taskName::endsWith)
}
val pgoInstrumentationRequested = providers
    .gradleProperty("micronautc.pgo.instrument")
    .map(String::toBoolean)
    .orElse(false)
    .get()
val pgoInstrumentationEnabled = pgoProfileGenerationRequested || pgoInstrumentationRequested
val pgoProfilesDirectory = layout.projectDirectory.dir("src/pgo-profiles/main")
val pgoProfilesPresent = pgoProfilesDirectory.asFile
    .takeIf { it.isDirectory }
    ?.listFiles { file -> file.isFile && file.extension == "iprof" }
    ?.isNotEmpty()
    ?: false

dependencies {
    implementation(projects.micronautInjectJava)

    testImplementation(projects.micronautContext)
    testImplementation(libs.jakarta.inject.api)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("io.micronaut.micronautc.Micronautc")
}

graalvmNative {
    toolchainDetection = false
    metadataRepository {
        enabled = true
    }
    binaries {
        named("main") {
            imageName.set("micronautc")
            mainClass.set("io.micronaut.micronautc.Micronautc")
            sharedLibrary.set(false)
            pgoInstrument.set(pgoInstrumentationEnabled)
            if (!pgoInstrumentationEnabled && !pgoProfilesPresent) {
                buildArgs.add("-Ob")
            }
            buildArgs.add("--add-modules=java.compiler")
            buildArgs.add("-H:ConfigurationFileDirectories=${project.layout.projectDirectory.dir("src/main/resources/META-INF/native-image/io.micronaut/micronautc").asFile.absolutePath}")
            buildArgs.add("-H:EnableURLProtocols=jar")
            buildArgs.add("-H:+UnlockExperimentalVMOptions")
            buildArgs.add("-H:+RuntimeClassLoading")
            buildArgs.add("-H:+AllowJRTFileSystem")
            buildArgs.add("-H:Preserve=package=javax.annotation.processing")
            buildArgs.add("-H:Preserve=package=javax.lang.model")
            buildArgs.add("-H:Preserve=package=javax.tools")
            buildArgs.add("-H:Preserve=package=java.lang")
            buildArgs.add("-H:Preserve=package=java.lang.invoke")
            buildArgs.add("-H:Preserve=package=jdk.internal.misc")
            buildArgs.add("-H:Preserve=package=jdk.internal.access")
            buildArgs.add("-H:Preserve=package=io.micronaut.inject")
            buildArgs.add("-H:Preserve=package=io.micronaut.inject.visitor")
            buildArgs.add("-H:Preserve=package=io.micronaut.inject.ast")
            buildArgs.add("-H:Preserve=package=io.micronaut.context")
            buildArgs.add("-H:-UnlockExperimentalVMOptions")
            buildArgs.add("--initialize-at-build-time=com.sun.tools.javac.api.JavacTool")
            buildArgs.add("--initialize-at-build-time=io.micronaut.sourcegen.model,org.objectweb.asm")
            buildArgs.add("--initialize-at-run-time=jdk.internal.loader.ClassLoaders")
            buildArgs.add("--initialize-at-run-time=io.micronaut.annotation.processing.TypeElementVisitorProcessor")
            buildArgs.add("--initialize-at-run-time=io.micronaut.annotation.processing.AggregatingTypeElementVisitorProcessor")
            buildArgs.add("--initialize-at-run-time=io.micronaut.annotation.processing.PackageElementVisitorProcessor")
            buildArgs.add("--initialize-at-run-time=io.micronaut.inject.visitor.TypeElementVisitor")
            buildArgs.add("--initialize-at-run-time=io.micronaut.inject.visitor.TypeElementVisitor\$VisitorKind")
            buildArgs.add("--initialize-at-run-time=com.sun.tools.javac")
            buildArgs.add("--initialize-at-run-time=com.sun.tools.javac.file")
            buildArgs.add("--initialize-at-run-time=com.sun.source")
            buildArgs.add("--initialize-at-run-time=jdk.javadoc.internal")
            buildArgs.add("--initialize-at-run-time=com.sun.tools.doclint")
            buildArgs.add("--initialize-at-run-time=jdk.internal.jshell.tool")
            buildArgs.add("--initialize-at-run-time=jdk.internal.org.jline.terminal.impl.ffm")
            buildArgs.add("--initialize-at-run-time=io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder")
            resources.autodetect()
        }
    }
}

abstract class CopyMicronautcPgoProfileTask : DefaultTask() {

    @TaskAction
    fun copyProfile() {
        val profileRunOutput = project.layout.buildDirectory.file("pgo/profile-run/default.iprof").get().asFile
        val nativeOutput = project.layout.buildDirectory.file("native/nativeCompile/default.iprof").get().asFile
        val sourceProfile = when {
            profileRunOutput.isFile -> profileRunOutput
            nativeOutput.isFile -> nativeOutput
            else -> throw IllegalStateException(
                "No PGO profile found. Expected default.iprof at ${profileRunOutput.absolutePath} or ${nativeOutput.absolutePath}."
            )
        }

        val destinationDirectory = project.layout.projectDirectory.dir("src/pgo-profiles/main").asFile
        if (!destinationDirectory.exists()) {
            destinationDirectory.mkdirs()
        }
        sourceProfile.copyTo(destinationDirectory.resolve("default.iprof"), overwrite = true)
    }
}

val pgoTrainingSourcesDirectory = layout.buildDirectory.dir("pgo/training/src")
val pgoTrainingOutputDirectory = layout.buildDirectory.dir("pgo/training/classes")
val pgoProfileRunDirectory = layout.buildDirectory.dir("pgo/profile-run")

val prepareMicronautcPgoTrainingSources = tasks.register("prepareMicronautcPgoTrainingSources") {
    outputs.dir(pgoTrainingSourcesDirectory)
    doLast {
        val sourceDirectory = pgoTrainingSourcesDirectory.get().asFile.resolve("example")
        sourceDirectory.mkdirs()

        sourceDirectory.resolve("Greeter.java").writeText(
            """
            package example;

            import jakarta.inject.Singleton;

            @Singleton
            public class Greeter {
                public String greet() {
                    return "hello";
                }
            }
            """.trimIndent()
        )

        sourceDirectory.resolve("HelloService.java").writeText(
            """
            package example;

            import jakarta.inject.Singleton;

            @Singleton
            public class HelloService {
                private final Greeter greeter;

                public HelloService(Greeter greeter) {
                    this.greeter = greeter;
                }

                public String message() {
                    return greeter.greet();
                }
            }
            """.trimIndent()
        )

        sourceDirectory.resolve("IntrospectedBean.java").writeText(
            """
            package example;

            import io.micronaut.core.annotation.Introspected;

            @Introspected
            public class IntrospectedBean {
                private String name;

                public String getName() {
                    return name;
                }

                public void setName(String name) {
                    this.name = name;
                }
            }
            """.trimIndent()
        )
    }
}

val runMicronautcPgoTraining = tasks.register<Exec>("runMicronautcPgoTraining") {
    dependsOn(tasks.named("nativeCompile"), prepareMicronautcPgoTrainingSources)
    inputs.files(
        pgoTrainingSourcesDirectory.map { dir ->
            listOf(
                dir.file("example/Greeter.java"),
                dir.file("example/HelloService.java"),
                dir.file("example/IntrospectedBean.java")
            )
        }
    )
    outputs.file(pgoProfileRunDirectory.map { it.file("default.iprof") })

    doFirst {
        val instrumentedExecutable = layout.buildDirectory.file("native/nativeCompile/micronautc-instrumented").get().asFile
        if (!instrumentedExecutable.isFile) {
            throw IllegalStateException(
                "Instrumented micronautc executable not found at ${instrumentedExecutable.absolutePath}. " +
                    "Run this task directly or invoke Gradle with -Pmicronautc.pgo.instrument=true."
            )
        }

        val sourceRoot = pgoTrainingSourcesDirectory.get().asFile.resolve("example")
        val outputDirectory = pgoTrainingOutputDirectory.get().asFile
        val profileDirectory = pgoProfileRunDirectory.get().asFile

        outputDirectory.mkdirs()
        profileDirectory.mkdirs()

        workingDir = profileDirectory
        executable = instrumentedExecutable.absolutePath
        args(
            "-d", outputDirectory.absolutePath,
            "-classpath", configurations.testRuntimeClasspath.get().asPath,
            sourceRoot.resolve("Greeter.java").absolutePath,
            sourceRoot.resolve("HelloService.java").absolutePath,
            sourceRoot.resolve("IntrospectedBean.java").absolutePath
        )
        environment("MICRONAUTC_JAVA_HOME", System.getProperty("java.home"))
    }
}

val copyMicronautcPgoProfile = tasks.register<CopyMicronautcPgoProfileTask>("copyMicronautcPgoProfile") {
    dependsOn(runMicronautcPgoTraining)
}

tasks.register("generateMicronautcPgoProfile") {
    group = "build"
    description = "Generates and stores a micronautc native-image PGO profile in src/pgo-profiles/main/default.iprof"
    dependsOn(copyMicronautcPgoProfile)
}

tasks.named<Test>("test") {
    dependsOn(tasks.named("nativeCompile"))
    systemProperty("micronautc.executable", layout.buildDirectory.file("native/nativeCompile/micronautc").get().asFile.absolutePath)
    systemProperty("micronautc.compile.classpath", configurations.testRuntimeClasspath.get().asPath)
}
