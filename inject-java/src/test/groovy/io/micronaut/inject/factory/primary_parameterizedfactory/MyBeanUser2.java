package io.micronaut.inject.factory.primary_parameterizedfactory;

public class MyBeanUser2 {

    private final String name;
    private final MyBeanUser myBean;

    public MyBeanUser2(String name, MyBeanUser myBean) {
        this.name = name;
        this.myBean = myBean;
    }

    public MyBeanUser getMyBean() {
        return myBean;
    }

    public String getName() {
        return name;
    }
}
