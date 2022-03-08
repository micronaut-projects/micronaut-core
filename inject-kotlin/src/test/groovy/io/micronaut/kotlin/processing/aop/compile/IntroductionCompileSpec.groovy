package io.micronaut.kotlin.processing.aop.compile

import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext
import spock.lang.Specification

import static io.micronaut.kotlin.processing.KotlinCompiler.*

class IntroductionCompileSpec extends Specification {

    void 'test apply introduction advise with interceptor binding'() {
        given:
        ApplicationContext context = buildContext('''
package introductiontest

import io.micronaut.aop.*
import jakarta.inject.Singleton

@TestAnn
interface MyBean {   
    fun test(): Int
}

@Retention
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Introduction
annotation class TestAnn

@InterceptorBean(TestAnn::class)
class StubIntroduction: Interceptor<Any, Any> {
    var invoked = 0
    override fun intercept(context: InvocationContext<Any, Any>): Any {
        invoked++
        return 10
    }
} 
''')
        def instance = getBean(context, 'introductiontest.MyBean')
        def interceptor = getBean(context, 'introductiontest.StubIntroduction')

        when:
        def result = instance.test()

        then:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked == 1
        result == 10
    }
}
