package io.micronaut.bom.verifier

import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.tasks.TaskContainer

import javax.inject.Inject

abstract class BomVerification {

    @Inject
    abstract ObjectFactory getObjects()

    @Inject
    abstract ConfigurationContainer getConfigurations()

    @Inject
    abstract TaskContainer getTasks()

    @Inject
    abstract ArtifactRepositoryContainer getRepositories()

    @Inject
    abstract ProjectLayout getLayout()

    @Inject
    abstract ProviderFactory getProviders()

    void verifyOnRepository(Closure<ArtifactRepository> repositorySpec) {
        def repo = repositorySpec.call()
        def name = repo.name
        def verificationConf = configurations.create("verification${name.capitalize()}") {
            it.transitive = false // because we don't care about transitive dependencies
            it.canBeResolved = true
            it.canBeConsumed = false
            it.extendsFrom(configurations.verification)
            it.attributes {
                it.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY))
                it.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.JAR))
            }
        }

        def verifyBom = tasks.register("verifyBomFor${name.capitalize()}", BomVerifier) {
            it.verificationConf = verificationConf
            it.repository.set(providers.provider { repo })
            it.outputFile.set(layout.buildDirectory.file("bom-verification/${name}.txt"))
        }
        tasks.named('verifyAll') {
            dependsOn verifyBom
        }

        // Make sure that before any kind of publishing we verify the BOM
        tasks.withType(AbstractPublishToMaven).configureEach {
            dependsOn verifyBom
        }
    }
}
