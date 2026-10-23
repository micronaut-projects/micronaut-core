package io.micronaut.aop.factory

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * Which methods of a type produced by a {@code @Factory} carry the around advice declared by the producing element.
 *
 * <p>This is the rule a bean that declares the advice itself gets - public and package-private methods inherit the
 * class-level advice, and a method carries advice it declares itself whatever its visibility - bounded by what the
 * generated code can reach: the proxy is a subclass of the produced type generated into the factory's package, so a
 * non-public method is only reachable when the factory's package can see it.</p>
 */
class FactoryProducedBeanAdvisedMethodsSpec extends AbstractTypeElementSpec {

    void 'test the public and package private methods of a produced type are advised'() {
        given:
        ApplicationContext context = buildContext('''
package produced.samepackage;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

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

class Product {
    public void publicMethod() {
        Calls.RECORDED.add("publicMethod");
    }
    void packagePrivateMethod() {
        Calls.RECORDED.add("packagePrivateMethod");
    }
    protected void protectedMethod() {
        Calls.RECORDED.add("protectedMethod");
    }
    @Traced
    protected void boundProtectedMethod() {
        Calls.RECORDED.add("boundProtectedMethod");
    }
    public final void finalMethod() {
        Calls.RECORDED.add("finalMethod");
    }
    private void privateMethod() {
        Calls.RECORDED.add("privateMethod");
    }
}

class Invoker {
    static void callAll(Product product) {
        product.publicMethod();
        product.packagePrivateMethod();
        product.protectedMethod();
        product.boundProtectedMethod();
        product.finalMethod();
    }
}

@Factory
class ProductFactory {
    @Singleton
    @Traced
    Product product() {
        return new Product();
    }
}
''')
        Class<?> callsType = context.classLoader.loadClass('produced.samepackage.Calls')
        Class<?> productType = context.classLoader.loadClass('produced.samepackage.Product')
        Class<?> invokerType = context.classLoader.loadClass('produced.samepackage.Invoker')

        when: 'the methods are called through the proxy reference, from the factory package'
        def bean = context.getBean(productType)
        def callAll = invokerType.getDeclaredMethod('callAll', productType)
        callAll.setAccessible(true)
        callsType.RECORDED.clear()
        callAll.invoke(null, bean)

        then: 'the public and package-private methods inherit the advice, a protected method only carries the advice it declares itself, and the final one is not advised'
        callsType.RECORDED == [
                'AROUND:publicMethod', 'publicMethod',
                'AROUND:packagePrivateMethod', 'packagePrivateMethod',
                'protectedMethod',
                'AROUND:boundProtectedMethod', 'boundProtectedMethod',
                'finalMethod'
        ]

        and: 'the proxy overrides exactly those methods, and neither the final nor the private one'
        overriddenMethods(bean) == ['boundProtectedMethod', 'packagePrivateMethod', 'publicMethod']

        cleanup:
        context.close()
    }

    void 'test a non public method of a produced type in another package is not advised'() {
        given: 'the produced type and the factory are in different packages'
        def files = new AbstractTypeElementSpec.JavaFiles()
                .add('produced.other.Product', '''
package produced.other;

import java.util.*;

public class Product {
    public static final List<String> RECORDED = new ArrayList<>();

    public void publicMethod() {
        RECORDED.add("publicMethod");
    }
    void packagePrivateMethod() {
        RECORDED.add("packagePrivateMethod");
    }
    protected void protectedMethod() {
        RECORDED.add("protectedMethod");
    }
}
''')
                .add('produced.otherfactory.ProductFactory', '''
package produced.otherfactory;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import produced.other.Product;

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
        Product.RECORDED.add("AROUND:" + ctx.getMethodName());
        return ctx.proceed();
    }
}

@Factory
public class ProductFactory {
    @Singleton
    @Traced
    Product product() {
        return new Product();
    }
}
''')
        ApplicationContext context = buildContext(files)
        Class<?> productType = context.classLoader.loadClass('produced.other.Product')

        when:
        def bean = context.getBean(productType)
        productType.RECORDED.clear()
        bean.publicMethod()

        then: 'the public method is advised'
        productType.RECORDED == ['AROUND:publicMethod', 'publicMethod']

        and: 'the proxy declares no override of the methods its package cannot see: it could not override the ' +
                'package-private one at all, and could not invoke the protected one on the target'
        overriddenMethods(bean) == ['publicMethod']

        cleanup:
        context.close()
    }

    void 'test the advised methods of a produced type match those of the same type declared as a bean'() {
        given: 'the same type, once produced by a factory and once declaring the advice itself'
        ApplicationContext context = buildContext('''
package produced.parity;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

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
        return ctx.proceed();
    }
}

class Product {
    public void publicMethod() {
    }
    void packagePrivateMethod() {
    }
    protected void protectedMethod() {
    }
    @Traced
    protected void boundProtectedMethod() {
    }
}

@Singleton
@Traced
@Named("declared")
class DeclaredProduct extends Product {
}

@Factory
class ProductFactory {
    @Singleton
    @Traced
    @Named("produced")
    Product product() {
        return new Product();
    }
}
''')
        Class<?> productType = context.classLoader.loadClass('produced.parity.Product')
        Class<?> declaredType = context.classLoader.loadClass('produced.parity.DeclaredProduct')

        when:
        def produced = context.getBean(productType, io.micronaut.inject.qualifiers.Qualifiers.byName('produced'))
        def declared = context.getBean(declaredType)
        then: 'the produced bean and the declared bean advise the same methods'
        overriddenMethods(produced) == ['boundProtectedMethod', 'packagePrivateMethod', 'publicMethod']
        overriddenMethods(declared) == overriddenMethods(produced)

        cleanup:
        context.close()
    }

    private static List<String> overriddenMethods(Object bean) {
        bean.getClass().declaredMethods
                .collect { it.name }
                .findAll { !it.startsWith('$') && !it.startsWith('intercepted') && it != 'hasCachedInterceptedTarget' }
                .toSorted()
    }
}
