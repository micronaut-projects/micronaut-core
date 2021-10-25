package io.micronaut.build.internal.pom

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import static org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP

@CompileStatic
@CacheableTask
abstract class PomChecker extends DefaultTask {
    @Input
    abstract ListProperty<String> getRepositories()

    @Input
    abstract Property<String> getPomCoordinates()

    @Input
    abstract Property<Boolean> getFailOnSnapshots()

    @Input
    abstract Property<Boolean> getFailOnError()

    @Input
    abstract Property<Boolean> getFailOnBomContents()

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    @Optional
    abstract RegularFileProperty getPomFile()

    @Input
    abstract Property<Boolean> getCheckBomContents()

    @OutputFile
    abstract RegularFileProperty getReport()

    PomChecker() {
        description = "Verifies a POM file"
        group = VERIFICATION_GROUP
        getFailOnError().convention(true)
        getFailOnSnapshots().convention(getPomCoordinates().map(v -> !v.endsWith("-SNAPSHOT")))
        getFailOnBomContents().convention(false)
    }

    @TaskAction
    void verifyBom() {
        List<String> errors = []
        boolean found = false
        for (String repositoryUrl : repositories.get()) {
            def coordinates = pomCoordinates.get().split(':')
            if (coordinates.length != 3) {
                throw new GradleException("Incorrect BOM coordinates '${pomCoordinates.get()}': should be of the form group:artifact:version ")
            }
            def (group, artifact, version) = [coordinates[0], coordinates[1], coordinates[2]]
            if (repositoryUrl.endsWith('/')) {
                repositoryUrl = repositoryUrl.substring(0, repositoryUrl.length() - 1)
            }
            def uri = "$repositoryUrl/${group.replace((char) '.', (char) '/')}/${artifact}/${version}/${artifact}-${version}.pom"
            try {
                def pom
                if (pomFile.present) {
                    pom = new XmlSlurper().parse(pomFile.asFile.get())
                } else {
                    pom = new XmlSlurper().parse(uri)
                }
                if (checkBomContents.get()) {
                     assertDependencyManagementConfinedToGroup(pom, group, artifact, errors)
                }
                if (version.endsWith("-SNAPSHOT")) {
                    String message = "Dependency ${pomCoordinates.get()} is a SNAPSHOT"
                    if (failOnSnapshots.get()) {
                        errors << message
                    } else {
                        logger.warn(message)
                    }
                }
                found = true
                break
            } catch (Exception ex) {
                getLogger().debug("Skipping repository $repositoryUrl as the POM file is missing or corrupt")
            }
        }
        if (!found) {
            errors << "Dependency ${pomCoordinates.get()} is wasn't found in any repository".toString()
        }
        def reportFile = report.asFile.get()
        def reportDir = reportFile.parentFile
        if (reportDir.directory || reportDir.mkdirs()) {
            reportFile.text = errors.join("\n")
        } else {
            throw new GradleException("Unable to write report file to ${reportFile}")
        }
        if (errors) {
            String message = "Validation failed for ${pomCoordinates.get()}. Check the report at ${reportFile} for details."
            if (failOnError.get()) {
                throw new GradleException(message)
            } else {
                logger.error(message)
            }
        }
    }

    @CompileDynamic
    private void assertDependencyManagementConfinedToGroup(GPathResult pom, String group, String name, List<String> errors) {
        pom.dependencyManagement.dependencies.dependency.each {
            String depGroup = it.groupId.text().replace('${project.groupId}', group)
            if (!depGroup.startsWith(group)) {
                def scope = it.scope.text()
                if (scope != 'import') {
                    String message = "Error validating BOM [${name}]: includes the dependency [${it.groupId}:${it.artifactId}:${it.version}] which doesn't start with the group id of the BOM: [${group}]"
                    if (failOnBomContents.get()) {
                        errors << message
                    } else {
                        logger.warn(message)
                    }
                }
            }
        }
    }
}
