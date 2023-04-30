package io.micronaut.inject.configproperties

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

class ConfigurationPropertiesInjectSpec extends AbstractTypeElementSpec {

    void "test @ConfigurationInject constructor with beans and other configs"() {
        given:
        when:
        def context = ApplicationContext.run(['spec': getClass().getSimpleName(), 'foo.bar.host': 'test', 'foo.bar.server-port': '123', 'xyz.name': "33"])
        def config = context.getBean(MyConfigWithConstructorConfigurationInject)

        then:
        config.host == 'test'
        config.serverPort == 123
        config.otherBean
        config.otherConfig.name == "33"
        config.otherMissingConfig
        config.otherSingleton
        config.optionalOtherSingleton.get()
        config.otherSingletonBeanProvider.get() == config.otherSingleton
        config.otherSingletonProvider.get() == config.otherSingleton

        cleanup:
        context.close()
    }

    void "test @ConfigurationInject method with beans and other configs"() {
        given:
        when:
        def context = ApplicationContext.run(['spec': getClass().getSimpleName(), 'foo.bar.host': 'test', 'foo.bar.server-port': '123', 'xyz.name': "33"])
        def config = context.getBean(MyConfigWithMethodConfigurationInject)

        then:
        config.host == 'test'
        config.serverPort == 123
        config.otherBean
        config.otherConfig.name == "33"
        config.otherMissingConfig
        config.otherSingleton
        config.optionalOtherSingleton.get()
        config.otherSingletonBeanProvider.get() == config.otherSingleton
        config.otherSingletonProvider.get() == config.otherSingleton

        cleanup:
        context.close()
    }
}
