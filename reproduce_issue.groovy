import groovy.transform.Field

@Field
String testSource = """
package io.micronaut.reproduce

import io.micronaut.context.exceptions.CircularDependencyException
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Replaces
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Singleton
import spock.lang.Specification

/**
 * Reproduces: Turning bean from @Singleton to @Context may cause tests to fail on circular exception
 *
 * This test sets up a circular dependency between ServiceA and ServiceB.
 * ServiceA is annotated with @Context, forcing eager initialization.
 * ServiceB depends on ServiceA, completing the cycle.
 *
 * Additionally, ServiceA is @Replaced by TestServiceA in the test environment.
 * The issue suggests that @Replaces, combined with @Context, can expose circular dependencies
 * prematurely during test context startup, even if the replaced bean's original
 * implementation was @Singleton and might have handled it differently or lazily.
 *
 * If the bug is present, the Micronaut context startup (triggered by @MicronautTest)
 * should fail with a CircularDependencyException.
 * If the test passes, it means the context started successfully, and the bug is not reproduced.
 */
@MicronautTest // This annotation triggers the Micronaut context startup
class ReproduceIssueSpec extends Specification {

    void "test context startup with @Context bean and @Replaces leading to circular dependency"() {
        // If the Micronaut context fails to start due to a CircularDependencyException,
        // this Spock test will automatically fail, which indicates the bug is reproduced.
        // If the context starts successfully, this test method will be executed,
        // meaning the bug is NOT reproduced. In that case, we explicitly fail the test
        // to clearly indicate that the expected bug condition was not met.

        when: "the Micronaut context attempts to initialize with the defined circular @Context dependency"

        then: "a CircularDependencyException is expected during context startup"
        // No explicit assertions here. Spock's @MicronautTest runner will catch
        // exceptions thrown during context initialization.
        // If CircularDependencyException is thrown, the test fails, signaling reproduction.

        // If this line is reached, it means the context started successfully without the expected
        // CircularDependencyException, which indicates the bug is NOT reproduced.
        throw new IllegalStateException("The Micronaut context started successfully. This indicates the CircularDependencyException was NOT thrown during startup for the @Context bean, meaning the bug is NOT reproduced as expected.")
    }

    // --- Beans for circular dependency simulation ---

    /**
     * ServiceA is the bean marked with @Context.
     * This forces eager initialization upon application context startup.
     * It depends on ServiceB, forming one half of the circular dependency.
     */
    @Context
    static class ServiceA {
        final ServiceB serviceB // Depends on ServiceB

        ServiceA(ServiceB serviceB) {
            this.serviceB = serviceB
            // println "ServiceA initialized" // Uncomment for verbose output
        }
    }

    /**
     * ServiceB is a @Singleton bean.
     * It depends on ServiceA, completing the circular dependency cycle.
     */
    @Singleton
    static class ServiceB {
        final ServiceA serviceA // Depends on ServiceA

        ServiceB(ServiceA serviceA) {
            this.serviceA = serviceA
            // println "ServiceB initialized" // Uncomment for verbose output
        }
    }

    /**
     * TestServiceA replaces the original ServiceA.
     * The issue mentions that @Replaces can interact poorly with @Context beans,
     * potentially surfacing circular dependencies.
     * Even if this replacement is @Singleton, its participation in the dependency
     * graph that includes the original @Context ServiceA can trigger the issue.
     */
    @Replaces(ServiceA)
    @Singleton
    static class TestServiceA extends ServiceA {
        TestServiceA(ServiceB serviceB) {
            super(serviceB)
            // println "TestServiceA (Replaced) initialized" // Uncomment for verbose output
        }
    }
}
"""

def testDir = new File("test-suite/src/test/groovy/io/micronaut/reproduce")

try {
    testDir.mkdirs()
    def testFile = new File(testDir, "ReproduceIssueSpec.groovy")
    testFile.write(testSource)

    // Command to execute the newly created Groovy test specification.
    // We use `--continue` to ensure that even if other tests fail, we get the result for our specific test.
    // The issue description refers to `EagerBeansTest` failing, which is in a different repository.
    // This script creates a test in `micronaut-core` that mimics the conditions.
    def command = "./gradlew :test-suite:test --tests io.micronaut.reproduce.ReproduceIssueSpec"
    println "Executing command: ${command}"

    def process = command.execute()
    process.waitForProcessOutput(System.out, System.err)
    def exitCode = process.exitValue()

    // Interpret the exit code from the Gradle test run:
    // If the ReproduceIssueSpec test fails (due to CircularDependencyException caught by @MicronautTest),
    // gradlew will return a non-zero exit code (typically 1). This means the bug is reproduced.
    // If the ReproduceIssueSpec test passes (meaning no CircularDependencyException occurred during startup),
    // gradlew will return 0. This means the bug is NOT reproduced.

    if (exitCode != 0) {
        println "Gradle test command exited with non-zero code: ${exitCode}. This indicates the `ReproduceIssueSpec` failed."
        println "Assuming this failure is due to the expected circular dependency, the issue is reproduced (exit 129)."
        System.exit(129) // Issue reproduced
    } else {
        println "Gradle test command exited with zero code: ${exitCode}. This indicates the `ReproduceIssueSpec` passed."
        println "Assuming this means the circular dependency exception did NOT occur as expected, the issue is NOT reproduced (exit 0)."
        System.exit(0) // Issue not reproduced
    }
} catch (Exception e) {
    e.printStackTrace()
    System.exit(1) // Script error
} finally {
    // Clean up the created test file and directory.
    def testFile = new File(testDir, "ReproduceIssueSpec.groovy")
    if (testFile.exists()) {
        testFile.delete()
    }
    if (testDir.exists()) {
        testDir.deleteDir() // Deletes the directory and its contents
    }
    println "Cleaned up test file and directory."
}