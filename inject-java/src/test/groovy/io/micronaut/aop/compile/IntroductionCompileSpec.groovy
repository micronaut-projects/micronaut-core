package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext

class IntroductionCompileSpec extends AbstractTypeElementSpec {
    void 'test apply introduction advise with interceptor binding'() {
        given:
        ApplicationContext context = buildContext('''
package introductiontest;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import jakarta.inject.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


@TestAnn
interface MyBean {
    
    int test();
}

@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Introduction
@interface TestAnn {
}

@InterceptorBean(TestAnn.class)
class StubIntroduction implements Interceptor {
    int invoked = 0;
    @Override
    public Object intercept(InvocationContext context) {
        invoked++;
        return 10;
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
