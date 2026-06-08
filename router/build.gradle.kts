plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)

    api(projects.micronautInject)
    api(projects.micronautHttp)
    compileOnly(libs.managed.groovy)

    testImplementation(projects.micronautContext)
    testImplementation(projects.micronautInjectGroovy)
    testImplementation(projects.micronautInjectJava)
    testAnnotationProcessor(projects.micronautInjectJava)
    testImplementation(projects.micronautInjectGroovyTest)
    testImplementation(libs.jazzer.junit)
    testImplementation(libs.jazzer.api)
    testImplementation(libs.icu4j)
}

tasks.withType<Test>().configureEach {
    if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_25)) {
        // Jazzer 0.24.0 cannot instrument JDK 25 class files (major version 69).
        exclude("**/UriUtilTest.class")
    }
}

//compileTestGroovy.groovyOptions.forkOptions.jvmArgs = ['-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005']
//compileTestGroovy.groovyOptions.fork = true
