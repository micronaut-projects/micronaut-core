plugins {
    id("java")
}

// The HTTP server TCK with the routes of its controllers compiled: the controllers are compiled in
// the TCK library without the route compiler, so this suite links them into one route plan.
val linkedPackages = listOf(
    "io.micronaut.http.server.tck.tests",
    "io.micronaut.http.server.tck.tests.binding",
    "io.micronaut.http.server.tck.tests.bodywritable",
    "io.micronaut.http.server.tck.tests.codec",
    "io.micronaut.http.server.tck.tests.constraintshandler",
    "io.micronaut.http.server.tck.tests.cors",
    "io.micronaut.http.server.tck.tests.endpoints",
    "io.micronaut.http.server.tck.tests.endpoints.health",
    "io.micronaut.http.server.tck.tests.exceptions",
    "io.micronaut.http.server.tck.tests.filter",
    "io.micronaut.http.server.tck.tests.filter.options",
    "io.micronaut.http.server.tck.tests.forms",
    "io.micronaut.http.server.tck.tests.hateoas",
    "io.micronaut.http.server.tck.tests.jsonview",
    "io.micronaut.http.server.tck.tests.mediatype",
    "io.micronaut.http.server.tck.tests.raw",
    "io.micronaut.http.server.tck.tests.routing",
    "io.micronaut.http.server.tck.tests.staticresources",
    "io.micronaut.http.server.tck.tests.textplain"
)

dependencies {
    testAnnotationProcessor(projects.micronautInjectJava)
    testAnnotationProcessor(projects.micronautRouterProcessor)
    testImplementation(projects.micronautHttpServerTck)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautManagement)
    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(projects.micronautHttpClient)
    testImplementation(libs.junit.platform.engine)
    testImplementation(libs.logback.classic)
    testImplementation(platform(libs.test.boms.micronaut.validation))
    testImplementation(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks {
    compileTestJava {
        options.compilerArgs.addAll(listOf(
            "-Amicronaut.router.link.packages=" + linkedPackages.joinToString(","),
            "-Amicronaut.router.link.class=io.micronaut.http.server.tck.netty.compiled.\$LinkedRoutePlan"
        ))
    }
    test {
        useJUnitPlatform()
        systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
    }
}
