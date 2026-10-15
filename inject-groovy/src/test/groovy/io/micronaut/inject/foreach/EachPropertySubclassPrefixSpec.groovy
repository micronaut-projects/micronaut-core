package io.micronaut.inject.foreach

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.inject.qualifiers.Qualifiers

class EachPropertySubclassPrefixSpec extends AbstractBeanDefinitionSpec {

    void "a subclass that redeclares the EachProperty of its superclass binds under the same prefix"() {
        when:
        def context = buildContext('''
package test

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Replaces
import jakarta.inject.Inject

@EachProperty("services")
class ServiceConfig {
    final String name
    String url

    ServiceConfig(@Parameter String name) {
        this.name = name
    }
}

@EachProperty("services")
@Replaces(ServiceConfig)
class ExtendedServiceConfig extends ServiceConfig {
    final Extra extra
    String flavour

    @Inject
    ExtendedServiceConfig(@Parameter String name, Extra extra) {
        super(name)
        this.extra = extra
    }

    @ConfigurationProperties("extra")
    static class Extra {
        int level
    }
}
''', false, [
                'services.one.url'        : 'http://one',
                'services.one.flavour'    : 'vanilla',
                'services.one.extra.level': 3,
        ])
        def type = context.classLoader.loadClass('test.ServiceConfig')
        def bean = context.getBean(type, Qualifiers.byName('one'))

        then:
        bean.class.name == 'test.ExtendedServiceConfig'
        bean.url == 'http://one'
        bean.flavour == 'vanilla'
        bean.extra.level == 3
        context.getBeanDefinition(context.classLoader.loadClass('test.ExtendedServiceConfig'))
                .stringValue(ConfigurationReader, ConfigurationReader.PREFIX).get() == 'services.*'

        cleanup:
        context.close()
    }
}
