package io.micronaut.inject.dependent.listeners;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.event.BeanPreDestroyEvent;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import io.micronaut.inject.dependent.TestData;

@Prototype
public class PreDestroyAnotherBeanB implements BeanPreDestroyEventListener<AnotherBeanB> {
    @Override
    public AnotherBeanB onPreDestroy(BeanPreDestroyEvent<AnotherBeanB> event) {
        TestData.DESTRUCTION_ORDER.add(AnotherBeanB.class.getSimpleName());
        AnotherBeanB bean = event.getBean();
        bean.destroyed = true;
        return bean;
    }
}
