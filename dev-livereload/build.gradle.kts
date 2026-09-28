plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    api(projects.micronautDev)
    api(libs.managed.netty.codec.http)
}

micronautBuild {
    binaryCompatibility {
        enabledAfter("5.3.0")
    }
}
