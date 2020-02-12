package io.micronaut.inject.field.inheritance

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.AbstractTypeElementSpec

class FieldInheritanceInjectionSpec extends AbstractTypeElementSpec {

    void "test injecting into super abstract class"() {
        when:
        ApplicationContext context = buildContext('test.Listener', '''
package test;

import javax.inject.Singleton;
import javax.inject.Inject;

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
        context.classLoader.loadClass('test.$AbstractListenerDefinition')

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
