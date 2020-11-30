package io.micronaut.inject.foreach;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;

import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;

@Singleton
public class MyContextBeanListener implements BeanCreatedEventListener<MyContextBean> {
    Set<String> createdNames = new HashSet<>();
    @Override
    public MyContextBean onCreated(BeanCreatedEvent<MyContextBean> event) {
        String name = event.getBeanIdentifier().getName();
        createdNames.add(name);
        return event.getBean();
    }

    public Set<String> getCreatedNames() {
        return createdNames;
    }
}
