package io.micronaut.inject.configproperties.eachbeanparameter;

class MyBean  {

    private final String name;

    MyBean(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
