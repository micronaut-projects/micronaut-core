plugins {
    id("io.micronaut.build.internal.convention-library")
}

micronautBuild {
    core {
        usesMicronautTest()
    }
}

dependencies {
    api(projects.micronautCoreProcessor)

    testImplementation(projects.micronautContext)
    testImplementation(projects.micronautAop)
    testImplementation(projects.micronautInjectJavaHelper)
    testCompileOnly(projects.micronautInjectJavaHelper2)

    testAnnotationProcessor(projects.micronautInjectJava)
    testAnnotationProcessor(platform(libs.test.boms.micronaut.validation))
    testAnnotationProcessor (libs.micronaut.validation.processor) {
        exclude(group="io.micronaut")
    }

    testImplementation(projects.micronautInjectJavaTest)
    testImplementation(projects.micronautInjectTestUtils)
    testImplementation(projects.micronautRuntime)

    testImplementation(libs.managed.reactor)

    testImplementation(libs.spotbugs)
    testImplementation(libs.hibernate)
    testImplementation(libs.compile.testing)
    testImplementation(libs.neo4j.bolt)
    testImplementation(libs.managed.groovy.json)
    testImplementation (libs.micronaut.session) {
        exclude(group="io.micronaut")
    }
    testImplementation(projects.micronautHttpServer)
    testImplementation(platform(libs.test.boms.micronaut.validation))
    testImplementation (libs.micronaut.validation) {
        exclude(group="io.micronaut")
    }
    testImplementation (libs.micronaut.validation.processor) {
        exclude(group="io.micronaut")
    }
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.javax.annotation.api)
    testImplementation(libs.javax.inject)
    testImplementation(libs.graal)
    testImplementation(libs.managed.snakeyaml)
    testImplementation(libs.managed.jspecify)
    testRuntimeOnly(libs.jakarta.el.impl)
    testRuntimeOnly(libs.jakarta.el)
}
//compileTestJava.options.fork = true
//compileTestJava.options.forkOptions.jvmArgs = ['-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005']


tasks.withType<Test>().configureEach {
    forkEvery = 100
    maxParallelForks = 4
    useJUnitPlatform()
}

//compileTestGroovy.groovyOptions.forkOptions.jvmArgs = ['-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005']
//compileTestGroovy.groovyOptions.fork = true
