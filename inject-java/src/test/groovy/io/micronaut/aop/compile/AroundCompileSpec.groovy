package io.micronaut.aop.compile

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.AdvisedBeanType
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference

class AroundCompileSpec extends AbstractTypeElementSpec {

    void 'test byte[] return compile'() {
        given:
        ApplicationContext context = buildContext('test.$MyBeanDefinition$Intercepted', '''
package test;

import io.micronaut.aop.proxytarget.*;

@javax.inject.Singleton
@Mutating("someVal")
class MyBean {
    byte[] test(byte[] someVal) {
        return null;
    };
}
''')
        def instance = context.getBean(context.classLoader.loadClass('test.MyBean'))
        expect:
        instance != null
    }

    void 'compile simple AOP advice'() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.$MyBeanDefinition$Intercepted', '''
package test;

import io.micronaut.aop.simple.*;

@javax.inject.Singleton
@Mutating("someVal")
class MyBean {
    void test() {};
}
''')

        BeanDefinitionReference ref = buildBeanDefinitionReference('test.$MyBeanDefinition$Intercepted', '''
package test;

import io.micronaut.aop.simple.*;

@javax.inject.Singleton
@Mutating("someVal")
class MyBean {
    void test() {};
}
''')

        expect:
        beanDefinition != null
        beanDefinition instanceof AdvisedBeanType
        beanDefinition.interceptedType.name == 'test.MyBean'
        ref in AdvisedBeanType
        ref.interceptedType.name == 'test.MyBean'
    }

    void "test validated on class with generics"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.$BaseEntityServiceDefinition$Intercepted', """
package test;

@io.micronaut.validation.Validated
class BaseEntityService<T extends BaseEntity> extends BaseService<T> {
}

class BaseEntity {}
abstract class BaseService<T> implements IBeanValidator<T> {
    public boolean isValid(T entity) {
        return true;
    }
}
interface IBeanValidator<T> {
    boolean isValid(T entity);
}
""")

        then:
        noExceptionThrown()
        beanDefinition != null
        beanDefinition.getTypeArguments('test.BaseService')[0].type.name == 'test.BaseEntity'
    }
}
