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
    testImplementation(libs.managed.graalvm.nativeimage)
    testImplementation(libs.managed.snakeyaml)
    testImplementation(libs.managed.jspecify)
    testImplementation(libs.bytebuddy)
    testRuntimeOnly(libs.jakarta.el.impl)
    testRuntimeOnly(libs.jakarta.el)
}
//compileTestJava.options.fork = true
//compileTestJava.options.forkOptions.jvmArgs = ['-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005']


tasks.withType<Test>().configureEach {
    maxParallelForks = 4
    // The annotation processing tests run short compilations: C2 costs more CPU than it saves
    jvmArgs("-XX:TieredStopAtLevel=1")
    useJUnitPlatform()
}

tasks.named<GroovyCompile>("compileTestGroovy") {
    // The largest Groovy compilation of the build, up to 1.2 GB live on its own. A compiler daemon
    // that compiled other Groovy sources first still holds part of their memory, and the 2 GB of the
    // convention ran out compiling these sources in such a daemon. A larger heap also gives this
    // compilation a daemon of its own: a daemon with less memory cannot be reused for it.
    groovyOptions.forkOptions.memoryMaximumSize = "3g"
}
//compileTestGroovy.groovyOptions.forkOptions.jvmArgs = ['-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005']
//compileTestGroovy.groovyOptions.fork = true

micronautBuild {
    binaryCompatibility.enabledAfter("5.0.0")
}
