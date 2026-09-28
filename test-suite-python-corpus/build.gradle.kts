// The corpus of real Pyronaut applications, compiled with the type checker and the static compiler
// of this build: see src/corpus/README.md. The io.micronaut:* coordinates the applications depend
// on are substituted with the projects of this build by the base convention plugin; the rest is
// resolved through the Micronaut platform BOM. Runs with the other Python tests:
//   ./gradlew :test-suite-python-corpus:test -Ppython-ci
plugins {
    id("io.micronaut.build.internal.convention-test-library")
}

fun corpusClasspath(name: String) = configurations.create(name) {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
    // the applications pin GraalJS for their views; a compilation loads neither GraalJS nor a second GraalPy
    exclude(group = "org.graalvm.js")
    exclude(group = "org.graalvm.truffle")
    exclude(group = "org.graalvm.polyglot")
}

val fullstackClasspath = corpusClasspath("fullstackClasspath")
val fullstackProcessorPath = corpusClasspath("fullstackProcessorPath")
val petclinicClasspath = corpusClasspath("petclinicClasspath")
val petclinicProcessorPath = corpusClasspath("petclinicProcessorPath")

// the dependency lists of the applications' pyproject.toml files, minus the artifacts a compilation does not load
val fullstackDependencies = listOf(
    "io.micronaut:micronaut-http-server-netty",
    "io.micronaut.data:micronaut-data-jdbc",
    "io.micronaut.sql:micronaut-jdbc-hikari",
    "com.mysql:mysql-connector-j",
    "io.micronaut.flyway:micronaut-flyway",
    "org.flywaydb:flyway-mysql",
    "io.micronaut.serde:micronaut-serde-jackson",
    "io.micronaut.validation:micronaut-validation",
    "io.micronaut.security:micronaut-security-jwt",
    "org.springframework.security:spring-security-crypto:7.1.1",
    "org.springframework:spring-core:7.0.9",
    "io.micronaut.email:micronaut-email-javamail",
    "io.micronaut.email:micronaut-email-template",
    "org.eclipse.angus:angus-mail",
    "io.micronaut.views:micronaut-views-react:6.2.0",
    "io.micronaut.controlpanel:micronaut-control-panel-datasource",
    "io.micronaut:micronaut-context-python",
)
val fullstackProcessors = listOf(
    "io.micronaut:micronaut-context-python",
    "io.micronaut:micronaut-inject-python",
    "io.micronaut.data:micronaut-data-processor",
    "io.micronaut.serde:micronaut-serde-processor",
    "io.micronaut.validation:micronaut-validation-processor",
    "io.micronaut.security:micronaut-security-annotations",
)
val petclinicDependencies = listOf(
    "io.micronaut:micronaut-http-server-netty",
    "io.micronaut.cache:micronaut-cache-core",
    "io.micronaut.data:micronaut-data-jdbc",
    "io.micronaut.flyway:micronaut-flyway:8.1.1",
    "io.micronaut.serde:micronaut-serde-jackson",
    "com.fasterxml.jackson.core:jackson-databind:2.22.1",
    "io.micronaut.sql:micronaut-jdbc-ucp",
    "io.micronaut.validation:micronaut-validation",
    "io.micronaut.views:micronaut-views-jinjava",
    "com.oracle.database.jdbc:ojdbc11",
    "org.flywaydb:flyway-database-oracle",
    "io.micronaut.controlpanel:micronaut-control-panel-datasource",
    "io.micronaut:micronaut-context-python",
)
val petclinicProcessors = listOf(
    "io.micronaut:micronaut-context-python",
    "io.micronaut:micronaut-inject-python",
    "io.micronaut.data:micronaut-data-processor",
    "io.micronaut.serde:micronaut-serde-processor",
    "io.micronaut.validation:micronaut-validation-processor",
)

dependencies {
    testImplementation(projects.micronautInjectPython)
    testImplementation(projects.micronautContextPython)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)

    for ((configuration, artifacts) in listOf(
        fullstackClasspath to fullstackDependencies,
        fullstackProcessorPath to fullstackProcessors,
        petclinicClasspath to petclinicDependencies,
        petclinicProcessorPath to petclinicProcessors,
    )) {
        configuration(platform(libs.test.boms.micronaut.platform))
        artifacts.forEach { configuration(it) }
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    maxHeapSize = "4g"
    inputs.files(fullstackClasspath, fullstackProcessorPath, petclinicClasspath, petclinicProcessorPath)
    inputs.dir("src/corpus")
    // the class paths of the applications, given to the compiler by the test
    doFirst {
        systemProperty("corpus.fullstack.classpath", fullstackClasspath.asPath)
        systemProperty("corpus.fullstack.processorPath", fullstackProcessorPath.asPath)
        systemProperty("corpus.petclinic.classpath", petclinicClasspath.asPath)
        systemProperty("corpus.petclinic.processorPath", petclinicProcessorPath.asPath)
        // a checkout of an application instead of its pinned copy: -Dcorpus.<name>.dir=<path to src>
        listOf("fullstack", "petclinic").forEach { corpus ->
            System.getProperty("corpus.$corpus.dir")?.let { systemProperty("corpus.$corpus.dir", it) }
        }
    }
}
