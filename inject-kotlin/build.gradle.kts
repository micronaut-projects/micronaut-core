plugins {
    id("io.micronaut.build.internal.convention-library")
    alias(libs.plugins.managed.kotlin.jvm)
    alias(libs.plugins.managed.ksp)
}

micronautBuild {
    core {
        usesMicronautTest()
        usesMicronautTestKotest()
    }
}

dependencies {
    api(projects.micronautCoreProcessor)

    // We use ASM API for some type conversions
    implementation(mnSourcegen.asm)

    implementation(libs.managed.ksp.api)

    kspTest(project)
    kspTest(platform(libs.test.boms.micronaut.validation))
    kspTest(libs.micronaut.validation.processor) {
        exclude(group = "io.micronaut")
    }

    testImplementation(projects.micronautContext)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautInjectKotlinTest)
    testImplementation(libs.managed.kotlin.stdlib)
    testImplementation(projects.micronautHttpClient)
    testImplementation(libs.managed.jackson.annotations)
    testImplementation(libs.managed.reactor)
    testImplementation(libs.hibernate)
    testImplementation(platform(libs.test.boms.micronaut.validation))
    testImplementation(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.micronaut.validation.processor) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.javax.persistence)
    testImplementation(projects.micronautRuntime)
    testImplementation(libs.neo4j.bolt)
    testImplementation(libs.managed.kotlinx.coroutines.core)
    testImplementation(libs.managed.kotlinx.coroutines.jdk8)
    testImplementation(libs.managed.kotlinx.coroutines.rx2)
    testImplementation(libs.micronaut.test.junit5) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.kotlin.kotest.junit5)
}


configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion(libs.versions.managed.kotlin.asProvider().get())
        } else if (requested.group == "com.google.devtools.ksp") {
            useVersion(libs.versions.managed.ksp.get())
        }
    }
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs = listOf("-Xjvm-default=all")
        }
    }
    compileTestGroovy {
        classpath += files(compileTestKotlin)
    }
    test {
        classpath += files(compileTestKotlin)
//    testLogging {
//        showStandardStreams = true
//    }
        maxHeapSize = "3G"
        forkEvery = 40
        maxParallelForks = 4
    }
}

kotlin {
    jvmToolchain(17)
//    kotlinDaemonJvmArgs = ["-Xdebug","-Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=y"]
}
