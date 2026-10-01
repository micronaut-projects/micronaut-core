package io.micronaut.inject.lifecycle.registrationclose;

import io.micronaut.context.event.BeanPreDestroyEvent;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class OwnerBeanPreDestroyListener implements BeanPreDestroyEventListener<OwnerBean> {

    final List<OwnerBean> destroyed = new ArrayList<>();

    @Override
    public OwnerBean onPreDestroy(BeanPreDestroyEvent<OwnerBean> event) {
        destroyed.add(event.getBean());
        return event.getBean();
    }
}
