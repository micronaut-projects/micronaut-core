package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.InterceptorBinding
import io.micronaut.aop.InterceptorKind
import io.micronaut.aop.simple.Mutating
import io.micronaut.aop.simple.TestBinding
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.AdvisedBeanType
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference

class AroundCompileSpec extends AbstractTypeElementSpec {

    void 'test byte[] return compile'() {
        given:
        ApplicationContext context = buildContext('''
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
        def instance = getBean(context, 'test.MyBean')
        expect:
        instance != null

        cleanup:
        context.close()
    }

    void 'compile simple AOP advice'() {
        given:
        BeanDefinition beanDefinition = buildInterceptedBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.aop.simple.*;

@javax.inject.Singleton
@Mutating("someVal")
@TestBinding
class MyBean {
    void test() {};
}
''')

        BeanDefinitionReference ref = buildInterceptedBeanDefinitionReference('test.MyBean', '''
package test;

import io.micronaut.aop.simple.*;

@javax.inject.Singleton
@Mutating("someVal")
@TestBinding
class MyBean {
    void test() {};
}
''')

        def annotationMetadata = beanDefinition?.annotationMetadata
        def values = annotationMetadata.getAnnotationValuesByType(InterceptorBinding)

        expect:
        values.size() == 2
        values[0].stringValue().get() == Mutating.name
        values[0].enumValue("kind", InterceptorKind).get() == InterceptorKind.AROUND
        values[0].classValue("interceptorType").isPresent()
        values[1].stringValue().get() == TestBinding.name
        !values[1].classValue("interceptorType").isPresent()
        values[1].enumValue("kind", InterceptorKind).get() == InterceptorKind.AROUND
        beanDefinition != null
        beanDefinition instanceof AdvisedBeanType
        beanDefinition.interceptedType.name == 'test.MyBean'
        ref in AdvisedBeanType
        ref.interceptedType.name == 'test.MyBean'
    }
}
