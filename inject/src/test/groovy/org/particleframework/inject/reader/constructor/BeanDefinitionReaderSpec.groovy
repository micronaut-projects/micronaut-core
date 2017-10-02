package org.particleframework.inject.reader.constructor

import org.particleframework.inject.BeanDefinition
import org.particleframework.inject.reader.BeanDefinitionReader
import spock.lang.Ignore
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Named

/**
 * Created by graemerocher on 26/05/2017.
 */
@Ignore
class BeanDefinitionReaderSpec extends Specification {

    void "test read simple constructor def"() {
        given:
        BeanDefinitionReader reader = new BeanDefinitionReader(getClass().getClassLoader())

        when:
        BeanDefinition<B> definition = reader.readBeanDefinition(B)

        then:
        definition != null
        definition.constructor.arguments.size() == 1
    }

    void "test read simple constructor with qualifier"() {
        given:
        BeanDefinitionReader reader = new BeanDefinitionReader(getClass().getClassLoader())

        when:
        BeanDefinition<B> definition = reader.readBeanDefinition(B2)

        then:
        definition != null
        definition.constructor.arguments.size() == 1
        definition.constructor.arguments[0].qualifier != null
    }


//    void "test read simple constructor with generic types"() {
//        given:
//        BeanDefinitionReader reader = new BeanDefinitionReader(getClass().getClassLoader())
//
//        when:
//        BeanDefinition<B> definition = reader.readBeanDefinition(B3)
//
//        then:
//        definition != null
//        definition.constructor.arguments.size() == 1
//        definition.constructor.arguments[0].genericTypes != null
//        definition.constructor.arguments[0].genericTypes[0]== A
//
//    }

    static class A {}

    static class B {
        @Inject B(A a) {}
    }

    static class B2 {
        @Inject B2(@Named("a") A a) {}
    }

    static class B3 {
        @Inject B3(List<A> a) {}
    }
}
