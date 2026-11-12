package io.micronaut.aop.factory

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.aop.InterceptedProxy
import io.micronaut.context.ApplicationContext

import javax.tools.Diagnostic

/**
 * Advice a method of a type produced by a {@code @Factory} declares itself makes the produced bean a proxy, even when
 * neither the producing element nor the produced type declares any advice, which is what a bean declaring the same
 * method gets. Only the methods declaring the advice are advised then.
 */
class FactoryProducedBeanMethodAdviceSpec extends AbstractTypeElementSpec {

    private JavaParser parser

    @Override
    protected JavaParser newJavaParser() {
        parser = super.newJavaParser()
        return parser
    }

    private static final String ADVICE = '''
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@interface Traced {
}

@Singleton
@InterceptorBean(Traced.class)
class TracedInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        Calls.RECORDED.add("AROUND:" + ctx.getMethodName());
        return ctx.proceed();
    }
}

class Calls {
    static final List<String> RECORDED = new ArrayList<>();
}
'''

    void 'test a method declaring advice makes an unannotated produced bean a proxy'() {
        given:
        ApplicationContext context = buildContext("""
package producedmethod.plain;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;
$ADVICE
class Product {
    @Traced
    public void method() {
        Calls.RECORDED.add("method");
    }
    public void plainMethod() {
        Calls.RECORDED.add("plainMethod");
    }
    @Traced
    protected void protectedMethod() {
        Calls.RECORDED.add("protectedMethod");
    }
}

class Invoker {
    static void callAll(Product product) {
        product.method();
        product.plainMethod();
        product.protectedMethod();
    }
}

@Factory
class ProductFactory {
    @Singleton
    Product product() {
        return new Product();
    }
}
""")
        Class<?> callsType = context.classLoader.loadClass('producedmethod.plain.Calls')
        Class<?> productType = context.classLoader.loadClass('producedmethod.plain.Product')
        Class<?> invokerType = context.classLoader.loadClass('producedmethod.plain.Invoker')

        when:
        def bean = context.getBean(productType)
        def callAll = invokerType.getDeclaredMethod('callAll', productType)
        callAll.setAccessible(true)
        callsType.RECORDED.clear()
        callAll.invoke(null, bean)

        then: 'the bean is a proxy advising only the methods declaring the advice'
        bean instanceof InterceptedProxy
        callsType.RECORDED == [
                'AROUND:method', 'method',
                'plainMethod',
                'AROUND:protectedMethod', 'protectedMethod'
        ]

        cleanup:
        context.close()
    }

    void 'test a produced type that cannot be proxied keeps compiling and its method advice is reported'() {
        given: 'a produced type whose only constructor takes an argument'
        ApplicationContext context = buildContext("""
package producedmethod.unproxyable;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;
$ADVICE
class Product {
    Product(String name) {
    }
    @Traced
    public void method() {
        Calls.RECORDED.add("method");
    }
}

@Factory
class ProductFactory {
    @Singleton
    Product product() {
        return new Product("name");
    }
}
""")
        Class<?> callsType = context.classLoader.loadClass('producedmethod.unproxyable.Calls')
        Class<?> productType = context.classLoader.loadClass('producedmethod.unproxyable.Product')

        when:
        def bean = context.getBean(productType)
        callsType.RECORDED.clear()
        bean.method()

        then: 'the bean is the produced instance, as before'
        !(bean instanceof InterceptedProxy)
        callsType.RECORDED == ['method']

        and: 'the unapplied advice is reported on the producing element'
        parser.diagnosticCollector.diagnostics
                .findAll { it.kind == Diagnostic.Kind.WARNING || it.kind == Diagnostic.Kind.MANDATORY_WARNING }
                .any { it.getMessage(Locale.ENGLISH).contains('declare AOP advice, which is not applied because the type has no accessible no arguments constructor') }

        cleanup:
        context.close()
    }

    void 'test a produced type without method advice is not proxied'() {
        given:
        ApplicationContext context = buildContext("""
package producedmethod.none;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;
$ADVICE
class Product {
    public void method() {
    }
}

@Factory
class ProductFactory {
    @Singleton
    Product product() {
        return new Product();
    }
}
""")
        Class<?> productType = context.classLoader.loadClass('producedmethod.none.Product')

        expect:
        !(context.getBean(productType) instanceof InterceptedProxy)

        cleanup:
        context.close()
    }
}
