package io.micronaut.inject.dependent.listeners;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.event.BeanPreDestroyEvent;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import io.micronaut.inject.dependent.TestData;

@Prototype
public class PreDestroyAnotherBeanA implements BeanPreDestroyEventListener<AnotherSingletonBeanA> {
    @Override
    public AnotherSingletonBeanA onPreDestroy(BeanPreDestroyEvent<AnotherSingletonBeanA> event) {
        TestData.DESTRUCTION_ORDER.add(AnotherSingletonBeanA.class.getSimpleName());
        AnotherSingletonBeanA bean = event.getBean();
        bean.destroyed = true;
        return bean;
    }
}
