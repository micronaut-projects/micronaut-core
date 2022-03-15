package io.micronaut.inject.visitor.beans;

public abstract class TestMatchingGetterClass {

    private Boolean isDeleted;
    private String name;

    public Boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
