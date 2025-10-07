import groovy.transform.Field

@Field
String testSource = """
package io.micronaut.reproduce

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Status
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono
import spock.lang.Specification

class ReproduceIssueSpec extends Specification {

    EmbeddedServer embeddedServer
    HttpClient httpClient

    void setup() {
        embeddedServer = ApplicationContext.run(EmbeddedServer)
        httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())
    }

    void cleanup() {
        httpClient?.close()
        embeddedServer?.close()
    }

    @Controller("/")
    static class MyController {
        @Get("/502")
        @Status(HttpStatus.BAD_GATEWAY)
        Mono<String> badGateway() {
            println "Controller method badGateway() invoked, returning Mono.empty()"
            Mono.empty()
        }

        // Add an endpoint to confirm the server is generally working and status annotations are processed
        @Get("/200-test")
        @Status(HttpStatus.OK)
        Mono<String> okTest() {
            Mono.just("OK")
        }
    }

    void "test controller returns 404 when Mono.empty() and @Status is used"() {
        when: "A request is made to the endpoint that should return BAD_GATEWAY (502) with an empty publisher"
        HttpResponse<String> response
        try {
            // HttpClient.toBlocking().exchange() will throw HttpClientResponseException for 4xx/5xx responses
            response = httpClient.toBlocking().exchange(HttpRequest.GET("/502"), String)
        } catch (HttpClientResponseException e) {
            response = e.response
        }

        then: "The status code should be 404 if the bug is reproduced, or 502 if fixed"
        println "Received HTTP Status: \${response.status.code} - \${response.status.reason}"

        // If the bug IS reproduced, the actual status will be 404.
        // In this case, 'response.status == HttpStatus.NOT_FOUND' will be true, making the Spock test PASS.
        // The outer Groovy script will interpret a passing test (exit code 0 from gradlew) as bug reproduced (129).
        //
        // If the bug IS NOT reproduced (i.e., fixed), the actual status will be 502.
        // In this case, 'response.status == HttpStatus.NOT_FOUND' will be false, making the Spock test FAIL.
        // The outer Groovy script will interpret a failing test (non-zero exit code from gradlew)
        // AND the 'Received HTTP Status: 502' log as bug NOT reproduced (0).
        response.status == HttpStatus.NOT_FOUND
    }
}
"""

def testDir = new File("test-suite/src/test/groovy/io/micronaut/reproduce")

try {
    testDir.mkdirs()
    def testFile = new File(testDir, "ReproduceIssueSpec.groovy")
    testFile.write(testSource)

    def command = "./gradlew :test-suite:test --tests io.micronaut.reproduce.ReproduceIssueSpec --no-daemon"
    def stdout = new StringBuilder()
    def stderr = new StringBuilder()
    def process = command.execute()
    process.waitForProcessOutput(stdout, stderr)
    def gradleExitCode = process.exitValue()

    def fullOutput = stdout.toString() + stderr.toString()

    println "--- Gradle Test Output Start ---"
    println fullOutput
    println "--- Gradle Test Output End ---"

    if (gradleExitCode == 0) {
        // Spock test passed. This means 'response.status == HttpStatus.NOT_FOUND' was true.
        // Thus, the bug is reproduced as 404 was received instead of 502.
        println ">>> Issue REPRODUCED: Test passed, indicating 404 NOT_FOUND was returned for empty Mono."
        System.exit(129)
    } else {
        // Spock test failed or other Gradle error.
        // Check if the failure was due to receiving 502 (bug fixed).
        if (fullOutput.contains("Received HTTP Status: 502")) {
            // The test failed because it asserted 404, but 502 was correctly returned.
            // This means the bug is NOT reproduced (it's fixed).
            println ">>> Issue NOT REPRODUCED (FIXED): Test failed, but 502 BAD_GATEWAY was correctly returned."
            System.exit(0)
        } else {
            // Test failed for other reasons (e.g., compilation error, server startup failure,
            // or an unexpected status code other than 404 or 502).
            println ">>> UNEXPECTED ERROR: Test failed with Gradle exit code ${gradleExitCode} and unknown status."
            System.exit(1)
        }
    }
} catch (Exception e) {
    e.printStackTrace()
    System.exit(1) // Script error
} finally {
    // Attempt to delete the directory and its contents
    if (testDir.exists()) {
        testDir.deleteDir()
    }
}