package io.micronaut.reflection;

/**
 * A type written to and never read from: the processor describes one write property and no read one, and so
 * does reflection - the field the setter names is private, and no access kind admits it.
 */
public class IntroWriteOnly {

    private String secret = "secret";

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String describe() {
        return "secret:" + secret;
    }
}
