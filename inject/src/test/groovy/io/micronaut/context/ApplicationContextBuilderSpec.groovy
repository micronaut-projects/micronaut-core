package io.micronaut.context

import io.micronaut.context.env.PropertySource
import spock.lang.Specification

class ApplicationContextBuilderSpec extends Specification {

    void "test disable default property sources"() {
        given:
        ApplicationContextBuilder builder = ApplicationContext.builder()
        builder.enableDefaultPropertySources(false)
            .propertySources(PropertySource.of("custom", [foo:'bar']))
        when:
        def ctx = builder.build().start()

        then:
        ctx.environment.propertySources.size() == 1
        ctx.environment.propertySources.first().name == 'custom'

        cleanup:
        ctx.close()
    }

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
