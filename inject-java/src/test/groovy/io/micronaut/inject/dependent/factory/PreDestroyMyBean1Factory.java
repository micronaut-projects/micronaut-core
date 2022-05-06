package io.micronaut.inject.dependent.factory;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.event.BeanPreDestroyEvent;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import io.micronaut.inject.dependent.TestData;

@Prototype
public class PreDestroyMyBean1Factory implements BeanPreDestroyEventListener<MyBean1Factory> {
    @Override
    public MyBean1Factory onPreDestroy(BeanPreDestroyEvent<MyBean1Factory> event) {
        TestData.DESTRUCTION_ORDER.add(MyBean1Factory.class.getSimpleName());
        MyBean1Factory.destroyed++;
        return event.getBean();
    }
}
