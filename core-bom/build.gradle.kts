plugins {
    id("io.micronaut.build.internal.bom")
}

group = properties["projectGroupId"].toString()
version = properties["projectVersion"].toString()

micronautBom {
    extraExcludedProjects = listOf(
            "benchmarks",
            "inject-test-utils"
    )
    suppressions {
        // io_uring was graduated to the main netty repo, so we don't track the incubator version anymore.
        acceptedVersionRegressions.add("netty-iouring")
        acceptedVersionRegressions.add("netty-http3")
        acceptedLibraryRegressions.add("netty-incubator-codec-http3")
        acceptedVersionRegressions.add("jackson-databind") // version is already defined with jackson
        acceptedVersionRegressions.add("netty-contrib-multipart")
        acceptedLibraryRegressions.add("netty-contrib-multipart-vintage")
        // Accept removal of obsolete Jackson 2 add-ons as we are on Jackson 3
        acceptedLibraryRegressions.add("jackson-module-parameterNames")
        acceptedLibraryRegressions.add("jackson-datatype-jdk8")
        acceptedLibraryRegressions.add("jackson-datatype-jsr310")

        // Kotlin 2 is now the only supported release
        acceptedVersionRegressions.add("kotlin2")
        acceptedVersionRegressions.add("ksp2")

        dependencies.add("tools.jackson:jackson-bom:3.0.1")
        dependencies.add("com.fasterxml.jackson.core:jackson-annotations:2.20")
    }
    propertyName = "core"
}
