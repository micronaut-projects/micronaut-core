package io.micronaut.inject.foreach.noqualifier;

import io.micronaut.context.annotation.EachBean;

@EachBean(MyService.class)
public class MyEach1 {

    private final MyService myService;

    public MyEach1(MyService myService) {
        this.myService = myService;
    }

    public MyService getMyService() {
        return myService;
    }
}
