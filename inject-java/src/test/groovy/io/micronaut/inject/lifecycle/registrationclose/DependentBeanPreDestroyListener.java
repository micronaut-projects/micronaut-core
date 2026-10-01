package io.micronaut.inject.lifecycle.registrationclose;

import io.micronaut.context.event.BeanPreDestroyEvent;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class DependentBeanPreDestroyListener implements BeanPreDestroyEventListener<DependentBean> {

    final List<DependentBean> destroyed = new ArrayList<>();

    @Override
    public DependentBean onPreDestroy(BeanPreDestroyEvent<DependentBean> event) {
        destroyed.add(event.getBean());
        return event.getBean();
    }
}
