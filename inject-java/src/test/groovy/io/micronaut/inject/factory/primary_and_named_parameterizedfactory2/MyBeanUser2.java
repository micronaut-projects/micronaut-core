package io.micronaut.inject.factory.primary_and_named_parameterizedfactory2;

public class MyBeanUser2 {

    private final String name;
    private final MyBean myBean;

    public MyBeanUser2(String name, MyBean myBean) {
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
