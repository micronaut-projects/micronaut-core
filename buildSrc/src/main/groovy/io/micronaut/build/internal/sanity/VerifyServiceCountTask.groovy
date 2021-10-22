package io.micronaut.build.internal.sanity


import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class VerifyServiceCountTask extends DefaultTask {

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract DirectoryProperty getClassesDirectory()

    @Input
    abstract MapProperty<String, Integer> getExpectedServiceCount()

    @Input
    abstract Property<Boolean> getFailOnMismatch()

    @OutputFile
    abstract RegularFileProperty getOutputFile()

    @Internal
    abstract Property<Object> getGradleEnterprise()

    @TaskAction
    void check() {
        boolean failed = false
        def error = { String service, String message ->
            failed = true
            logger.error("""=======
WARNING
=======
$message
=======
""")
            gradleEnterprise.get().buildScan {
                it.tag 'ANNOTATION_PROCESSING_FAILURE'
                it.value "${path}: Annotation processor service file $service", message
            }
        }
        def baseDir = classesDirectory.dir("META-INF/services").get().asFile
        expectedServiceCount.get().each { String k, Integer expectedCount ->
            def serviceFile = new File(baseDir, k)
            if (serviceFile.exists()) {
                def relativePath = classesDirectory.get().asFile.toPath().relativize(serviceFile.toPath()).toString()
                def actualCount = serviceFile.readLines().findAll { it.trim() }.size()
                if (actualCount < expectedCount) {
                    error(k, "Expected $expectedCount services of type $k to be registed but only found $actualCount in $relativePath")
                } else if (actualCount > expectedCount) {
                    error(k, "Expected $expectedCount services of type $k to be registed but found more ($actualCount) in $relativePath. Consider updating the internalSanityChecks count.")
                }
            } else {
                error(k, "Expected a service file ${serviceFile} to be generated but it's absent!")
            }
        }
        if (failed && failOnMismatch.get()) {
            throw new GradleException("Sanity check for service file generation failed")
        }
        def outputFile = outputFile.get().asFile
        if (outputFile.parentFile.exists() || outputFile.parentFile.mkdirs()) {
            outputFile.text = "ok"
        }
    }
}
