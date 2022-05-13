package io.micronaut.inject.dependent.listeners;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.event.BeanPreDestroyEvent;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import io.micronaut.inject.dependent.TestData;

@Prototype
public class PreDestroyAnotherBeanC implements BeanPreDestroyEventListener<AnotherBeanC> {
    @Override
    public AnotherBeanC onPreDestroy(BeanPreDestroyEvent<AnotherBeanC> event) {
        TestData.DESTRUCTION_ORDER.add(AnotherBeanC.class.getSimpleName());
        AnotherBeanC bean = event.getBean();
        bean.destroyed = true;
        return bean;
    }
}
