import groovy.transform.Field

@Field
String testSource = """
package io.micronaut.reproduce

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.RequestScope
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class ReproduceIssueSpec extends Specification {

    // A simple bean to be injected into the request-scoped service
    static class MyRequestBean {
        String value = "test-value"
    }

    // A request-scoped service that uses MyRequestBean
    @RequestScope
    static class MyRequestScopedService {
        MyRequestBean myBean
        MyRequestScopedService(MyRequestBean myBean) {
            this.myBean = myBean
        }
        String getValue() {
            return myBean.value
        }
    }

    // A controller to trigger the creation of the request-scoped service
    @Controller("/my-app")
    static class MyController {
        MyRequestScopedService service

        MyController(MyRequestScopedService service) {
            this.service = service
        }

        @Get("/test")
        String test() {
            return service.value
        }
    }

    void "reproduce NPE in AbstractConcurrentCustomScope after request scope bean destruction"() {
        given: "a Micronaut application with a RequestScoped bean and an endpoint to activate it"
        EmbeddedServer server = null
        HttpClient client = null
        try {
            server = ApplicationContext.builder().start()
            client = server.applicationContext.createBean(HttpClient, server.getURL())

            when: "a request is made that activates the RequestScope and then allows it to be destroyed"
            // Making an HTTP call will activate the RequestScope for the duration of the request.
            // After the response is received, the RequestScope will be torn down and its thread-local
            // context will be cleared.
            client.toBlocking().retrieve("/my-app/test")

            then: "a subsequent attempt to find a bean registration from any custom scope (like HealthIndicator does) should cause an NPE"
            // This call simulates what Micronaut's internal components (e.g., MongoHealthIndicator
            // or any bean lookup that iterates through custom scopes) might do *after* a request
            // has finished.
            // It will eventually call RequestScope.findBeanRegistration, which in turn calls
            // AbstractConcurrentCustomScope.getScopeMap(false). If the request context is null,
            // getScopeMap(false) returns null, leading to the NPE at line 224 when .values() is called on it.
            server.applicationContext.customScopeRegistry.findBeanRegistration(MyRequestBean.class)

            // If the test reaches this line, it means no NullPointerException was thrown,
            // implying the bug is NOT reproduced.
            assert true // Explicitly mark as passed if no exception
        } finally {
            client?.close()
            server?.close()
        }
    }
}
"""

def testDir = new File("test-suite/src/test/groovy/io/micronaut/reproduce")

try {
    testDir.mkdirs()
    def testFile = new File(testDir, "ReproduceIssueSpec.groovy")
    testFile.write(testSource)

    // Command to execute the specific Spock test.
    // Adding --build-cache and --scan for potentially faster and more informative runs.
    def command = "./gradlew :test-suite:test --tests io.micronaut.reproduce.ReproduceIssueSpec --build-cache --scan"
    println "Executing command: $command"

    def process = command.execute()
    process.waitForProcessOutput(System.out, System.err)
    def gradleExitCode = process.exitValue()

    println "Gradle test process exited with code: $gradleExitCode"

    // According to the bug description, the issue is reproduced if a NullPointerException occurs.
    // In a Spock test, if an unexpected exception like NPE is thrown, the test fails, and
    // gradlew will return a non-zero exit code.
    if (gradleExitCode != 0) {
        println "Test failed. This likely indicates the NullPointerException was reproduced."
        System.exit(129) // Issue reproduced
    } else {
        println "Test passed successfully. The NullPointerException was NOT reproduced."
        System.exit(0) // Issue not reproduced
    }
} catch (Exception e) {
    e.printStackTrace()
    System.exit(1) // Script error (e.g., file writing error, command execution error)
} finally {
    // Clean up: delete the created test file and directory
    def testFile = new File(testDir, "ReproduceIssueSpec.groovy")
    if (testFile.exists()) {
        testFile.delete()
    }
    if (testDir.exists()) {
        testDir.deleteDir()
    }
}