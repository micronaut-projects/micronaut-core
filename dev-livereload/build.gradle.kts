plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    api(projects.micronautDev)
    api(libs.managed.netty.codec.http)

    // an application launched in development mode, compiled by the embedded javac, serves the pages the script goes into
    testImplementation(projects.micronautInjectJava)
    testImplementation(projects.micronautHttpServerNetty)
    testImplementation(projects.micronautJacksonDatabind)
    testImplementation(libs.logback.classic)
}

micronautBuild {
    binaryCompatibility {
        enabledAfter("5.3.0")
    }
}
