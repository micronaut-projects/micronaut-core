import groovy.transform.Field

@Field
String testSource = """
package io.micronaut.reproduce

import io.micronaut.retry.annotation.Retryable
import io.micronaut.test.extensions.spock.MicronautSpec
import jakarta.inject.Singleton
import spock.lang.Shared

class ReproduceIssueSpec extends MicronautSpec {

    @Shared
    static int callCount = 0

    // Define a service interface as a static inner interface for proper bean discovery
    interface MyService {
        @Retryable
        void doSomething()
    }

    // Define the implementation as a static inner class for proper bean discovery
    @Singleton
    static class MyServiceImpl implements MyService {
        @Override
        @Retryable // The annotation is also applied here to ensure it's picked up by the AOP proxy
        void doSomething() {
            ReproduceIssueSpec.callCount++
            println "doSomething called. Call count: \${ReproduceIssueSpec.callCount}"
            // Throw an Error subtype, which should NOT be retried by default
            throw new OutOfMemoryError("Simulated OutOfMemoryError")
        }
    }

    void "test @Retryable should not retry on OutOfMemoryError by default"() {
        setup:
        callCount = 0 // Reset counter for this test run

        // MicronautSpec automatically handles the application context lifecycle and bean injection.
        // We can directly get the bean from the context provided by MicronautSpec.
        MyService service = applicationContext.getBean(MyService)

        when:
        // Execute the method that throws OutOfMemoryError.
        // We wrap it in a try-catch to observe the outcome and prevent the test runner from failing directly,
        // allowing us to assert on the call count.
        def caughtThrowable = null
        try {
            service.doSomething()
        } catch (Throwable t) {
            caughtThrowable = t
            println "Caught throwable after potential retries: \${t.class.simpleName}: \${t.message}"
        }

        then:
        // Ensure a throwable was caught, as the method is designed to always throw
        caughtThrowable != null

        // Ensure the caught throwable is an OutOfMemoryError or has it as a cause
        // This handles cases where Micronaut might wrap the original exception.
        caughtThrowable instanceof OutOfMemoryError ||
        (caughtThrowable.cause != null && caughtThrowable.cause instanceof OutOfMemoryError)

        // Expected behavior (if fixed): callCount == 1 (only the initial attempt, no retry for Error)
        // Actual behavior (if bug reproduced): callCount > 1 (initial attempt + retries because Error is caught by default)
        println "Final call count: \${callCount}"

        // If callCount > 1, it means the bug is reproduced, and this assertion will fail.
        // A failed Spock test will cause the Gradle test task to return a non-zero exit code.
        callCount == 1
    }
}
"""

def testDir = new File("test-suite/src/test/groovy/io/micronaut/reproduce")

try {
    testDir.mkdirs()
    def testFile = new File(testDir, "ReproduceIssueSpec.groovy")
    testFile.write(testSource)

    // Command to invoke the test script.
    // The test script itself will assert on the call count.
    // If the assertion fails (bug reproduced), gradlew will exit with a non-zero code.
    // If the assertion passes (bug not reproduced/fixed), gradlew will exit with 0.
    def command = "./gradlew :test-suite:test --tests io.micronaut.reproduce.ReproduceIssueSpec --build-cache --max-workers=2"
    def process = command.execute()
    process.waitForProcessOutput(System.out, System.err)
    def gradleExitCode = process.exitValue()
    
    // Interpret the Gradle exit code
    if (gradleExitCode == 0) {
        // Gradle test task passed (callCount was 1). Bug is NOT reproduced.
        println "Bug NOT reproduced (or fixed): @Retryable did not retry on OutOfMemoryError by default."
        System.exit(0)
    } else if (gradleExitCode != 0) {
        // Gradle test task failed. If it's due to the assertion failure (callCount > 1),
        // it means the bug IS reproduced. We assume this specific test failure implies reproduction.
        println "Bug reproduced: @Retryable retried on OutOfMemoryError by default."
        System.exit(129)
    } else {
        // Any other unexpected error
        println "Unexpected error during Gradle test execution. Gradle exit code: \$gradleExitCode"
        System.exit(1)
    }
} catch (Exception e) {
    e.printStackTrace()
    System.exit(1) // Script error
} finally {
    // Clean up the created test file and directory
    if (testDir.exists()) {
        testDir.deleteDir()
    }
}