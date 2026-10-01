package io.micronaut.inject.lifecycle.registrationclose;

import io.micronaut.context.event.BeanDestroyedEvent;
import io.micronaut.context.event.BeanDestroyedEventListener;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class OwnerBeanDestroyedListener implements BeanDestroyedEventListener<OwnerBean> {

    final List<OwnerBean> destroyed = new ArrayList<>();

    @Override
    public void onDestroyed(BeanDestroyedEvent<OwnerBean> event) {
        destroyed.add(event.getBean());
    }
}
