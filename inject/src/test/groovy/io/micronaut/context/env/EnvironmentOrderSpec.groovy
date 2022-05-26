package io.micronaut.context.env

import io.micronaut.core.version.SemanticVersion
import spock.lang.Requires
import spock.lang.Specification
import spock.util.environment.Jvm

// fails due to https://issues.apache.org/jira/browse/GROOVY-10145
@Requires({
    SemanticVersion.isAtLeastMajorMinor(GroovySystem.version, 4, 0) ||
            !Jvm.current.isJava16Compatible()
})
class EnvironmentOrderSpec extends Specification {

    void "test the last environment has priority"() {
        Environment env = new DefaultEnvironment({ ["first", "second"] }).start()

        expect:
        env.getRequiredProperty("environment.order", String) == "second"

        cleanup:
        env.close()
    }

    void "test the last environment has priority 2"() {
        Environment env = new DefaultEnvironment({ ["second", "first"] }).start()

        expect:
        env.getRequiredProperty("environment.order", String) == "first"

        cleanup:
        env.close()
    }
}
