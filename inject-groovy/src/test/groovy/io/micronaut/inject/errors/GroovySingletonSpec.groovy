package io.micronaut.inject.errors

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.context.DefaultBeanContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory
import io.micronaut.inject.qualifiers.One

class GroovySingletonSpec extends AbstractBeanDefinitionSpec {

    void "test that compilation fails if injection visited on Groovy singleton"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('io.micronaut.inject.property.MyBean', '''
package io.micronaut.inject.property;

import io.micronaut.inject.qualifiers.*

@Singleton
class MyBean  {
    @javax.inject.Inject
    @javax.annotation.Nullable
    AnotherBean injected
}

@javax.inject.Singleton
class AnotherBean {
    
}
''')
        then:
        def e = thrown(Exception)
        e.message.contains("Class annotated with groovy.lang.Singleton instead of javax.inject.Singleton. Import javax.inject.Singleton to use Micronaut Dependency Injection")

    }

}
