import groovy.transform.Field

@Field
String testSource = """
package io.micronaut.reproduce

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Singleton
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.runtime.ApplicationConfiguration
import spock.lang.Specification
import org.codehaus.groovy.control.CompilationFailedException
import java.lang.reflect.Modifier
import io.micronaut.context.exceptions.BeanInstantiationException

class ReproduceIssueSpec extends Specification {

    void "test HttpClientConfiguration requires implementation of getConnectionPoolConfiguration"() {
        setup:
        ApplicationContext applicationContext = null
        Throwable caughtException = null
        boolean bugDetected = false // Renamed for clarity: is the bug *detected* by our test logic?

        when: "Attempting to compile and instantiate a class extending HttpClientConfiguration without implementing the abstract method within Micronaut context"
        try {
            // Use the current test's classloader to ensure Micronaut dependencies are available for compilation
            GroovyClassLoader classLoader = new GroovyClassLoader(Thread.currentThread().contextClassLoader)
            Class<?> dynamicClass = classLoader.parseClass(\"\"\"
                package io.micronaut.reproduce

                import io.micronaut.context.annotation.Singleton
                import io.micronaut.http.client.HttpClientConfiguration
                import io.micronaut.runtime.ApplicationConfiguration
                import io.micronaut.http.client.HttpClientConfiguration.ConnectionPoolConfiguration

                @Singleton
                class TempFaultyHttpClientConfiguration extends HttpClientConfiguration {
                   public TempFaultyHttpClientConfiguration(ApplicationConfiguration configuration) {
                        super(configuration)
                    }
                    // Intentionally omitting: @Override public ConnectionPoolConfiguration getConnectionPoolConfiguration() { return new ConnectionPoolConfiguration() }
                }
            \"\"\")

            println "--- Dynamic Class Compilation Info (if no CompilationFailedException) ---"
            println "Class Name: " + dynamicClass.getName()
            println "Is Abstract: " + Modifier.isAbstract(dynamicClass.getModifiers())
            println "------------------------------------------------------------------------"


            applicationContext = ApplicationContext.builder().start()
            println "Attempting to get bean of " + dynamicClass.getName() + " from Micronaut ApplicationContext..."
            applicationContext.getBean(dynamicClass)
            println "SUCCESS: Bean was created without error. This indicates the bug is NOT reproduced."

        } catch (CompilationFailedException e) {
            caughtException = e
            println "Caught CompilationFailedException: " + e.class.simpleName + ": " + e.message
            // This is a direct compilation failure for the class within GroovyClassLoader
            if (e.message.contains("is not abstract") && e.message.contains("getConnectionPoolConfiguration")) {
                bugDetected = true
            }
        } catch (BeanInstantiationException e) { // Micronaut specific exception for bean creation failures
            caughtException = e
            println "Caught BeanInstantiationException: " + e.class.simpleName + ": " + e.message
            // Look for nested InstantiationError or similar root cause
            Throwable rootCause = e.cause
            while (rootCause != null && !(rootCause instanceof InstantiationError)) {
                rootCause = rootCause.cause
            }
            if (rootCause instanceof InstantiationError) {
                println "  -> Root cause is InstantiationError: " + rootCause.message
                if (rootCause.message.contains("abstract class") || rootCause.message.contains("TempFaultyHttpClientConfiguration")) {
                    bugDetected = true
                }
            } else if (e.message?.contains("abstract method") || e.message?.contains("HttpClientConfiguration") || e.message?.contains("must be abstract")) {
                // Also check BeanInstantiationException message directly for common patterns
                bugDetected = true
            }
        } catch (InstantiationError e) { // Fallback if not wrapped by BeanInstantiationException
            caughtException = e
            println "Caught InstantiationError directly: " + e.class.simpleName + ": " + e.message
            if (e.message.contains("abstract class") || e.message.contains("TempFaultyHttpClientConfiguration")) {
                bugDetected = true
            }
        } catch (Exception e) { // Catch any other unexpected exceptions
            caughtException = e
            println "Caught unexpected general Exception: " + e.class.simpleName + ": " + e.message
            e.printStackTrace()
            // Generic check for messages related to abstract methods/classes if not caught by more specific handlers
            if (e.message?.contains("abstract method") || e.message?.contains("HttpClientConfiguration") || e.message?.contains("must be abstract")) {
                bugDetected = true
            }
        } finally {
            applicationContext?.close()
        }

        then: "The bug should be reproduced by either a compilation error or an instantiation error, leading to `bugDetected = true`"
        // If bug is reproduced, `bugDetected` should be true.
        // We want the Spock test to FAIL if the bug IS reproduced, and PASS if it's NOT.
        // This is the standard behavior for test cases that ensure a bug is fixed.
        // So, if `bugDetected` is true (bug found), we assert false, making the test fail.
        !bugDetected
    }
}
"""

def testDir = new File("test-suite/src/test/groovy/io/micronaut/reproduce")

try {
    testDir.mkdirs()
    def testFile = new File(testDir, "ReproduceIssueSpec.groovy")
    testFile.write(testSource)

    // Add clean step to ensure fresh compilation
    println "Running clean tasks..."
    def cleanCommand = "./gradlew clean :test-suite:cleanTest"
    def cleanProcess = cleanCommand.execute()
    cleanProcess.waitForProcessOutput(System.out, System.err)
    if (cleanProcess.exitValue() != 0) {
        System.err.println("Clean tasks failed. Exiting with 1.")
        System.exit(1)
    }

    // Ensure we are on the specific version if not already
    println "Checking out git version v4.8.0..."
    def gitCheckoutCommand = "git checkout v4.8.0"
    def gitProcess = gitCheckoutCommand.execute()
    gitProcess.waitForProcessOutput(System.out, System.err)
    if (gitProcess.exitValue() != 0) {
        System.err.println("Failed to checkout v4.8.0. Exiting with 1.")
        System.exit(1)
    }

    println "Running test command: ./gradlew :test-suite:test --tests io.micronaut.reproduce.ReproduceIssueSpec"
    def command = "./gradlew :test-suite:test --tests io.micronaut.reproduce.ReproduceIssueSpec"
    def process = command.execute()
    process.waitForProcessOutput(System.out, System.err)
    def gradleExitCode = process.exitValue()

    println "Gradle test task exited with code: " + gradleExitCode

    // Restore original branch to avoid detached HEAD state and clean up working directory
    println "Restoring git working directory..."
    def gitRestoreCommand = "git restore ."
    def gitRestoreProcess = gitRestoreCommand.execute()
    gitRestoreProcess.waitFor()
    if (gitRestoreProcess.exitValue() != 0) {
        System.err.println("Failed to restore git working directory.")
    }

    // Interpret Gradle's exit code
    if (gradleExitCode == 1) { // Gradle returned 1, meaning the test failed. This means bug was detected by !bugDetected
        println "Test failed, indicating bug reproduced."
        System.exit(129) // Issue reproduced
    } else if (gradleExitCode == 0) { // Gradle returned 0, meaning the test passed. This means bug was NOT detected by !bugDetected
        println "Test passed, indicating bug NOT reproduced (fixed)."
        System.exit(0) // Issue not reproduced (fixed)
    } else {
        println "Unexpected Gradle exit code: " + gradleExitCode
        System.exit(1) // Other error
    }
} catch (Exception e) {
    e.printStackTrace()
    System.exit(1) // Script error
} finally {
    // Clean up the created test directory and file
    if (testDir.exists()) {
        println "Cleaning up test directory: " + testDir.absolutePath
        testDir.deleteDir()
    }
}