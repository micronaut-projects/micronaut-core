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
    }
    propertyName = "core"
}
