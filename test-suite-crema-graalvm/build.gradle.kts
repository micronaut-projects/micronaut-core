plugins {
    id("java")
    id("org.graalvm.buildtools.native")
}

description = "Test suite for service loading in a native image with runtime class loading (Crema)"

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

val cremaRuntimePathProperty = "micronaut.test.crema.runtime.path"

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    dependsOn(cremaRuntime.output)
    systemProperty(cremaRuntimePathProperty, cremaRuntime.output.asPath)
}

dependencies {
    testImplementation(projects.micronautCore)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

graalvmNative {
    toolchainDetection = false
    metadataRepository {
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
            runtimeArgs.add("-D$cremaRuntimePathProperty=${cremaRuntime.output.asPath}")
        }
    }
}

tasks.named("nativeTest") {
    dependsOn(cremaRuntime.output)
}
