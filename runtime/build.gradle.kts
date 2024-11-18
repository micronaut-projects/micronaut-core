plugins {
    id("io.micronaut.build.internal.convention-core-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    annotationProcessor(projects.micronautGraal)
    api(projects.micronautAop)
    api(projects.micronautDiscoveryCore)
    api(projects.micronautContext)
    api(projects.micronautContextPropagation)
    api(projects.micronautCoreReactive)
    api(projects.micronautHttp)
    api(projects.micronautInject)
    api(projects.micronautRetry)

    implementation(libs.managed.reactor)

    compileOnly(libs.graal)
    compileOnly(libs.jcache)

    compileOnly(libs.jakarta.el)
    compileOnly(libs.caffeine)
    compileOnly(libs.managed.kotlinx.coroutines.core)
    compileOnly(libs.managed.kotlinx.coroutines.reactive)
    testImplementation(libs.logback.classic)
    testImplementation(libs.managed.snakeyaml)
    testAnnotationProcessor(projects.micronautInjectJava)
    testImplementation(libs.jsr107)
    testImplementation(libs.jcache)
    testImplementation(projects.micronautInjectJava)
    testImplementation(projects.micronautInjectJavaTest)
    testImplementation(projects.micronautInjectGroovy)
    testImplementation(libs.systemlambda)
}

//compileJava.options.fork = true
//compileJava.options.forkOptions.jvmArgs = ['-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005']

//compileTestGroovy.groovyOptions.forkOptions.jvmArgs = ['-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005']

spotless {
    java {
        targetExclude("**/io/micronaut/scheduling/cron/CronExpression.java")
    }
}

tasks.withType<Test>().configureEach {
    if (JavaVersion.current().majorVersion.toInt() >= 17) {
        logger.warn("Opening java.util and java.lang, so SystemLambda can work")
        jvmArgs(
            listOf(
                "--add-opens", "java.base/java.util=ALL-UNNAMED",
                "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            )
        )
    }
}
