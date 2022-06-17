package io.micronaut.inject.factory.primary_and_named_parameterizedfactory;

public class MyBean {

    private final String name;
    private final MyAssocBean myAssocBean;

    public MyBean(String name, MyAssocBean myAssocBean) {
        this.name = name;
        this.myAssocBean = myAssocBean;
    }

    public MyAssocBean getMyAssocBean() {
        return myAssocBean;
    }

    public String getName() {
        return name;
    }
}
