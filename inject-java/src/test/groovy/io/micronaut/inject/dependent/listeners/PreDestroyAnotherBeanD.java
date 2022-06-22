package io.micronaut.inject.dependent.listeners;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.event.BeanPreDestroyEvent;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import io.micronaut.inject.dependent.TestData;

@Prototype
public class PreDestroyAnotherBeanD implements BeanPreDestroyEventListener<AnotherBeanD> {
    @Override
    public AnotherBeanD onPreDestroy(BeanPreDestroyEvent<AnotherBeanD> event) {
        TestData.DESTRUCTION_ORDER.add(AnotherBeanD.class.getSimpleName());
        AnotherBeanD bean = event.getBean();
        bean.destroyed = true;
        return bean;
    }
}
