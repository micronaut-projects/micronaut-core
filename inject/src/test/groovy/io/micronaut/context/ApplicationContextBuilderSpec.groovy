package io.micronaut.context

import spock.lang.Specification

class ApplicationContextBuilderSpec extends Specification {

    void "test context configuration"() {
        given:
        ApplicationContextBuilder builder = ApplicationContext.builder()
        def loader = new GroovyClassLoader()
        builder.classLoader(loader)
               .environments("foo")
               .deduceEnvironment(false)


        ApplicationContextConfiguration config = (ApplicationContextConfiguration) builder

        expect:
        config.classLoader.is(loader)
        config.resourceLoader.classLoader.is(loader)
        config.environments.contains('foo')
        config.deduceEnvironments.get() == false

    }
}
