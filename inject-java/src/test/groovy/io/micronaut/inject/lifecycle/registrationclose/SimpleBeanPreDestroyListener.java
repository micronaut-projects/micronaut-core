package io.micronaut.inject.lifecycle.registrationclose;

import io.micronaut.context.event.BeanPreDestroyEvent;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class SimpleBeanPreDestroyListener implements BeanPreDestroyEventListener<SimpleBean> {

    final List<SimpleBean> destroyed = new ArrayList<>();
    SimpleBean replacement;

    @Override
    public SimpleBean onPreDestroy(BeanPreDestroyEvent<SimpleBean> event) {
        destroyed.add(event.getBean());
        return replacement == null ? event.getBean() : replacement;
    }
}
