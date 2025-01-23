plugins {
    id("io.micronaut.build.internal.convention-test-library")
    id("io.micronaut.build.internal.functional-test")
    id("java-test-fixtures")
    alias(libs.plugins.webdriver)
    id("io.micronaut.build.internal.convention-geb-base")
}

micronautBuild {
    core {
        usesMicronautTestJunit()
        usesMicronautTestSpock()
    }
}

dependencies {
    testImplementation(projects.micronautHttp)
    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(projects.micronautJacksonDatabind)

    testRuntimeOnly(libs.logback.classic)
}
