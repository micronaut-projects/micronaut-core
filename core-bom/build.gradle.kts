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

        // Kotlin 2 is now the only supported release
        acceptedVersionRegressions.add("kotlin2")
        acceptedVersionRegressions.add("ksp2")

    }
    propertyName = "core"
}
