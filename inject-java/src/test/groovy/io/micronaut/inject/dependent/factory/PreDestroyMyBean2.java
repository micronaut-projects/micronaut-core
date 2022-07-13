package io.micronaut.inject.dependent.factory;

import io.micronaut.context.event.BeanPreDestroyEvent;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import io.micronaut.inject.dependent.TestData;
import jakarta.inject.Singleton;

@Singleton
public class PreDestroyMyBean2 implements BeanPreDestroyEventListener<MyBean2> {
    @Override
    public MyBean2 onPreDestroy(BeanPreDestroyEvent<MyBean2> event) {
        TestData.DESTRUCTION_ORDER.add(MyBean2.class.getSimpleName());
        MyBean2Factory.beanDestroyed++;
        return event.getBean();
    }
}
