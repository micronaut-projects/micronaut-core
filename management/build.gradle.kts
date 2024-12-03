plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    testAnnotationProcessor(projects.micronautInjectJava)
    annotationProcessor(projects.micronautGraal)

    api(projects.micronautRouter)
    api(projects.micronautDiscoveryCore)
    compileOnly(projects.micronautJacksonDatabind)
    compileOnly(libs.micronaut.sql.jdbc) {
        exclude(group = "io.micronaut")
    }

    // Support validation annotations
    compileOnly(platform(libs.test.boms.micronaut.validation))
    compileOnly(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }
    compileOnly(projects.micronautHttpClientCore)
    implementation(libs.managed.reactor)

    testImplementation(projects.micronautHttpClient)
    testImplementation(projects.micronautInjectGroovy)
    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(libs.micronaut.sql.jdbc.tomcat) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.managed.groovy.json)
    testImplementation(libs.logback.classic)

    testRuntimeOnly(platform(libs.test.boms.micronaut.sql))
    testRuntimeOnly(libs.h2)
    testRuntimeOnly(libs.mysql.driver)

    compileOnly(libs.logback.classic)
    compileOnly(libs.log4j)
    testImplementation(libs.awaitility)
}
