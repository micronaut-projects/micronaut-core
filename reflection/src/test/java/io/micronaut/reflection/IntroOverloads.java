package io.micronaut.reflection;

/**
 * A type declaring several accessors of the same property: an overloaded setter taking more than the property
 * holds, and both accessors a boolean is named by. Only {@code setValue(String)} and {@code isActive} yield
 * what the type holds, so that a specification can tell which declaration the introspection selects.
 */
public class IntroOverloads {

    private String value = "";

    private boolean active;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setValue(Object value) {
        this.value = "object";
    }

    public boolean isActive() {
        return active;
    }

    public boolean getActive() {
        return false;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
