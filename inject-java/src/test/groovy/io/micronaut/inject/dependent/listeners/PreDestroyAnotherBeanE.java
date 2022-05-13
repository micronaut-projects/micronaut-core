package io.micronaut.inject.dependent.listeners;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.event.BeanPreDestroyEvent;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import io.micronaut.inject.dependent.TestData;

@Prototype
public class PreDestroyAnotherBeanE implements BeanPreDestroyEventListener<AnotherBeanE> {
    @Override
    public AnotherBeanE onPreDestroy(BeanPreDestroyEvent<AnotherBeanE> event) {
        TestData.DESTRUCTION_ORDER.add(AnotherBeanE.class.getSimpleName());
        AnotherBeanE bean = event.getBean();
        bean.destroyed = true;
        return bean;
    }
}
