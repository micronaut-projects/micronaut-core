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
        // remove managed Kotlin 2 Ksp 2
        acceptedLibraryRegressions.add("ksp2")
        acceptedLibraryRegressions.add("kotlin2-stdlib")
        acceptedLibraryRegressions.add("kotlin2-test")
        acceptedLibraryRegressions.add("kotlin2-reflect")
        acceptedLibraryRegressions.add("ksp2-api")
        acceptedLibraryRegressions.add("kotlin2-annotation-processing-embeddable")
        acceptedLibraryRegressions.add("kotlin2-compiler-embeddable")
        acceptedLibraryRegressions.add("kotlin2-stdlib-jdk8")
        acceptedVersionRegressions.add("jackson-databind") // version is already defined with jackson
    }
    propertyName = "core"
}
