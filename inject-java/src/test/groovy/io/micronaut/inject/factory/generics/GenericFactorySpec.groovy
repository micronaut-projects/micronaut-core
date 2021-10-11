package io.micronaut.inject.factory.generics

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.exceptions.DependencyInjectionException
import io.micronaut.context.exceptions.NoSuchBeanException

class GenericFactorySpec extends AbstractTypeElementSpec {

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
    @Inject
    public BaseCache<String, Integer> fieldInjectBase;
    public Cache<StringBuilder, Float> methodInject;
    
    @Inject
    void setCache(Cache<StringBuilder, Float> methodInject) {
        this.methodInject = methodInject;
    }
}

@Singleton
class OtherBean {
    @Inject
    public Cache<Boolean, Integer> invalid;
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

interface BaseCache<K, V> {}

interface Cache<K, V> extends BaseCache<K, V> {}

class CacheImpl implements Cache {
    public final Class<?> keyType;
    public final Class<?> valueType;
    
    CacheImpl(Class<?> k, Class<?> v) {
        keyType = k;
        valueType = v;
    }
}
''')
        when:
        def bean = getBean(context, "genfact.MyBean")

        then:
        bean.fieldInject.keyType == String
        bean.fieldInject.valueType == Integer
        bean.fieldInjectBase.keyType == String
        bean.fieldInjectBase.valueType == Integer
        bean.methodInject.keyType == StringBuilder
        bean.methodInject.valueType == Float

        when:
        getBean(context, "genfact.OtherBean")

        then:
        def e  = thrown(DependencyInjectionException)
        e.message.contains("No bean of type [genfact.Cache<java.lang.Boolean,java.lang.Integer>] exists")

        cleanup:
        context.close()
    }
}
