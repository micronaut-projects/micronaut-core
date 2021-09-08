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
import org.gradle.api.tasks.OutputFile
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
    abstract Property<Boolean> getCheckBomContents()

    @OutputFile
    abstract RegularFileProperty getReport()

    PomChecker() {
        description = "Verifies a POM file"
        group = VERIFICATION_GROUP
    }

    @TaskAction
    void verifyBom() {
        List<String> errors = []
        for (String repositoryUrl : repositories.get()) {
            def coordinates = pomCoordinates.get().split(':')
            if (coordinates.length != 3) {
                throw new GradleException("Incorrect BOM coordinates '${pomCoordinates.get()}': should be of the form group:artifact:version ")
            }
            def (group, artifact, version) = [coordinates[0], coordinates[1], coordinates[2]]
            def uri = "$repositoryUrl/${group.replace((char) '.', (char) '/')}/${artifact}/${version}/${artifact}-${version}.pom"
            try {
                def pom = new XmlSlurper().parse(uri)
                if (checkBomContents.get()) {
                    assertDependencyManagementConfinedToGroup(pom, group, artifact, errors)
                }
                if (version.endsWith("-SNAPSHOT")) {
                    errors << "Dependency ${pomCoordinates.get()} is a SNAPSHOT".toString()
                }
                return
            } catch (Exception ex) {
                getLogger().debug("Skipping repository $repositoryUrl as the POM file is missing or corrupt")
            }
        }
        def reportFile = report.asFile.get()
        def reportDir = reportFile.parentFile
        if (reportDir.directory || reportDir.mkdirs()) {
            reportFile.text = errors.join("\n")
        } else {
            throw new GradleException("Unable to write report file to ${reportFile}")
        }
        if (errors) {
            throw new GradleException("Validation failed for ${pomCoordinates.get()}. Check the report at ${reportFile} for details.")
        }
    }

    @CompileDynamic
    private static void assertDependencyManagementConfinedToGroup(GPathResult pom, String group, String name, List<String> errors) {
        pom.dependencyManagement.dependencies.dependency.each {
            String depGroup = it.groupId.text().replace('${project.groupId}', group)
            if (!depGroup.startsWith(group)) {
                errors << "Error validating BOM [${name}]: includes the dependency [${it.groupId}:${it.artifactId}:${it.version}] which doesn't start with the group id of the BOM: [${group}]".toString()
            }
        }
    }
}
