plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

micronautBuild {
    core {
        usesMicronautTestJunit()
        usesMicronautTestSpock()
    }
}

dependencies {
    testImplementation(libs.managed.netty.codec.http)
    testImplementation(projects.micronautHttpClient)
    testImplementation(projects.micronautHttpClientJdk)
    testImplementation(projects.micronautInjectGroovy)
    testImplementation(projects.micronautInjectGroovyTest)
    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(projects.micronautRuntime)
    testImplementation(platform(libs.test.boms.micronaut.validation))
    testImplementation(libs.micronaut.validation) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.micronaut.validation.processor) {
        exclude(group = "io.micronaut")
    }
    testImplementation(projects.micronautInject)
    testImplementation(projects.micronautManagement)
    testImplementation(libs.micronaut.session) {
        exclude(group = "io.micronaut")
    }
    testImplementation(libs.jcache)
    testImplementation(libs.managed.groovy.sql)
    testImplementation(libs.managed.groovy.templates)
    testImplementation(libs.managed.groovy.json)
    testImplementation(libs.logbook.netty)
    testImplementation(projects.micronautFunctionClient)
    testImplementation(projects.micronautFunctionWeb)
    testRuntimeOnly(platform(libs.test.boms.micronaut.aws))
    testRuntimeOnly(libs.aws.java.sdk.lambda)
    testRuntimeOnly(libs.bcpkix)

    testImplementation(libs.managed.reactor)
    testImplementation(libs.awaitility)
    testImplementation(libs.javax.persistence)
    testImplementation(libs.jakarta.persistence)
}

//compileTestGroovy.groovyOptions.forkOptions.jvmArgs = ['-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005']
