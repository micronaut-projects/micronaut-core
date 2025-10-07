import groovy.transform.Field

@Field
String testSource = """
package io.micronaut.reproduce

import io.micronaut.http.client.exceptions.ReadTimeoutException
import spock.lang.Specification
import io.micronaut.http.client.exceptions.HttpClientException

/**
 * Reproduces the issue where ReadTimeoutException's serviceId remains null
 * due to HttpClientException's `serviceIdLocked` flag being set during
 * ReadTimeoutException's construction.
 *
 * If this test passes, the issue is reproduced (serviceId is null after attempting to set,
 * and an IllegalStateException is thrown upon attempt).
 * If this test fails, the issue is resolved (serviceId can be set).
 */
class ReproduceIssueSpec extends Specification {

    void "ReadTimeoutException serviceId is null and cannot be set"() {
        setup:
        // Use the static final instance as there is no public constructor for ReadTimeoutException
        HttpClientException exception = ReadTimeoutException.TIMEOUT_EXCEPTION

        when: "An attempt is made to set the serviceId on the shared ReadTimeoutException instance"
        exception.setServiceId("test-service-id")

        then: "An IllegalStateException should be thrown, indicating that the serviceId cannot be set"
        thrown(IllegalStateException)

        and: "The serviceId on the exception should remain null after the failed attempt"
        // Note: The `thrown` block intercepts the exception, so subsequent `and:` blocks
        // are still executed. If the exception was thrown, the serviceId would indeed remain null.
        exception.getServiceId() == null
    }
}
"""

def testDir = new File("test-suite/src/test/groovy/io/micronaut/reproduce")
def exitCode = 1 // Default to error

try {
    testDir.mkdirs()
    def testFile = new File(testDir, "ReproduceIssueSpec.groovy")
    testFile.write(testSource)

    println "Executing test to reproduce the issue..."
    // Target the 'test-suite' project where the test file is located
    def command = "./gradlew :test-suite:test --tests io.micronaut.reproduce.ReproduceIssueSpec"
    def process = command.execute()
    process.waitForProcessOutput(System.out, System.err)
    def gradleExitValue = process.exitValue()

    // Gradle test exit codes:
    // 0: All tests passed
    // >0: Tests failed or other Gradle error

    if (gradleExitValue == 0) {
        // If the Spock test "ReadTimeoutException serviceId is null and cannot be set" passes,
        // it means the IllegalStateException was thrown AND getServiceId() was null,
        // which confirms the issue IS reproduced.
        println "Test passed: ReadTimeoutException's serviceId remained null and could not be set. Issue reproduced (exit code 129)."
        exitCode = 129
    } else {
        // If the Spock test failed, it means either the IllegalStateException was NOT thrown
        // or getServiceId() was NOT null, meaning the issue is NOT reproduced/fixed.
        println "Test failed: ReadTimeoutException's serviceId was settable or the expected exception was not thrown. Issue NOT reproduced (exit code 0)."
        exitCode = 0
    }

} catch (Exception e) {
    e.printStackTrace()
    exitCode = 1 // Script error
} finally {
    // Clean up the created test file and directory
    if (testDir.exists()) {
        testDir.deleteDir()
    }
}

System.exit(exitCode)