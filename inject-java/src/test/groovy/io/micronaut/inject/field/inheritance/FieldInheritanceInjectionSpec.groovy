package io.micronaut.inject.field.inheritance

import io.micronaut.context.ApplicationContext
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.writer.BeanDefinitionWriter

class FieldInheritanceInjectionSpec extends AbstractTypeElementSpec {

    void "test injecting into super abstract class"() {
        when:
        ApplicationContext context = buildContext('test.Listener', '''
package test;

import jakarta.inject.Singleton;
import jakarta.inject.Inject;

abstract class AbstractListener  {
@Inject protected SomeBean someBean;
}

@Singleton
class SomeBean {
}

@Singleton
class Listener extends AbstractListener {
}
''')

        then:
        noExceptionThrown()

        when:
        context.classLoader.loadClass('test.$AbstractListener' + BeanDefinitionWriter.CLASS_SUFFIX)

        then:
        thrown(ClassNotFoundException)

        when:
        Class clazz = context.classLoader.loadClass('test.Listener')
        Object bean = context.getBean(clazz)

        then:
        noExceptionThrown()
        bean.someBean != null
    }
}
