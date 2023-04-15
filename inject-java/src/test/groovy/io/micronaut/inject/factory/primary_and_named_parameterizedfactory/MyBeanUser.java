package io.micronaut.inject.factory.primary_and_named_parameterizedfactory;

public class MyBeanUser {

    private final String name;
    private final MyBean myBean;

    public MyBeanUser(String name, MyBean myBean) {
        this.name = name;
        this.myBean = myBean;
    }

    public MyBean getMyBean() {
        return myBean;
    }

    public String getName() {
        return name;
    }
}
