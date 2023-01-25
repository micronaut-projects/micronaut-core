package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext

class IntroductionCompileSpec extends AbstractTypeElementSpec {

    void "test inherited default methods are not overridden"() {
        given:
        ApplicationContext context = buildContext('''
package introductiontest;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import jakarta.inject.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


@TestAnn
interface MyBean extends Parent {

    int test();

    default String getName() {
        return "my-bean";
    }

    @Override
    default String getDescription() {
        return "description";
    }
}

interface Parent {
    default String getParentName() {
        return "parent";
    }

    String getDescription();
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
        instance.name == 'my-bean'
        instance.description == 'description'
        instance.parentName == 'parent'
        result == 10
        interceptor.invoked == 1
    }

    void "test inherited default or abstract methods are not overridden"() {
        given:
        ApplicationContext context = buildContext('''
package introductiontest;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import jakarta.inject.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@TestAnn
abstract class MyBean extends Parent implements MyInterface {

    abstract int test();

    public String getName() {
        return "my-bean";
    }

    @Override
    public String getDescription() {
        return "description";
    }
}

abstract class Parent {
    public String getParentName() {
        return "parent";
    }

    abstract String getDescription();
}

interface MyInterface {

    String getDescription();
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
        instance.name == 'my-bean'
        instance.description == 'description'
        instance.parentName == 'parent'
        result == 10
        interceptor.invoked == 1
    }

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
