package io.micronaut.inject.configproperties

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory

class ConfigPropertiesParseSpec extends AbstractBeanDefinitionSpec {

    void "test configuration properties returns self"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties("my")
class MyConfig {
    private String host
    String getHost() {
        host
    }
    MyConfig setHost(String host) {
        this.host = host
        this
    }
}''')
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.build(["my.host": "abc"]).start()
        def bean = factory.build(applicationContext, beanDefinition)

        then:
        bean.getHost() == "abc"
    }
}
