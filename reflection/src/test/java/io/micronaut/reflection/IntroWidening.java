package io.micronaut.reflection;

/**
 * A type whose only setter takes more than the property holds. The processor keeps such a setter - the value
 * of the property can be given to its parameter - so the property is written through it.
 */
public class IntroWidening {

    private String value = "";

    public void setValue(Object value) {
        this.value = String.valueOf(value);
    }

    public String read() {
        return value;
    }
}
