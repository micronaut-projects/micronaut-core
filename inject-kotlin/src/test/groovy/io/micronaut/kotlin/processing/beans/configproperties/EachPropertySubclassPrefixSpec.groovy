package io.micronaut.kotlin.processing.beans.configproperties

import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.buildContext

class EachPropertySubclassPrefixSpec extends Specification {

    void "a subclass that redeclares the EachProperty of its superclass binds under the same prefix"() {
        when:
        def context = buildContext('''
package test

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Replaces

@EachProperty("services")
open class ServiceConfig(@Parameter val name: String) {
    var url: String? = null
}

@EachProperty("services")
@Replaces(ServiceConfig::class)
open class ExtendedServiceConfig(@Parameter name: String, val extra: Extra) : ServiceConfig(name) {
    var flavour: String? = null

    @ConfigurationProperties("extra")
    class Extra {
        var level: Int = 0
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
