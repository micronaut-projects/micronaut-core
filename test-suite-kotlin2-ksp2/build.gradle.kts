plugins {
    id("io.micronaut.build.internal.convention-test-library")
    alias(libs.plugins.managed.kotlin2.jvm)
    alias(libs.plugins.managed.ksp2)
}

micronautBuild {
    core {
        usesMicronautTestJunit()
        usesMicronautTestSpock()
        usesMicronautTestKotest()
    }
}

dependencies {
    api(libs.managed.kotlin2.stdlib)
    api(libs.managed.kotlin2.reflect)
    api(libs.managed.kotlinx.coroutines.core)
    api(libs.managed.kotlinx.coroutines.jdk8)
    api(libs.managed.kotlinx.coroutines.rx2)
    api(projects.micronautHttpServerNetty)
    api(projects.micronautHttpClient)
    api(projects.micronautRuntime)

    testImplementation(projects.micronautContext)
    testImplementation(libs.managed.kotlin2.test)
    testImplementation(libs.managed.kotlinx.coroutines.core)
    testImplementation(libs.managed.kotlinx.coroutines.rx2)
    testImplementation(libs.managed.kotlinx.coroutines.slf4j)
    testImplementation(libs.managed.kotlinx.coroutines.reactor)

    // Adding these for now since micronaut-test isnt resolving correctly ... probably need to upgrade gradle there too
    testImplementation(libs.junit.jupiter.api)

    testImplementation(platform(libs.test.boms.micronaut.validation))
    testImplementation(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }
    testImplementation(projects.micronautManagement)
    testImplementation(projects.micronautInjectJava)
    testImplementation(projects.micronautInject)
    testImplementation(libs.jcache)
    testImplementation(projects.micronautHttpClient)
    testImplementation(libs.micronaut.session) {
        exclude(group = "io.micronaut")
    }
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(libs.managed.groovy.templates)

    testImplementation(projects.micronautFunctionClient)
    testImplementation(projects.micronautFunctionWeb)
    testImplementation(libs.kotlin.kotest.junit5)
    testImplementation(libs.logbook.netty)
    kspTest(projects.micronautInjectKotlin)
    kspTest(platform(libs.test.boms.micronaut.validation))
    kspTest(libs.micronaut.validation.processor) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.javax.inject)

    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(platform(libs.test.boms.micronaut.aws))
    testRuntimeOnly(libs.aws.java.sdk.lambda)
    testImplementation(libs.bcpkix)

    testImplementation(libs.managed.reactor)

    testImplementation(libs.javax.persistence)
    testImplementation(libs.jakarta.persistence)
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion(libs.versions.managed.kotlin2.get())
        } else if (requested.group == "com.google.devtools.ksp") {
            useVersion(libs.versions.managed.ksp2.get())
        }
    }
}

//kotlin {
//    kotlinDaemonJvmArgs = ["-Xdebug","-Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=y"]
//}

tasks {
    test {
        useJUnitPlatform()
    }
}
