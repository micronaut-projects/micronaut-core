plugins {
    id("io.micronaut.build.internal.convention-library")
}

micronautBuild {
    core {
        usesMicronautTest()
    }
    binaryCompatibility {
        enabledAfter("5.1.0")
    }
}

dependencies {
    annotationProcessor(projects.micronautInjectJava)
    annotationProcessor(projects.micronautGraal)

    api(projects.micronautContextPython)
    api(projects.micronautHttpNetty)
    implementation(projects.micronautHttpServerNetty)
    implementation(libs.managed.netty.resolver.dns)

    testImplementation(projects.micronautInject)
    testImplementation(projects.micronautInjectJavaTest)
    testImplementation(libs.managed.netty.pkitesting)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
