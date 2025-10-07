import groovy.transform.Field

@Field
String javaSource = """
package io.micronaut.reproduce;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import jakarta.inject.Inject;

@Bean
public class Parent {
    @Inject ApplicationContext context;

    public ApplicationContext getContext() {
        return context;
    }
}
"""

@Field
String kotlinSource = """
package io.micronaut.reproduce

import io.micronaut.context.annotation.Bean
import io.micronaut.runtime.Micronaut
import jakarta.annotation.PostConstruct
import io.micronaut.context.ApplicationContext // Import ApplicationContext

@Bean
open class Child : Parent() { // Use 'open' for inheritance
    lateinit var injectedContext: ApplicationContext // A separate field to hold context from Parent if needed for verification in this class

    @PostConstruct
    fun init() {
        // Here, we check if the context from the Java parent is injected
        // The issue states 'check(context != null)' from the original report
        // So we will try to access the context from the parent and ensure it's not null.
        if (context == null) {
            println("ERROR: context from Java Parent is NULL! Issue reproduced.")
            throw IllegalStateException("Context from Java Parent was not injected.")
        }
        this.injectedContext = context
    }
}
"""

@Field
String testSource = """
package io.micronaut.reproduce

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.Micronaut
import spock.lang.Specification
import spock.lang.AutoCleanup

class ReproduceIssueSpec extends Specification {

    @AutoCleanup
    ApplicationContext applicationContext

    void "test Kotlin child inherits injected field from Java parent with KSP"() {
        given:
        applicationContext = Micronaut.run()

        when:
        Child child = applicationContext.getBean(Child)

        then:
        // The @PostConstruct in Child should have thrown an exception if context is null
        // If it reaches here, it means @PostConstruct passed, but we'll re-check to be safe.
        child.context != null

        // This assertion directly reflects the check in the Kotlin class's @PostConstruct
        // If the @PostConstruct didn't throw, this should pass.
        // If the bug is reproduced, the @PostConstruct *should* throw, causing the test to fail.
        // So a test failure means the bug is reproduced.
    }
}
"""

// THIS IS WHERE THE YOUR TEST SOURCE WILL BE PLACED
def testSuiteDir = new File("test-suite/src/test/groovy/io/micronaut/reproduce")
def testSuiteKotlinJavaTestDir = new File("test-suite-kotlin/src/test/java/io/micronaut/reproduce")
def testSuiteKotlinKotlinTestDir = new File("test-suite-kotlin/src/test/kotlin/io/micronaut/reproduce")

try {
    // Create directories
    testSuiteDir.mkdirs()
    testSuiteKotlinJavaTestDir.mkdirs()
    testSuiteKotlinKotlinTestDir.mkdirs()

    // Write source files
    new File(testSuiteKotlinJavaTestDir, "Parent.java").write(javaSource)
    new File(testSuiteKotlinKotlinTestDir, "Child.kt").write(kotlinSource)
    def testFile = new File(testSuiteDir, "ReproduceIssueSpec.groovy")
    testFile.write(testSource)

    // Command to invoke the test script
    // We need to build and test test-suite-kotlin first to ensure KSP processing
    // Then run the test-suite test which depends on it
    def command = "./gradlew :test-suite-kotlin:test :test-suite:test --tests io.micronaut.reproduce.ReproduceIssueSpec"
    println "Executing command: $command"
    def process = command.execute()
    process.waitForProcessOutput(System.out, System.err)
    def gradleExitCode = process.exitValue()

    // Analyze Gradle exit code
    if (gradleExitCode == 0) {
        // If Gradle exited with 0, the test passed. This means the bug was NOT reproduced.
        println "Test passed, issue NOT reproduced."
        System.exit(0)
    } else {
        // If Gradle exited with non-zero, the test failed. This indicates the bug was reproduced.
        // The Child's @PostConstruct should throw an exception if 'context' is null,
        // which would cause the test to fail.
        println "Test failed (Gradle exit code: $gradleExitCode), issue REPRODUCED."
        System.exit(129)
    }
} catch (Exception e) {
    e.printStackTrace()
    System.exit(1) // Script error
} finally {
    // Clean up created files and directories
    println "Cleaning up temporary files..."
    new File(testSuiteKotlinJavaTestDir, "Parent.java").delete()
    new File(testSuiteKotlinKotlinTestDir, "Child.kt").delete()
    new File(testSuiteDir, "ReproduceIssueSpec.groovy").delete()

    // Attempt to delete parent directories, they might not be empty
    testSuiteDir.deleteDir()
    testSuiteKotlinJavaTestDir.deleteDir()
    testSuiteKotlinKotlinTestDir.deleteDir()
}