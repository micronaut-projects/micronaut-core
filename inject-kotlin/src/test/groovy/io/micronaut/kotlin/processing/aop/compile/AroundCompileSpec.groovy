package io.micronaut.kotlin.processing.aop.compile

import io.micronaut.aop.Intercepted
import io.micronaut.aop.InterceptorBinding
import io.micronaut.aop.InterceptorKind
import io.micronaut.kotlin.processing.aop.simple.Mutating
import io.micronaut.kotlin.processing.aop.simple.TestBinding
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.AdvisedBeanType
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.annotation.NamedAnnotationMapper
import io.micronaut.inject.annotation.NamedAnnotationTransformer
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.inject.writer.BeanDefinitionWriter
import spock.lang.Issue
import spock.lang.PendingFeature
import spock.lang.Specification

import static io.micronaut.kotlin.processing.KotlinCompiler.*

class AroundCompileSpec extends Specification {

    void 'test stereotype method level interceptor matching'() {
        given:
        ApplicationContext context = buildContext('''
package annbinding2

import io.micronaut.aop.Around
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.Interceptor
import io.micronaut.aop.InvocationContext
import jakarta.inject.Singleton

@Singleton
open class MyBean {

    @TestAnn2
    open fun test() {
        
    }
    
}

@Retention
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Around
annotation class TestAnn

@Retention
@Target(AnnotationTarget.FUNCTION)
@TestAnn
annotation class TestAnn2

@InterceptorBean(TestAnn::class)
class TestInterceptor: Interceptor<Any, Any> {
    var invoked = false
    
    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
} 

''')
        def instance = getBean(context, 'annbinding2.MyBean')
        def interceptor = getBean(context, 'annbinding2.TestInterceptor')

        when:
        instance.test()

        then:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked

        cleanup:
        context.close()
    }

    void 'test apply interceptor binder with annotation mapper'() {
        given:
        ApplicationContext context = buildContext('''
package mapperbinding

import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.Interceptor
import io.micronaut.aop.InvocationContext
import jakarta.inject.Singleton

@Singleton
open class MyBean {

    @TestAnn
    open fun test() {
    }
}

@Retention
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
annotation class TestAnn


@InterceptorBean(TestAnn::class)
class TestInterceptor: Interceptor<Any, Any> {
    var invoked = false
    
    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
} 

''')
        def instance = getBean(context,'mapperbinding.MyBean')
        def interceptor = getBean(context,'mapperbinding.TestInterceptor')

        when:
        instance.test()

        then:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked
    }

    void 'test apply interceptor binder with annotation mapper - plus members'() {
        given:
        ApplicationContext context = buildContext('''
package mapperbindingmembers

import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.Interceptor
import io.micronaut.aop.InvocationContext
import jakarta.inject.Singleton

@Singleton
open class MyBean {
    @TestAnn(num=1)
    open fun test() {
    }
}

@Retention
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class MyInterceptorBinding

@Retention
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@MyInterceptorBinding
annotation class TestAnn(val num: Int)

@Singleton
@TestAnn(num=1)
class TestInterceptor: Interceptor<Any, Any> {
    var invoked = false
    
    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
} 

@Singleton
@TestAnn(num=2)
class TestInterceptor2: Interceptor<Any, Any> {
    var invoked = false
    
    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
} 

''')
        def instance = getBean(context, 'mapperbindingmembers.MyBean')
        def interceptor = getBean(context, 'mapperbindingmembers.TestInterceptor')
        def interceptor2 = getBean(context, 'mapperbindingmembers.TestInterceptor2')

        when:
        instance.test()

        then:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked
        !interceptor2.invoked
    }

    void 'test method level interceptor matching'() {
        given:
        ApplicationContext context = buildContext('''
package annbinding2

import io.micronaut.aop.*
import jakarta.inject.Singleton

@Singleton
open class MyBean {

    @TestAnn
    open fun test() {

    }

    @TestAnn2
    open fun test2() {

    }
}

@Retention
@Target(AnnotationTarget.FUNCTION)
@Around
annotation class TestAnn

@Retention
@Target(AnnotationTarget.FUNCTION)
@Around
annotation class TestAnn2

@InterceptorBean(TestAnn::class)
class TestInterceptor: Interceptor<Any, Any> {
    var invoked = false
    
    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
} 

@InterceptorBean(TestAnn2::class)
class AnotherInterceptor: Interceptor<Any, Any> {
    var invoked = false
    
    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
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

    @PendingFeature(reason = "annotation defaults")
    void 'test annotation with just interceptor binding'() {
        given:
        ApplicationContext context = buildContext('''
package annbinding1

import io.micronaut.aop.*
import jakarta.inject.Singleton

@Singleton
@TestAnn
open class MyBean {

    open fun test() {
    }
}

@Retention
@Target(AnnotationTarget.CLASS)
@InterceptorBinding
annotation class TestAnn

@Singleton
@InterceptorBinding(TestAnn::class)
class TestInterceptor: Interceptor<Any, Any> {
    var invoked = false
    
    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
} 

@Singleton
class AnotherInterceptor: Interceptor<Any, Any> {
    var invoked = false
    
    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
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

    @PendingFeature(reason = "annotation defaults")
    void 'test multiple interceptor binding'() {
        given:
        ApplicationContext context = buildContext('''
package multiplebinding

import io.micronaut.aop.*
import io.micronaut.context.annotation.NonBinding
import jakarta.inject.Singleton

@Retention
@InterceptorBinding(kind = InterceptorKind.AROUND)
annotation class Deadly

@Retention
@InterceptorBinding(kind = InterceptorKind.AROUND)
annotation class Fast

@Retention
@InterceptorBinding(kind = InterceptorKind.AROUND)
annotation class Slow

interface Missile {
    fun fire()
}

@Fast
@Deadly
@Singleton
open class FastAndDeadlyMissile: Missile {
    override fun fire() {
    }
}

@Deadly
@Singleton
open class AnyDeadlyMissile: Missile {
    override fun fire() {
    }
}

@Singleton
open class GuidedMissile: Missile {
    @Slow
    @Deadly
    open fun lockAndFire() {
    }

    @Fast
    @Deadly
    override fun fire() {
    }
}

@Slow
@Deadly
@Singleton
open class SlowMissile: Missile {
    override fun fire() {
    }
}

@Fast
@Deadly
@Singleton
class MissileInterceptor: MethodInterceptor<Any, Any> {
    var intercepted = false
    
    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        intercepted = true
        return context.proceed()
    }
} 

@Slow
@Deadly
@Singleton
class LockInterceptor: MethodInterceptor<Any, Any> {
    var intercepted = false
    
    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        intercepted = true
        return context.proceed()
    }
} 
''')
        def missileInterceptor = getBean(context, 'multiplebinding.MissileInterceptor')
        def lockInterceptor = getBean(context, 'multiplebinding.LockInterceptor')

        when:
        missileInterceptor.intercepted = false
        lockInterceptor.intercepted = false
        def guidedMissile = getBean(context, 'multiplebinding.GuidedMissile');
        guidedMissile.fire()

        then:
        missileInterceptor.intercepted
        !lockInterceptor.intercepted

        when:
        missileInterceptor.intercepted = false
        lockInterceptor.intercepted = false
        def fastAndDeadlyMissile = getBean(context, 'multiplebinding.FastAndDeadlyMissile');
        fastAndDeadlyMissile.fire()

        then:
        missileInterceptor.intercepted
        !lockInterceptor.intercepted

        when:
        missileInterceptor.intercepted = false
        lockInterceptor.intercepted = false
        def slowMissile = getBean(context, 'multiplebinding.SlowMissile');
        slowMissile.fire()

        then:
        !missileInterceptor.intercepted
        lockInterceptor.intercepted

        when:
        missileInterceptor.intercepted = false
        lockInterceptor.intercepted = false
        def anyMissile = getBean(context, 'multiplebinding.AnyDeadlyMissile');
        anyMissile.fire()

        then:
        missileInterceptor.intercepted
        lockInterceptor.intercepted

        cleanup:
        context.close()
    }

    void 'test annotation with just interceptor binding - member binding'() {
        given:
        ApplicationContext context = buildContext('''
package memberbinding

import io.micronaut.aop.*
import io.micronaut.context.annotation.NonBinding
import jakarta.inject.Singleton

@Singleton
@TestAnn(num=1, debug = false)
open class MyBean {
    open fun test() {
    }

    @TestAnn(num=2) // overrides binding on type
    open fun test2() {

    }
}

@Retention
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@InterceptorBinding(bindMembers = true)
annotation class TestAnn(val num: Int, @get:NonBinding val debug: Boolean = false)

@InterceptorBean(TestAnn::class)
@TestAnn(num = 1, debug = true)
class TestInterceptor: Interceptor<Any, Any> {
    var invoked = false
    
    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
} 

@InterceptorBean(TestAnn::class)
@TestAnn(num = 2)
class AnotherInterceptor: Interceptor<Any, Any> {
    var invoked = false
    
    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
} 
''')
        def instance = getBean(context, 'memberbinding.MyBean')
        def interceptor = getBean(context, 'memberbinding.TestInterceptor')
        def anotherInterceptor = getBean(context, 'memberbinding.AnotherInterceptor')

        when:
        instance.test()

        then:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked
        !anotherInterceptor.invoked

        when:
        interceptor.invoked = false
        instance.test2()

        then:
        !interceptor.invoked
        anotherInterceptor.invoked

        cleanup:
        context.close()
    }

    /*
    void 'test annotation with just around'() {
        given:
        ApplicationContext context = buildContext('''
package justaround;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import jakarta.inject.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Singleton
@TestAnn
class MyBean {
    void test() {
    }
}

@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
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
import jakarta.inject.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Singleton
class MyBean {
    @TestAnn
    private void test() {
    }
}

@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Around
@interface TestAnn {
}
''')

        then:
        Throwable t = thrown()
        t.message.contains 'Method annotated as executable but is declared private'
    }

    void 'test byte[] return compile'() {
        given:
        ApplicationContext context = buildContext('''
package test;

import io.micronaut.aop.proxytarget.*;

@jakarta.inject.Singleton
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

@jakarta.inject.Singleton
@Mutating("someVal")
@TestBinding
class MyBean {
    void test() {};
}
''')

        BeanDefinitionReference ref = buildInterceptedBeanDefinitionReference('test.MyBean', '''
package test;

import io.micronaut.aop.simple.*;

@jakarta.inject.Singleton
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
import jakarta.inject.*;
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
@Target({ElementType.METHOD, ElementType.TYPE})
@Around
@interface TestAnn {
}

@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
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
import jakarta.inject.*;
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
@Target({ElementType.METHOD, ElementType.TYPE})
@Around
@interface TestAnn {
}

@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Around
@interface TestAnn2 {
}


@InterceptorBean({ TestAnn.class, TestAnn2.class })
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
import jakarta.inject.*;
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
@Target({ElementType.METHOD, ElementType.TYPE})
@Around
@interface TestAnn {
}

@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Around
@interface TestAnn2 {
}


@InterceptorBean({ TestAnn.class, TestAnn2.class })
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
        BeanDefinition beanDefinition = buildBeanDefinition('test.$BaseEntityService' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, """
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
    }*/

}
