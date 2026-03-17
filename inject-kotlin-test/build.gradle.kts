plugins {
    id("io.micronaut.build.internal.convention-library")
}

dependencies {
    compileOnly(projects.micronautInjectKotlin)
    api(libs.managed.groovy)
    api(libs.spock) {
        exclude(module = "groovy-all")
    }
    api(libs.zacsweers.kct.ksp)
}
