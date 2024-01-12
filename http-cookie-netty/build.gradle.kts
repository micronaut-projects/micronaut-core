plugins {
    id("io.micronaut.build.internal.convention-library")
}
micronautBuild {
    core {
        usesMicronautTestSpock()
    }
}
dependencies {
    annotationProcessor(project(":inject-java"))
    api(project(":http"))
    api(libs.managed.netty.codec.http)
}
micronautBuild {
    // new module, so no binary check
    binaryCompatibility {
        enabled.set(false)
    }
}