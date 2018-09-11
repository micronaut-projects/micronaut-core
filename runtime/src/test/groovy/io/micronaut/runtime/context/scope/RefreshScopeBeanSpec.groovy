package io.micronaut.runtime.context.scope

import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.inject.BeanDefinition
import io.micronaut.support.AbstractBeanDefinitionSpec

import javax.inject.Scope

class RefreshScopeBeanSpec extends AbstractBeanDefinitionSpec {

    void "test configuration properties path"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig', '''
package test;

import io.micronaut.context.annotation.*

@ConfigurationProperties('foo')
class MyConfig {
    String bar
}
''')

        then:
        beanDefinition.getValue(ConfigurationReader, "prefix", String).get() ==  'foo'
    }
}
