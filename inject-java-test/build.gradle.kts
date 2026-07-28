plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)

    api(projects.micronautInjectJava)
    api(projects.micronautContext)
    api(libs.managed.groovy)
    api(libs.spock) {
        exclude(module = "groovy-all")
    }
    api(libs.jetbrains.annotations)

    testAnnotationProcessor(projects.micronautInjectJava)
    testCompileOnly(projects.micronautInjectGroovy)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(libs.javax.persistence)
    testImplementation(projects.micronautRuntime)
    testImplementation(libs.javax.inject)
    testImplementation(libs.blaze.persistence.core)
    testImplementation(libs.smallrye)

    testImplementation(platform(libs.test.boms.micronaut.validation))
    testImplementation(libs.micronaut.validation.processor) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }
}

tasks {
    sourcesJar {
        from("$projectDir/src/main/groovy")
    }
}

//compileTestGroovy.groovyOptions.forkOptions.jvmArgs = ['-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005']
//compileJava.options.fork = true
//compileJava.options.forkOptions.jvmArgs = ['-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005']
