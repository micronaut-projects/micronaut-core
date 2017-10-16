package org.particleframework.inject.lifecycle.beancreationeventlistener;

import org.particleframework.context.event.BeanCreatedEvent;
import org.particleframework.context.event.BeanCreatedEventListener;

import javax.inject.Singleton;

@Singleton
public class BCreationListener implements BeanCreatedEventListener<B> {

    @Override
    public B onCreated(BeanCreatedEvent<B> event) {
        ChildB childB = new ChildB(event.getBean());
        childB.name = "good";
        return childB;
    }
}
