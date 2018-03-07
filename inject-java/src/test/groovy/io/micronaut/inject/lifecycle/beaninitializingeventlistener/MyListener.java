package io.micronaut.inject.lifecycle.beaninitializingeventlistener;

import io.micronaut.context.event.BeanInitializedEventListener;
import io.micronaut.context.event.BeanInitializingEvent;
import io.micronaut.context.event.BeanInitializedEventListener;
import io.micronaut.context.event.BeanInitializingEvent;

import javax.inject.Singleton;

@Singleton
public class MyListener implements BeanInitializedEventListener<BFactory> {

    @Override
    public BFactory onInitialized(BeanInitializingEvent<BFactory> event) {
        BFactory bean = event.getBean();
        assert bean.getMethodInjected() != null;
        assert bean.getFieldA() != null;
        assert bean.getAnotherField() != null;
        assert bean.getA()  != null;
        assert !bean.isPostConstructCalled();
        assert !bean.isGetCalled();
        bean.setName("changed");
        return bean;
    }
}
