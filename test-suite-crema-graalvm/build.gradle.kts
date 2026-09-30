import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.graalvm.buildtools.gradle.dsl.GraalVMReachabilityMetadataRepositoryExtension

plugins {
    id("java")
}

description = "Test suite for service loading in a native image with runtime class loading (Crema)"

// Crema is experimental and what it supports changes between GraalVM releases, so the native tests of this module
// are opt-in. The native plugin is only applied, and nativeTest only exists, with -PcremaTests=true (or with the
// ORG_GRADLE_PROJECT_cremaTests=true environment variable). The GraalVM workflows run the nativeTest tasks they
// find in the build, so they do not run this module by default. See README.md.
val cremaTests = providers.gradleProperty("cremaTests").map { it.toBoolean() }.getOrElse(false)

// Classes that the tests load at run time from a class path that is not part of the image. In the
// native test image, Crema defines them at run time.
val cremaRuntime: SourceSet = sourceSets.create("cremaRuntime") {
    compileClasspath += sourceSets.test.get().output + sourceSets.test.get().compileClasspath
}

tasks.named<JavaCompile>(cremaRuntime.compileJavaTaskName) {
    // Crema in Oracle GraalVM 25.0.3 cannot define a class that has an InnerClasses attribute
    // ("enclosing class is not supported yet"). Invokedynamic string concatenation adds one for
    // MethodHandles.Lookup, so these sources also use no lambdas and no nested classes.
    options.compilerArgs.add("-XDstringConcat=inline")
}

/**
 * Passes the class path of the classes loaded at run time as a system property. The class path is an input of
 * the task, and it is only resolved when the task runs.
 */
class RuntimeClassPath(@get:Classpath val classpath: FileCollection) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> = listOf("-Dmicronaut.test.crema.runtime.path=${classpath.asPath}")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgumentProviders.add(RuntimeClassPath(cremaRuntime.output))
}

dependencies {
    testImplementation(projects.micronautCore)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

if (cremaTests) {
    apply(plugin = "org.graalvm.buildtools.native")

    val runtimeClassPath = cremaRuntime.output.elements.map { locations ->
        "-Dmicronaut.test.crema.runtime.path=" + locations.joinToString(File.pathSeparator) { it.asFile.absolutePath }
    }

    configure<GraalVMExtension> {
        toolchainDetection = false
        (this as ExtensionAware).configure<GraalVMReachabilityMetadataRepositoryExtension> {
            enabled = true
        }
        binaries {
            all {
                if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
                    buildArgs.add("--initialize-at-build-time=org.junit.platform.suite.engine.IsSuiteClass")
                    buildArgs.add("--initialize-at-build-time=org.junit.platform.suite.engine.IsPotentialTestContainer")
                }
                buildArgs.add("-H:+UnlockExperimentalVMOptions")
                buildArgs.add("-H:+RuntimeClassLoading")
                // keep the code that the classes loaded at run time call into
                buildArgs.add("-H:Preserve=package=io.micronaut.core.io.service")
                buildArgs.add("-H:Preserve=package=io.micronaut.core.util")
                buildArgs.add("-H:Preserve=package=example.crema")
                buildArgs.add("-H:-UnlockExperimentalVMOptions")
                resources.autodetect()
            }
            named("test") {
                runtimeArgs.add(runtimeClassPath)
            }
        }
    }

    tasks.named("nativeTest") {
        dependsOn(cremaRuntime.output)
    }
}
