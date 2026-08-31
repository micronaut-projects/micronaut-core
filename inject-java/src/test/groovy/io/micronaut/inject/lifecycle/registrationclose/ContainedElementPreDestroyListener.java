package io.micronaut.inject.lifecycle.registrationclose;

import io.micronaut.context.event.BeanPreDestroyEvent;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class ContainedElementPreDestroyListener implements BeanPreDestroyEventListener<ContainedElement> {

    final List<ContainedElement> destroyed = new ArrayList<>();

    @Override
    public ContainedElement onPreDestroy(BeanPreDestroyEvent<ContainedElement> event) {
        destroyed.add(event.getBean());
        return event.getBean();
    }
}
