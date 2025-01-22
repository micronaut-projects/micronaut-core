plugins {
    id("io.micronaut.build.internal.convention-library")
    id("io.micronaut.build.internal.functional-test")
}

micronautBuild {
    core {
        usesMicronautTest()
    }
}

dependencies {
    api(projects.micronautCoreProcessor)
    // We need to replicate these dependencies from Micronaut Sourcegen Bytecode writer in the API scope
    // so they are included on the compileOnly scope
    api(mnSourcegen.asm)
    api(mnSourcegen.asm.util)
    api(mnSourcegen.asm.commons)

    api(libs.managed.groovy)
    testImplementation(projects.micronautContext)
    testImplementation(libs.javax.inject)
    testImplementation(libs.spotbugs)
    testImplementation(libs.hibernate)
    testRuntimeOnly(libs.jakarta.el.impl)
    testRuntimeOnly(libs.jakarta.el)
    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(projects.micronautHttpClient)
    testImplementation(projects.micronautRetry)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautInjectTestUtils)
    testImplementation(projects.micronautInjectGroovyTest)
    testImplementation(platform(libs.test.boms.micronaut.validation))
    testImplementation(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.micronaut.validation.processor) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.neo4j.bolt)
    testImplementation(libs.managed.groovy.json)
    testImplementation(libs.blaze.persistence.core)
    testImplementation(libs.managed.snakeyaml)
    testImplementation(libs.managed.reactor)

    functionalTestImplementation(testFixtures(projects.testSuite))
}

tasks {
    test {
        exclude("**/*\$_closure*")
        forkEvery = 100
        maxParallelForks = 4
        systemProperty("groovy.attach.groovydoc", true)
    }
}

//compileTestGroovy.groovyOptions.forkOptions.jvmArgs = ['-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005']
//compileGroovy.groovyOptions.forkOptions.jvmArgs = ['-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005']
