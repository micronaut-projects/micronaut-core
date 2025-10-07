import groovy.transform.Field

@Field
String testSource = """
package io.micronaut.reproduce

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Refreshable
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import spock.lang.Specification

class ReproduceIssueSpec extends Specification {

    // Define the configuration class that uses a camelCase name in @ConfigurationProperties
    @ConfigurationProperties("myCamelCaseConfig") // Annotated with camelCase
    @Refreshable // Make it refreshable
    static class MyCamelCaseConfig {
        String myProperty
    }

    void "test @ConfigurationProperties and @Refreshable with camelCase annotated name does not refresh from kebab-case property source"() {
        given: "an initial configuration with kebab-case property key that maps to the camelCase annotated bean name"
        def initialProperties = [
            "my-camel-case-config.my-property": "initialValue"
        ]

        def context = ApplicationContext.run(initialProperties)
        def environment = context.environment

        def configBean = context.getBean(MyCamelCaseConfig)

        expect: "the bean is initially loaded correctly from the kebab-case property"
        configBean.myProperty == "initialValue"

        when: "the configuration is changed with new kebab-case values and refresh is triggered"
        def updatedProperties = [
            "my-camel-case-config.my-property": "updatedValue"
        ]
        // Add a new property source with higher precedence to simulate a change in configuration
        environment.addPropertySource(PropertySource.of("test-update", updatedProperties))

        // Trigger the refresh mechanism for @Refreshable beans
        def diff = environment.refreshAndDiff()

        then: "the environment should detect the change for the kebab-case property"
        // Verify that the environment itself registers the property change.
        def changedPropertyNames = diff.getChanged().collect { it.name }
        changedPropertyNames.contains("my-camel-case-config.my-property")

        and: "the config bean's property should NOT be updated if the bug is reproduced, otherwise it is fixed"
        if (configBean.myProperty == "initialValue") {
            // If the property remains "initialValue", it means the refresh did not occur as expected
            // for the camelCase-named @ConfigurationProperties bean.
            // This indicates the bug is reproduced. Throw an AssertionError to make the Gradle test task fail.
            throw new AssertionError("BUG REPRODUCED: Property 'myCamelCaseConfig.myProperty' did not refresh from 'my-camel-case-config.my-property'. Expected 'updatedValue' but got 'initialValue'.")
        } else {
            // If the property updated to "updatedValue", it means the bug is fixed.
            // The test passes in this case, indicating a fix.
            configBean.myProperty == "updatedValue"
        }

        cleanup:
        context.close()
    }
}
"""

def testDir = new File("test-suite/src/test/groovy/io/micronaut/reproduce")
def testFile = new File(testDir, "ReproduceIssueSpec.groovy")

try {
    testDir.mkdirs()
    testFile.write(testSource)

    // Command to execute the specific Groovy test using Gradle
    def command = "./gradlew :test-suite:test --tests io.micronaut.reproduce.ReproduceIssueSpec"
    def process = command.execute()
    process.waitForProcessOutput(System.out, System.err)
    def gradleExitCode = process.exitValue()

    // Map Gradle's exit code to the required script exit codes:
    // If Gradle test fails (non-zero exit code), it means the bug was reproduced (AssertionError thrown).
    // If Gradle test passes (zero exit code), it means the bug was fixed.
    if (gradleExitCode != 0) {
        System.exit(129) // Issue reproduced
    } else {
        System.exit(0)   // Issue not reproduced (fixed)
    }
} catch (Exception e) {
    e.printStackTrace()
    System.exit(1) // Script error (e.g., file operations, command execution issues)
} finally {
    // Clean up the created test file and directory
    if (testFile.exists()) {
        testFile.delete()
    }
    if (testDir.exists()) {
        testDir.deleteDir()
    }
}