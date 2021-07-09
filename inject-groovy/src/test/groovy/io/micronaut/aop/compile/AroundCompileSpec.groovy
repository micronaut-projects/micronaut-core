package io.micronaut.aop.compile

import io.micronaut.aop.Intercepted
import io.micronaut.aop.InterceptorBinding
import io.micronaut.aop.InterceptorKind
import io.micronaut.aop.interceptors.Mutating
import io.micronaut.aop.simple.TestBinding
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.AdvisedBeanType
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.annotation.NamedAnnotationMapper
import io.micronaut.inject.visitor.VisitorContext
import spock.lang.Issue

import java.lang.annotation.Annotation

class AroundCompileSpec extends AbstractBeanDefinitionSpec {

    void 'test apply interceptor binder with annotation mapper'() {
        given:
        ApplicationContext context = buildContext('''
package mapperbinding;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import javax.inject.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Singleton
class MyBean {
    @TestAnn
    void test() {
        
    }
    
}

@Retention(RUNTIME)
@Target([ElementType.METHOD, ElementType.TYPE])
@interface TestAnn {
}

@InterceptorBean(TestAnn.class)
class TestInterceptor implements Interceptor {
    boolean invoked = false;
    @Override
    public Object intercept(InvocationContext context) {
        invoked = true;
        return context.proceed();
    }
} 

''')
        def instance = getBean(context, 'mapperbinding.MyBean')
        def interceptor = getBean(context, 'mapperbinding.TestInterceptor')

        when:
        instance.test()

        then:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked

    }

    void 'test method level interceptor matching'() {
        given:
        ApplicationContext context = buildContext('''
package annbinding2;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import javax.inject.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import io.micronaut.aop.simple.*;

@Singleton
class MyBean {
    @TestAnn
    void test() {
        
    }
    
    @TestAnn2
    void test2() {
        
    }
}

@Retention(RUNTIME)
@Target([ElementType.METHOD, ElementType.TYPE])
@Around
@interface TestAnn {
}

@Retention(RUNTIME)
@Target([ElementType.METHOD, ElementType.TYPE])
@Around
@interface TestAnn2 {
}

@InterceptorBean(TestAnn.class)
class TestInterceptor implements Interceptor {
    boolean invoked = false;
    @Override
    public Object intercept(InvocationContext context) {
        invoked = true;
        return context.proceed();
    }
} 

@InterceptorBean(TestAnn2.class)
class AnotherInterceptor implements Interceptor {
    boolean invoked = false;
    @Override
    public Object intercept(InvocationContext context) {
        invoked = true;
        return context.proceed();
    }
} 
''')
        def instance = getBean(context, 'annbinding2.MyBean')
        def interceptor = getBean(context, 'annbinding2.TestInterceptor')
        def anotherInterceptor = getBean(context, 'annbinding2.AnotherInterceptor')

        when:
        instance.test()

        then:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked
        !anotherInterceptor.invoked

        when:
        instance.test2()

        then:
        anotherInterceptor.invoked


        cleanup:
        context.close()
    }

    void 'test annotation with just interceptor binding'() {
        given:
        ApplicationContext context = buildContext('''
package annbinding1;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import javax.inject.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Singleton
@TestAnn
class MyBean {
    void test() {
    }
}

@Retention(RUNTIME)
@Target([ElementType.METHOD, ElementType.TYPE])
@Around
@interface TestAnn {
}

@InterceptorBean(TestAnn.class)
class TestInterceptor implements Interceptor {
    boolean invoked = false;
    @Override
    public Object intercept(InvocationContext context) {
        invoked = true;
        return context.proceed();
    }
} 

@Singleton
class AnotherInterceptor implements Interceptor {
    boolean invoked = false;
    @Override
    public Object intercept(InvocationContext context) {
        invoked = true;
        return context.proceed();
    }
} 
''')
        def instance = getBean(context, 'annbinding1.MyBean')
        def interceptor = getBean(context, 'annbinding1.TestInterceptor')
        def anotherInterceptor = getBean(context, 'annbinding1.AnotherInterceptor')
        instance.test()

        expect:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked
        !anotherInterceptor.invoked

        cleanup:
        context.close()
    }

    void 'test annotation with just around'() {
        given:
        ApplicationContext context = buildContext('''
package justaround;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import javax.inject.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Singleton
@TestAnn
class MyBean {
    void test() {
    }
}

@Retention(RUNTIME)
@Target([ElementType.METHOD, ElementType.TYPE])
@Around
@interface TestAnn {
}

@InterceptorBean(TestAnn.class)
class TestInterceptor implements Interceptor {
    boolean invoked = false;
    @Override
    public Object intercept(InvocationContext context) {
        invoked = true;
        return context.proceed();
    }
} 

@Singleton
class AnotherInterceptor implements Interceptor {
    boolean invoked = false;
    @Override
    public Object intercept(InvocationContext context) {
        invoked = true;
        return context.proceed();
    }
} 
''')
        def instance = getBean(context, 'justaround.MyBean')
        def interceptor = getBean(context, 'justaround.TestInterceptor')
        def anotherInterceptor = getBean(context, 'justaround.AnotherInterceptor')
        instance.test()

        expect:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked
        !anotherInterceptor.invoked

        cleanup:
        context.close()
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/5522')
    void 'test Around annotation on private method fails'() {
        when:
        buildContext('''
package around.priv.method;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import javax.inject.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Singleton
class MyBean {
    @TestAnn
    private void foobar() {
    }
}

@Retention(RUNTIME)
@Target([ElementType.METHOD, ElementType.TYPE])
@Around
@interface TestAnn {
}
''')

        then:
        Throwable t = thrown()
        t.message.contains 'Method annotated as executable but is declared private'
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/5522')
    void 'based on http-client StreamSpec; allow private method with Executable stereotype as long as not declared'() {
        when:
        buildContext('''
package stream.spec;

import io.micronaut.http.annotation.*;

    @Controller('/stream')
    class StreamEchoController {
        private static String helper(String s) {
            s.toUpperCase()
        }
    }
''')

        then:
        noExceptionThrown()
    }

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

import io.micronaut.aop.interceptors.*;
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

import io.micronaut.aop.interceptors.*;
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

    void 'test multiple annotations on a single method'() {
        given:
        ApplicationContext context = buildContext('''
package annbinding2;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import javax.inject.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import io.micronaut.aop.simple.*;

@Singleton
class MyBean {
    @TestAnn
    @TestAnn2
    void test() {
        
    }
    
}

@Retention(RUNTIME)
@Target([ElementType.METHOD, ElementType.TYPE])
@Around
@interface TestAnn {
}

@Retention(RUNTIME)
@Target([ElementType.METHOD, ElementType.TYPE])
@Around
@interface TestAnn2 {
}

@InterceptorBean(TestAnn.class)
class TestInterceptor implements Interceptor {
    boolean invoked = false;
    @Override
    public Object intercept(InvocationContext context) {
        invoked = true;
        return context.proceed();
    }
} 

@InterceptorBean(TestAnn2.class)
class AnotherInterceptor implements Interceptor {
    boolean invoked = false;
    @Override
    public Object intercept(InvocationContext context) {
        invoked = true;
        return context.proceed();
    }
} 
''')
        def instance = getBean(context, 'annbinding2.MyBean')
        def interceptor = getBean(context, 'annbinding2.TestInterceptor')
        def anotherInterceptor = getBean(context, 'annbinding2.AnotherInterceptor')

        when:
        instance.test()

        then:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked
        anotherInterceptor.invoked

        cleanup:
        context.close()
    }

    void 'test multiple annotations on an interceptor and method'() {
        given:
        ApplicationContext context = buildContext('''
package annbinding2;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import javax.inject.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import io.micronaut.aop.simple.*;

@Singleton
class MyBean {

    @TestAnn
    @TestAnn2
    void test() {
        
    }
    
}

@Retention(RUNTIME)
@Target([ElementType.METHOD, ElementType.TYPE])
@Around
@interface TestAnn {
}

@Retention(RUNTIME)
@Target([ElementType.METHOD, ElementType.TYPE])
@Around
@interface TestAnn2 {
}


@InterceptorBean([ TestAnn.class, TestAnn2.class ])
class TestInterceptor implements Interceptor {
    long count = 0;
    @Override
    public Object intercept(InvocationContext context) {
        count++;
        return context.proceed();
    }
}
''')
        def instance = getBean(context, 'annbinding2.MyBean')
        def interceptor = getBean(context, 'annbinding2.TestInterceptor')

        when:
        instance.test()

        then:
        interceptor.count == 1

        cleanup:
        context.close()
    }

    void 'test multiple annotations on an interceptor'() {
        given:
        ApplicationContext context = buildContext('''
package annbinding2;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import javax.inject.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import io.micronaut.aop.simple.*;

@Singleton
class MyBean {

    @TestAnn
    void test() {
        
    }
    
    @TestAnn2
    void test2() {
        
    }
    
}

@Retention(RUNTIME)
@Target([ElementType.METHOD, ElementType.TYPE])
@Around
@interface TestAnn {
}

@Retention(RUNTIME)
@Target([ElementType.METHOD, ElementType.TYPE])
@Around
@interface TestAnn2 {
}


@InterceptorBean([ TestAnn.class, TestAnn2.class ])
class TestInterceptor implements Interceptor {
    long count = 0;
    @Override
    public Object intercept(InvocationContext context) {
        count++;
        return context.proceed();
    }
}
''')
        def instance = getBean(context, 'annbinding2.MyBean')
        def interceptor = getBean(context, 'annbinding2.TestInterceptor')

        when:
        instance.test()

        then:
        interceptor.count == 1

        when:
        instance.test2()

        then:
        interceptor.count == 2

        cleanup:
        context.close()
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

    static class NamedTestAnnMapper implements NamedAnnotationMapper {

        @Override
        String getName() {
            return 'mapperbinding.TestAnn'
        }

        @Override
        List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
            return Collections.singletonList(AnnotationValue.builder(InterceptorBinding)
                    .value(getName())
                    .member("kind", InterceptorKind.AROUND)
                    .build())
        }
    }
}

