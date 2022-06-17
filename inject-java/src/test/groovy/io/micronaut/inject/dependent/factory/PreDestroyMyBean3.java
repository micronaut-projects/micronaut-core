package io.micronaut.inject.dependent.factory;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.event.BeanPreDestroyEvent;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import io.micronaut.inject.dependent.TestData;

@Prototype
public class PreDestroyMyBean3 implements BeanPreDestroyEventListener<MyBean3> {
    @Override
    public MyBean3 onPreDestroy(BeanPreDestroyEvent<MyBean3> event) {
        TestData.DESTRUCTION_ORDER.add(MyBean3.class.getSimpleName());
        MyBean3Factory.beanDestroyed++;
        return event.getBean();
    }
}
