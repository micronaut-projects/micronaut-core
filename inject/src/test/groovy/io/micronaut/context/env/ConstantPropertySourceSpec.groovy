package io.micronaut.context.env

import io.micronaut.runtime.Micronaut
import spock.lang.Specification

class ConstantPropertySourceSpec extends Specification {
    def "constant property sources are loaded conditionally based on the active environments"() {
        def env = new DefaultEnvironment(Micronaut.build().environments(name)) {
            @Override
            protected List<PropertySource> getConstantPropertySources() {
                [
                        propertySource('application'),
                        propertySource('application-dev'),
                        propertySource('application-cloud'),
                        propertySource('application-other', ['other':'value'])
                ]
            }
        }
        env.start()

        expect:
        def property = env.getProperty("some.conf", String)
        property.present
        property.get() == expectedValue
        !env.getProperty('other', String).present

        cleanup:
        env.stop()

        where:
        name      | expectedValue
        "default" | 'application'
        "dev"     | "application-dev"
        "cloud"   | "application-cloud"

    }

    private static TestPropertySource propertySource(String name, Map<String, String> values = [:]) {
        return new TestPropertySource(name, values)
    }

    private static class TestPropertySource extends MapPropertySource {
        TestPropertySource(String name, Map<String, String> values) {
            super(name, ['some.conf': name] + values)
        }
    }
}
