package io.micronaut.inject.factory.generics

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec

class GenericFactorySpec extends AbstractBeanDefinitionSpec {

    void "test generic factory with type variables"() {
        given:
        def context = buildContext('''
package genfact;

import io.micronaut.context.annotation.*;
import io.micronaut.inject.ArgumentInjectionPoint;
import jakarta.inject.*;
import io.micronaut.core.type.ArgumentCoercible;

@Singleton
class MyBean {
    @Inject
    public Cache<String, Integer> fieldInject;
    public Cache<StringBuilder, Float> methodInject;
    
    @Inject
    void setCache(Cache<StringBuilder, Float> methodInject) {
        this.methodInject = methodInject;
    }
}

@Factory
class CacheFactory {
    @Bean
    <K extends CharSequence, V> Cache<K, V> buildCache(ArgumentInjectionPoint<?, ?> ip) {
        Class<?> keyType = ip.asArgument().getTypeVariable("K").get().getType();
        Class<?> valueType = ip.asArgument().getTypeVariable("V").get().getType();
        
        return new CacheImpl(keyType, valueType);
    }
}

interface Cache<K extends CharSequence, V> {}

class CacheImpl implements Cache {
    public final Class<?> keyType;
    public final Class<?> valueType;
    
    CacheImpl(Class<?> k, Class<?> v) {
        keyType = k;
        valueType = v;
    }
}
''')
        def bean = getBean(context, "genfact.MyBean")

        expect:
        bean.fieldInject.keyType == String
        bean.fieldInject.valueType == Integer
        bean.methodInject.keyType == StringBuilder
        bean.methodInject.valueType == Float

        cleanup:
        context.close()
    }
}
