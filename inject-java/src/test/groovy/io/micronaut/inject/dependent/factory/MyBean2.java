package io.micronaut.inject.dependent.factory;

public class MyBean2 {

    private final MyBean3 myBean3;

    public MyBean2(MyBean3 myBean3) {
        this.myBean3 = myBean3;
    }

}
