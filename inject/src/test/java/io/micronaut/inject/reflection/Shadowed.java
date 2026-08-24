package io.micronaut.inject.reflection;

/**
 * A field shadowing the one of a super class, with a getter and a setter of its own: every declaration is a
 * member of the property, and each carries its own annotations.
 */
public class Shadowed extends ShadowedBase {

    @Tag("sub")
    private String value;

    @Hidden("indexed")
    private String marked = "here";

    public Shadowed(String value) {
        this.value = value;
    }

    @Tag("getter")
    public String getValue() {
        return value;
    }

    public void setValue(@Tag("param") String value) {
        this.value = value;
    }

    public String getMarked() {
        return marked;
    }
}
