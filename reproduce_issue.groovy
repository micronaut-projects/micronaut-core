import groovy.transform.Field
import java.io.ByteArrayOutputStream

@Field
String testSource = """
package io.micronaut.reproduce

import io.micronaut.http.uri.UriBuilder
import spock.lang.Specification

class ReproduceIssueSpec extends Specification {

    def "test UriBuilder encodes spaces as plus not percent20"() {
        println "DEBUG: ReproduceIssueSpec test method entered (UriBuilder direct test)."

        given: "a query parameter with a space"
        String paramValue = "value with spaces"

        when: "a UriBuilder encodes the parameter"
        // The issue states DefaultUriBuilder uses java.net.URLEncoder.
        // UriBuilder.of("/").queryParam() eventually uses DefaultUriBuilder.
        String encodedQuery = UriBuilder.of("/").queryParam("myParam", paramValue).build().getRawQuery()

        then: "the encoded parameter should use %20 for spaces as per RFC3986"
        String expectedEncodedPlus = "myParam=value+with+spaces" // As reported in the bug (W3C HTML 4 / java.net.URLEncoder default)
        String expectedEncodedPercent20 = "myParam=value%20with%20spaces" // Expected correct encoding (RFC 3986)

        println "DEBUG: Encoded query: '\$encodedQuery'"

        if (encodedQuery == expectedEncodedPlus) {
            println ">>> BUG REPRODUCED: UriBuilder encoded spaces as '+' as per the issue description."
            System.exit(129) // Indicate bug reproduced
        } else if (encodedQuery == expectedEncodedPercent20) {
            println ">>> BUG NOT REPRODUCED: UriBuilder encoded spaces as '%20', meaning the bug is fixed."
            System.exit(0) // Indicate bug not reproduced
        } else {
            println ">>> UNEXPECTED RESULT: Encoded query: '\$encodedQuery'. Neither '+' nor '%20' encoding found."
            System.exit(1) // General error, unexpected encoding
        }
    }
}
"""

def testDir = new File("test-suite/src/test/groovy/io/micronaut/reproduce")
def testFile = new File(testDir, "ReproduceIssueSpec.groovy")

try {
    testDir.mkdirs()
    testFile.write(testSource)
    System.out.println "Created test file: ${testFile.absolutePath}"

    // For this simple test, we don't need EmbeddedServer or HttpClient injection.
    // Removed -Dmicronaut.test.server.port=-1 as @MicronautTest is no longer used.
    // The test should run as a plain Spock test within the existing test-suite.
    def command = "./gradlew :test-suite:cleanTest :test-suite:test --tests io.micronaut.reproduce.ReproduceIssueSpec --rerun-tasks --info"
    System.out.println "Executing Gradle command: ${command}"

    ByteArrayOutputStream stdout = new ByteArrayOutputStream()
    ByteArrayOutputStream stderr = new ByteArrayOutputStream()

    def process = command.execute()
    process.waitForProcessOutput(stdout, stderr) // Capture output

    System.out.println "--- Gradle Standard Output ---"
    System.out.println stdout.toString()
    System.out.println "--- Gradle Standard Error ---"
    System.err.println stderr.toString()
    System.out.println "-----------------------------"

    def gradleExitCode = process.exitValue()
    System.out.println "Gradle command finished with exit code: ${gradleExitCode}"

    String fullOutput = stdout.toString() + stderr.toString()

    // Check for specific messages from the ReproduceIssueSpec to determine if the test ran and what it found.
    if (fullOutput.contains(">>> BUG REPRODUCED:")) {
        System.out.println "Script wrapper: Test explicitly reported 'BUG REPRODUCED'."
        System.exit(129) // Indicate bug reproduced
    } else if (fullOutput.contains(">>> BUG NOT REPRODUCED:")) {
        System.out.println "Script wrapper: Test explicitly reported 'BUG NOT REPRODUCED'."
        System.exit(0) // Indicate bug not reproduced
    } else if (fullOutput.contains(">>> UNEXPECTED RESULT:")) {
        System.out.println "Script wrapper: Test reported 'UNEXPECTED RESULT'."
        System.exit(1) // General error, unexpected encoding
    } else if (fullOutput.contains("NO TESTS WERE EXECUTED")) {
        System.err.println "Script wrapper: No tests were executed by Gradle. Check test path and build configuration."
        System.exit(1)
    } else if (gradleExitCode != 0) {
        System.err.println "Script wrapper: Gradle command failed with non-zero exit code (${gradleExitCode}). Review Gradle logs for compilation or test execution errors."
        System.exit(1)
    } else {
        // Gradle exited with 0, but no specific test outcome message was found. This indicates the test did not run correctly.
        System.err.println "Script wrapper: Gradle command completed with exit code 0, but no expected test outcome or diagnostic messages found. The test might not have run correctly."
        System.exit(1)
    }
} catch (Exception e) {
    System.err.println "Error in Groovy reproduction script wrapper itself:"
    e.printStackTrace(System.err)
    System.exit(1) // Script error
} finally {
    // Clean up the created test file.
    if (testFile.exists()) {
        testFile.delete()
        System.out.println "Deleted test file: ${testFile.absolutePath}"
    }
}