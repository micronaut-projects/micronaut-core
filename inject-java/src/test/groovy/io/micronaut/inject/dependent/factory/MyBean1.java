package io.micronaut.inject.dependent.factory;

public class MyBean1 {

    private final MyBean2 myBean2;

    public MyBean1(MyBean2 myBean2) {
        this.myBean2 = myBean2;
    }
}
