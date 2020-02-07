package io.micronaut.context.env

import spock.lang.Specification

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
