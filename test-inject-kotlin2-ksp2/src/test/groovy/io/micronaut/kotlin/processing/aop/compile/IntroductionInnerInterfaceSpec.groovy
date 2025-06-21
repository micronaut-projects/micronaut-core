package io.micronaut.kotlin.processing.aop.compile

import io.micronaut.aop.Intercepted
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class IntroductionInnerInterfaceSpec extends Specification {

    void 'test that an inner interface with introduction doesnt create advise for outer class'() {
        given:
        def clsName = 'inneritfce.Test'
        def context = buildContext('''
package inneritfce

import jakarta.inject.Singleton
import io.micronaut.kotlin.processing.aop.introduction.Stub

@Singleton
class Test {

    @Stub
    interface InnerIntroduction
}
''')
        when:
        def bean = getBean(context, clsName)

        then:'outer bean is not AOP advice'
        !(bean instanceof Intercepted)

        when:
        context.classLoader.loadClass(clsName + BeanDefinitionVisitor.PROXY_SUFFIX)

        then:'proxy not generated for outer type'
        thrown(ClassNotFoundException)
    }
}
