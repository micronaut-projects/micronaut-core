package io.micronaut.context

import io.micronaut.core.convert.exceptions.ConversionErrorException
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

    void "test behaviour when failOnConfigConversionError is true"() {
        given:
        def server = ApplicationContext.builder()
                .failOnConfigConversionError(true)
                .properties('test.failing.value': 'not-an-int')
                .start()

        when:
        server.environment.get('test.failing.value', Integer)

        then:
        def ex = thrown(ConversionErrorException)
        ex.message.contains('Failed to convert argument [Integer] for value [not-an-int]')

        cleanup:
        server.close()
    }

    void "test behaviour when failOnConfigConversionError is false"() {
        when:
        def builder = ApplicationContext.builder()
                .properties('test.failing.value': 'not-an-int')
        def server = builder.start()

        then: 'it defaults to false'
        !server.contextConfiguration.isFailOnConfigConversionError()

        when:
        def result = server.environment.get('test.failing.value', Integer)

        then:
        noExceptionThrown()
        !result.present

        cleanup:
        server.close()
    }
}
