package io.micronaut.reflection;

/**
 * A type keeping state to itself: only the field with accessors is a property, the others are state a
 * generated introspection does not describe either.
 */
public class IntroSecrets {

    String note = "note";

    private String password = "secret";

    private String hidden = "hidden";

    private String name = "name";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private String getHidden() {
        return hidden;
    }
}
