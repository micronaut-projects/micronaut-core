package io.micronaut.inject.factory.primary_and_named_parameterizedfactory2;

public class MyBeanUser {

    private final String name;
    private final MyBeanUser2 myBean;

    public MyBeanUser(String name, MyBeanUser2 myBean) {
        this.name = name;
        this.myBean = myBean;
    }

    public MyBeanUser2 getMyBean() {
        return myBean;
    }

    public String getName() {
        return name;
    }
}
