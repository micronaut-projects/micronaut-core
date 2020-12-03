package io.micronaut.bom.verifier

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@CompileStatic
abstract class BomVerifier extends DefaultTask {

    @Internal
    Configuration verificationConf

    @Internal
    abstract Property<ArtifactRepository> getRepository()

    @OutputFile
    abstract RegularFileProperty getOutputFile()

    BomVerifier() {
        // Because it messes with repositories, this task
        // isn't compatible with the configuration cache
        // nor is it up-to-date checking safe
        outputs.upToDateWhen { false }
    }

    @TaskAction
    void verify() {
        def repositories = project.repositories
        def repo = repository.get()
        repositories.clear()
        try {
            repositories.add(repo)
            def result = verificationConf.incoming.resolutionResult
            def unresolved = result.allDependencies.findAll { it instanceof UnresolvedDependencyResult }
            if (unresolved) {
                def unresolvedComponents = unresolved.collect { it.requested }.join(", ")
                throw new GradleException("The BOM contains references to the following components which weren't found in any remote repository: ${unresolvedComponents}")
            }
        } finally {
            repositories.remove(repo)
        }
        def out = outputFile.get().asFile
        def parent = out.parentFile
        if (parent.exists() || parent.mkdirs()) {
            out << "All dependencies verified"
        }
    }
}
