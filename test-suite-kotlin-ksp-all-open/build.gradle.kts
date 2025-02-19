plugins {
    id("io.micronaut.build.internal.convention-test-library")
    alias(libs.plugins.managed.kotlin.jvm)
    alias(libs.plugins.managed.ksp)
    alias(libs.plugins.managed.kotlin.allopen)
}

// tag::allopen[]
allOpen {
    annotations("io.micronaut.docs.aop.around.OpenSingleton", "io.micronaut.docs.aop.around.AnotherOpenSingleton")
}
// end::allopen[]

micronautBuild {
    core {
        usesMicronautTestJunit()
        usesMicronautTestSpock()
        usesMicronautTestKotest()
    }
}

dependencies {
    api(libs.managed.kotlin.stdlib)
    api(libs.managed.kotlin.reflect)
    api(libs.managed.kotlinx.coroutines.core)
    api(libs.managed.kotlinx.coroutines.jdk8)
    api(libs.managed.kotlinx.coroutines.rx2)
    api(projects.micronautHttpServerNetty)
    api(projects.micronautHttpClient)
    api(projects.micronautRuntime)

    testImplementation(projects.micronautContext)
    testImplementation(libs.managed.kotlin.test)
    testImplementation(libs.managed.kotlinx.coroutines.core)
    testImplementation(libs.managed.kotlinx.coroutines.rx2)
    testImplementation(libs.managed.kotlinx.coroutines.slf4j)
    testImplementation(libs.managed.kotlinx.coroutines.reactor)

    // Adding these for now since micronaut-test isnt resolving correctly ... probably need to upgrade gradle there too
    testImplementation(libs.junit.jupiter.api)

    kspTest(projects.micronautInjectKotlin)
}

configurations.testRuntimeClasspath {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion(libs.versions.managed.kotlin.asProvider().get())
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}

// tag::ksp[]
ksp {
    arg("kotlin.allopen.annotations", "io.micronaut.docs.aop.around.OpenSingleton|io.micronaut.docs.aop.around.AnotherOpenSingleton")
}
// end::ksp[]
