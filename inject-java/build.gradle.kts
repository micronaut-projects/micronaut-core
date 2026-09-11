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
    forkEvery = 100
    maxParallelForks = 4
    useJUnitPlatform()
}

//compileTestGroovy.groovyOptions.forkOptions.jvmArgs = ['-Xdebug', '-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005']
//compileTestGroovy.groovyOptions.fork = true

micronautBuild {
    binaryCompatibility.enabledAfter("5.0.0")
}

noReflection {
    allowIn("io.micronaut.annotation.processing.AnnotationUtils", "SERVICE_LOADING")
    allowIn("io.micronaut.annotation.processing.BeanDefinitionInjectProcessor", "CLASS_NAMES")
    allowIn("io.micronaut.annotation.processing.JavaAnnotationMetadataBuilder", "CLASS_LOADING", "ENUM_CONSTANTS", "REFLECTIVE_ACCESS")
    allowIn("io.micronaut.annotation.processing.LoadedVisitor", "GENERIC_SIGNATURES")
    allowIn("io.micronaut.annotation.processing.PackageElementVisitorProcessor", "SERVICE_LOADING")
    allowIn("io.micronaut.annotation.processing.PackageLoadedVisitor", "GENERIC_SIGNATURES")
    allowIn("io.micronaut.annotation.processing.TypeElementVisitorProcessor", "ANNOTATIONS", "CLASS_NAMES", "SERVICE_LOADING")
    allowIn("io.micronaut.annotation.processing.visitor.AbstractJavaElement", "ENUM_CONSTANTS")
    allowIn("io.micronaut.annotation.processing.visitor.JavaClassElement", "ENUM_CONSTANTS")
    allowIn("io.micronaut.annotation.processing.visitor.JavaVisitorContext", "REFLECTION_UTILS")
}
