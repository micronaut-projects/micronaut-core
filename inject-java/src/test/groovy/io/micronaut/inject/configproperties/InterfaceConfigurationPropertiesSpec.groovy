package io.micronaut.inject.configproperties

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory
import io.micronaut.inject.ValidatedBeanDefinition

class InterfaceConfigurationPropertiesSpec extends AbstractTypeElementSpec {


    void "test simple interface config props"() {

        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("foo.bar")
interface MyConfig {
    @javax.validation.constraints.NotBlank
    String getHost();
    int getServerPort();
}

''')
        then:
        beanDefinition instanceof ValidatedBeanDefinition

        when:
        def context = ApplicationContext.run('foo.bar.host': 'test', 'foo.bar.server-port': '9999')
        def config = ((BeanFactory) beanDefinition).build(context, beanDefinition)

        then:
        config.host == 'test'
        config.serverPort == 9999

        cleanup:
        context.close()

    }
}
