package io.micronaut.inject.factory

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition

import javax.tools.Diagnostic

/**
 * The lifecycle callbacks a type produced by a {@code @Factory} declares are not invoked for the produced bean: the
 * instance is constructed by the factory and is not a managed instance of the produced type, which is also what the
 * Jakarta CDI specification says about the return value of a producer method. The documented way to give a produced
 * bean a destroy callback is {@code @Bean(preDestroy = "...")} on the producing element, and construction needs no
 * equivalent because the producing element can initialize the instance itself.
 *
 * <p>Because that is silent, the processors report it once per produced bean.</p>
 */
class FactoryProducedBeanLifecycleCallbackSpec extends AbstractTypeElementSpec {

    private JavaParser parser

    @Override
    protected JavaParser newJavaParser() {
        parser = super.newJavaParser()
        return parser
    }

    void 'test the lifecycle callbacks declared by a produced type are not invoked'() {
        given:
        ApplicationContext context = buildContext('''
package producedlifecycle.plain;

import io.micronaut.context.annotation.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.util.*;

class Product {
    static final List<String> RECORDED = new ArrayList<>();

    @PostConstruct
    void init() {
        RECORDED.add("init");
    }

    @PreDestroy
    void close() {
        RECORDED.add("close");
    }
}

@Factory
class ProductFactory {
    @Singleton
    Product product() {
        return new Product();
    }
}
''')
        Class<?> productType = context.classLoader.loadClass('producedlifecycle.plain.Product')

        when:
        def bean = context.getBean(productType)
        BeanDefinition<?> definition = getBeanDefinition(context, productType.name)
        context.destroyBean(bean)

        then: 'neither callback is exposed by the definition of the produced bean, and neither ran'
        definition.postConstructExecutableMethods.isEmpty()
        definition.preDestroyExecutableMethods.isEmpty()
        productType.RECORDED == []

        and: 'the producing element carries a warning naming the callbacks that will not run'
        def warnings = parser.diagnosticCollector.diagnostics
                .findAll { it.kind == Diagnostic.Kind.WARNING || it.kind == Diagnostic.Kind.MANDATORY_WARNING }
                .collect { it.getMessage(Locale.ENGLISH) }
        warnings.any {
            it.contains('Product.init()') && it.contains('Product.close()') && it.contains('not invoked for a bean produced from a @Factory')
        }

        cleanup:
        context.close()
    }

    void 'test the pre destroy method named by the producing element is invoked and is not reported'() {
        given:
        ApplicationContext context = buildContext('''
package producedlifecycle.named;

import io.micronaut.context.annotation.*;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.util.*;

class Product {
    static final List<String> RECORDED = new ArrayList<>();

    @PreDestroy
    public void close() {
        RECORDED.add("close");
    }
}

@Factory
class ProductFactory {
    @Singleton
    @Bean(preDestroy = "close")
    Product product() {
        return new Product();
    }
}
''')
        Class<?> productType = context.classLoader.loadClass('producedlifecycle.named.Product')

        when:
        def bean = context.getBean(productType)
        context.destroyBean(bean)

        then: 'the named callback runs'
        productType.RECORDED == ['close']

        and: 'and is not reported as uninvoked'
        parser.diagnosticCollector.diagnostics
                .findAll { it.kind == Diagnostic.Kind.WARNING || it.kind == Diagnostic.Kind.MANDATORY_WARNING }
                .every { !it.getMessage(Locale.ENGLISH).contains('not invoked for a bean produced from a @Factory') }

        cleanup:
        context.close()
    }

    void 'test a produced type that is also a declared bean keeps its callbacks on its own definition only'() {
        given:
        ApplicationContext context = buildContext('''
package producedlifecycle.alsobean;

import io.micronaut.context.annotation.*;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.*;

@Singleton
@Named("declared")
class Product {
    static final List<String> RECORDED = new ArrayList<>();

    @PostConstruct
    void init() {
        RECORDED.add("init:" + getClass().getSimpleName());
    }
}

@Factory
class ProductFactory {
    @Singleton
    @Named("produced")
    Product product() {
        return new Product();
    }
}
''')
        Class<?> productType = context.classLoader.loadClass('producedlifecycle.alsobean.Product')

        when: 'the bean of the class own definition is resolved'
        productType.RECORDED.clear()
        context.getBean(productType, io.micronaut.inject.qualifiers.Qualifiers.byName('declared'))

        then: 'its callback runs'
        productType.RECORDED == ['init:Product']

        when: 'the bean produced by the factory is resolved'
        productType.RECORDED.clear()
        context.getBean(productType, io.micronaut.inject.qualifiers.Qualifiers.byName('produced'))

        then: 'the same callback does not run, because the produced instance is not built by that definition'
        productType.RECORDED == []

        cleanup:
        context.close()
    }
}
