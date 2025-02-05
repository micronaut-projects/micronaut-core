plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    annotationProcessor(projects.micronautGraal)
    api(projects.micronautInject)
    api(projects.micronautAop)

    compileOnly(projects.micronautCoreReactive)
    compileOnly(libs.log4j)
    compileOnly(libs.logback.classic)

    // Support validation annotations
    compileOnly(platform(libs.test.boms.micronaut.validation))
    compileOnly(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }

    testCompileOnly(projects.micronautInjectGroovy)
    testAnnotationProcessor(projects.micronautInjectJava)
    testImplementation(projects.micronautCoreReactive)
    testImplementation(projects.micronautInjectJavaTest)
    testImplementation(libs.logback.classic)
    testImplementation(libs.micronaut.test.junit5) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.junit.jupiter.api)

}

spotless {
    java {
        targetExclude("**/io/micronaut/scheduling/cron/CronExpression.java")
    }
}
