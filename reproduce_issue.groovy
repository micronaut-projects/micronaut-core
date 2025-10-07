import groovy.transform.Field

@Field
String testSource = """
package io.micronaut.reproduce

import io.micronaut.http.netty.cookies.NettyCookie
import io.netty.handler.codec.http.cookie.DefaultCookie
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.NotSerializableException
import spock.lang.Specification

class ReproduceIssueSpec extends Specification {

    def "NettyCookie should be serializable or throw NotSerializableException"() {
        when: "a NettyCookie is created and serialization is attempted"
        def nettyCookie = new NettyCookie(new DefaultCookie("mycookie", "myvalue"))

        boolean serializedSuccessfully = false
        try {
            def baos = new ByteArrayOutputStream()
            def oos = new ObjectOutputStream(baos)
            oos.writeObject(nettyCookie)
            oos.close()
            baos.close()
            serializedSuccessfully = true
        } catch (NotSerializableException e) {
            // This is the specific exception that indicates the bug is reproduced.
            println ">>> Caught NotSerializableException: " + e.message
            serializedSuccessfully = false
        } catch (Exception e) {
            // Catch any other unexpected exceptions
            println ">>> Caught unexpected exception during serialization: " + e.message
            e.printStackTrace()
            serializedSuccessfully = false
        }

        then: "the NettyCookie can be serialized without NotSerializableException"
        // This assertion should pass if the bug is fixed (serialization succeeds).
        // This assertion should fail if the bug is reproduced (NotSerializableException is caught).
        serializedSuccessfully == true
    }
}
"""

def testDir = new File("test-suite/src/test/groovy/io/micronaut/reproduce")
def testFile = new File(testDir, "ReproduceIssueSpec.groovy")

try {
    testDir.mkdirs()
    testFile.write(testSource)

    // Command to execute the specific Spock test
    def command = "./gradlew :test-suite:test --tests io.micronaut.reproduce.ReproduceIssueSpec"
    println "Executing command: $command"
    def process = command.execute()
    process.waitForProcessOutput(System.out, System.err)
    def gradlewExitCode = process.exitValue()

    // Logic to determine the final script exit code based on gradlew's exit code:
    // If gradlewExitCode is 0: The Spock test passed. This means `serializedSuccessfully` was true.
    //                          Thus, the bug is NOT reproduced (serialization succeeded).
    //                          In this scenario, the outer script should exit with 0.
    //
    // If gradlewExitCode is non-zero (e.g., 1 for test failure): The Spock test failed. This means `serializedSuccessfully` was false.
    //                                  This indicates that `NotSerializableException` was caught,
    //                                  meaning the bug IS reproduced.
    //                                  In this scenario, the outer script should exit with 129.

    if (gradlewExitCode == 0) {
        println "Gradle test passed. This implies NettyCookie serialized successfully. Bug is NOT reproduced."
        System.exit(0) // Issue not reproduced
    } else {
        println "Gradle test failed. This implies NettyCookie serialization failed (likely NotSerializableException). Bug IS reproduced."
        System.exit(129) // Issue reproduced
    }
} catch (Exception e) {
    e.printStackTrace()
    System.exit(1) // Script execution error
} finally {
    // Clean up the created test file and directory
    if (testDir.exists()) {
        testDir.deleteDir() // This will recursively delete the directory and its contents
    }
}