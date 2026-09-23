plugins {
    id("io.micronaut.build.internal.convention-core-library")
}
dependencies {
    annotationProcessor(projects.micronautInjectJava)

    api(projects.micronautCoreProcessor)

    testAnnotationProcessor(projects.micronautInjectJava)

    testImplementation(projects.micronautInject)
    testImplementation(projects.micronautHttp)
    testImplementation(projects.micronautInjectJavaTest)
    testImplementation(projects.micronautInjectKotlin)
    testImplementation(projects.micronautInjectKotlinTest)
    testImplementation(libs.managed.groovy.json)
    testImplementation(libs.javax.persistence)
    testImplementation(libs.managed.kotlin.compiler.embeddable)
    testImplementation(libs.managed.kotlin.stdlib)
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion(libs.versions.managed.kotlin.asProvider().get())
        } else if (requested.group == "com.google.devtools.ksp") {
            useVersion(libs.versions.managed.ksp.get())
        }
    }
}
