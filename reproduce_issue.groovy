import groovy.transform.Field

@Field
String testSource = """
package io.micronaut.reproduce

import io.micronaut.http.server.types.files.StreamedFile
import spock.lang.Specification

class ReproduceIssueSpec extends Specification {

    // This test verifies the existence of the method with the incorrect RFC number in its name.
    // The bug is that 'encodeRfc6987' exists while it should be 'encodeRfc6266'.
    // If 'encodeRfc6987' is found, the bug is reproduced and this test should fail.
    // If 'encodeRfc6987' is not found (meaning it was renamed or removed), the bug is considered fixed and this test should pass.

    def "should check for the existence of incorrectly named encodeRfc6987 method in StreamedFile"() {
        when:
        boolean methodExists = false
        try {
            StreamedFile.getDeclaredMethod("encodeRfc6987", String.class)
            methodExists = true
            println "Method 'encodeRfc6987' FOUND. This indicates the bug is reproduced."
        } catch (NoSuchMethodException e) {
            methodExists = false
            println "Method 'encodeRfc6987' NOT FOUND. This indicates the bug is fixed or not reproducible."
        }

        then:
        // If the method exists, we assert false to make the test fail, indicating the bug is reproduced.
        // If the method does not exist, we assert true, indicating the bug is not reproduced (fixed).
        !methodExists
    }
}
"""

def testDir = new File("test-suite/src/test/groovy/io/micronaut/reproduce")

try {
    testDir.mkdirs()
    def testFile = new File(testDir, "ReproduceIssueSpec.groovy")
    testFile.write(testSource)

    // Command to run the newly created Groovy test.
    // We target the :test-suite module which has access to the necessary classes from :http-server.
    def command = "./gradlew :test-suite:test --tests io.micronaut.reproduce.ReproduceIssueSpec"
    def process = command.execute()
    process.waitForProcessOutput(System.out, System.err)
    def gradleExitCode = process.exitValue()

    if (gradleExitCode == 0) {
        // Gradle test task passed. This means the Spock test passed.
        // The Spock test passes if 'encodeRfc6987' method does NOT exist.
        // So, the bug is NOT reproduced.
        println "--- BUG NOT REPRODUCED / FIXED ---"
        System.exit(0)
    } else if (gradleExitCode != 0) {
        // Gradle test task failed. This means the Spock test failed.
        // The Spock test fails if 'encodeRfc6987' method EXISTS.
        // So, the bug IS reproduced.
        println "--- BUG REPRODUCED ---"
        println "Gradle test task failed with exit code ${gradleExitCode}. This indicates the 'encodeRfc6987' method still exists, reproducing the issue."
        System.exit(129)
    } else {
        // Any other unexpected exit code from Gradle for a test run
        println "--- UNEXPECTED GRADLE EXIT CODE ---"
        System.exit(1)
    }
} catch (Exception e) {
    e.printStackTrace()
    System.exit(1) // Script error in the Groovy runner itself
} finally {
    // Clean up the created test file and directory
    if (testDir.exists()) {
        testDir.deleteDir()
    }
}