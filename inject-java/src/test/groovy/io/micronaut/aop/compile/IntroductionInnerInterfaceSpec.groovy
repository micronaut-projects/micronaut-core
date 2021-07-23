package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.writer.BeanDefinitionVisitor

class IntroductionInnerInterfaceSpec extends AbstractTypeElementSpec {

    void 'test that an inner interface with introduction doesnt create advise for outer class'() {

        given:
        def clsName = 'inneritfce.Test'
        def context = buildContext(clsName, '''
package inneritfce;

import jakarta.inject.*;
import io.micronaut.aop.introduction.*;

@Singleton
class Test {

    @Stub    
    interface InnerIntroduction {
    }
}
''')
        when:
        def bean = context.getBean(context.classLoader.loadClass(clsName))

        then:'outer bean is not AOP advice'
        !(bean instanceof Intercepted)

        when:
        context.classLoader.loadClass(clsName + BeanDefinitionVisitor.PROXY_SUFFIX)

        then:'proxy not generated for outer type'
        thrown(ClassNotFoundException)
    }
}
