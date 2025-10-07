import groovy.transform.Field

@Field
String testSource = """
package io.micronaut.reproduce

import io.micronaut.context.ApplicationContext
import io.micronaut.retry.annotation.Fallback
import io.micronaut.retry.annotation.Recoverable
import io.micronaut.http.client.exceptions.HttpClientResponseException
import spock.lang.Specification
import jakarta.inject.Singleton // Use jakarta.inject for Micronaut 4.x

/**
 * This test reproduces the issue where @Fallback's 'excludes' parameter
 * is not honored for a specified exception type.
 *
 * If the bug is reproduced, the fallback method will be executed even if
 * the thrown exception is listed in 'excludes'.
 * If the bug is fixed, the original exception will be rethrown, and the test will pass.
 */
class ReproduceIssueSpec extends Specification {

    // Define a service interface that can be proxied by Micronaut's AOP for @Recoverable
    interface MyService {
        @Recoverable // Marks this method as recoverable, enabling fallback logic
        String performAction()
    }

    // This is the primary implementation of MyService.
    // Its 'performAction' method will simulate a failure by throwing the exception
    // that we intend to exclude from fallback.
    @Singleton // Make it discoverable by Micronaut's component scanning
    static class MyServiceImpl implements MyService {
        @Override
        String performAction() {
            println "-> MyServiceImpl.performAction() called, throwing HttpClientResponseException..."
            throw new HttpClientResponseException("Simulated HTTP Client Error", null)
        }
    }

    // This is the fallback implementation for MyService.
    // It is linked to MyService using @Fallback(MyService.class).
    // The key part is the @Fallback(excludes = [HttpClientResponseException]) on the method,
    // which according to the issue, is not being honored.
    @Singleton // Make it discoverable by Micronaut's component scanning
    @Fallback(MyService.class) // Link this fallback to MyService
    static class MyServiceFallback implements MyService {
        @Override
        @Fallback(excludes = [HttpClientResponseException]) // This is the annotation under test
        String performAction() {
            // If this method is executed, it means the 'excludes' parameter was ignored,
            // and the bug is reproduced. The test will then fail by not throwing
            // the expected exception.
            println "-> MyServiceFallback.performAction() called. This indicates the 'excludes' parameter was ignored and the bug IS REPRODUCED."
            return "FallbackTriggered"
        }
    }

    void "test @Fallback excludes HttpClientResponseException correctly"() {
        given:
        // Initialize a Micronaut ApplicationContext to load and wire the beans
        def applicationContext = ApplicationContext.builder()
                                    .packages("io.micronaut.reproduce") // Scan for our test beans
                                    .build()
        applicationContext.start()

        // Get the proxied service bean. Micronaut's AOP will wrap MyServiceImpl
        // with the @Recoverable and @Fallback logic.
        def service = applicationContext.getBean(MyService)

        when:
        // Call the service method.
        // If the bug is fixed, performAction() will rethrow HttpClientResponseException,
        // which will be caught by the 'then: thrown' block.
        // If the bug is reproduced, performAction() will return "FallbackTriggered",
        // and the 'then: thrown' block will fail.
        service.performAction()

        then:
        // If the bug is fixed, performAction() will rethrow HttpClientResponseException.
        // This assertion expects that exception to be thrown, and thus the test passes.
        thrown(HttpClientResponseException)

        and: "The message of the thrown exception should match the original service's message"
        exception.message == "Simulated HTTP Client Error"

        cleanup:
        // Always close the application context to release resources
        applicationContext.close()
    }
}
"""

def testDir = new File("test-suite/src/test/groovy/io/micronaut/reproduce")
def testFile = new File(testDir, "ReproduceIssueSpec.groovy")

try {
    testDir.mkdirs()
    testFile.write(testSource)

    // Command to execute the specific Spock test using Gradle
    def command = "./gradlew :test-suite:test --tests io.micronaut.reproduce.ReproduceIssueSpec"
    println "Executing command: \$command"

    // Execute the command and capture its output
    def process = command.execute()
    process.waitForProcessOutput(System.out, System.err)
    def gradleExitCode = process.exitValue()

    // Analyze the Gradle process exit code
    if (gradleExitCode == 0) {
        // If Gradle exited with 0, it means the Spock test passed.
        // The Spock test passes if HttpClientResponseException was correctly rethrown (bug NOT reproduced).
        println "Test passed (Gradle exit 0). Bug NOT reproduced."
        System.exit(0)
    } else {
        // If Gradle exited with a non-zero code, it means the Spock test failed.
        // The Spock test fails if the fallback was triggered unexpectedly (HttpClientResponseException was NOT rethrown).
        println "Test failed (Gradle exit \$gradleExitCode). Bug IS reproduced."
        System.exit(129)
    }
} catch (Exception e) {
    e.printStackTrace()
    System.exit(1) // General script error
} finally {
    // Clean up the created test file and directory
    if (testFile.exists()) {
        testFile.delete()
    }
    if (testDir.exists()) {
        testDir.deleteDir()
    }
}