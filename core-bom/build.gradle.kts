plugins {
    id("io.micronaut.build.internal.bom")
}

group = properties["projectGroupId"]
version = properties["projectVersion"]

micronautBom {
    extraExcludedProjects = listOf(
            "benchmarks",
            "inject-test-utils"
    )
    propertyName = "core"
}
