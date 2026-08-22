package io.micronaut.inject.reflection;

@Shape("base")
public class Base {

    private String baseName = "base";

    public String getBaseName() {
        return baseName;
    }

    public void setBaseName(String baseName) {
        this.baseName = baseName;
    }
}
