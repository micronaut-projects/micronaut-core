package io.micronaut.reflection;

/**
 * A type the processor never saw, standing in for one of a library: it carries no
 * {@link io.micronaut.core.annotation.Introspected} and says nothing about how it is to be described.
 */
public class ConfiguredBean {

    private String kept;
    private String password;
    @Hidden("kept-out")
    private String secret;
    private String tucked;

    public String getKept() {
        return kept;
    }

    public void setKept(String kept) {
        this.kept = kept;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
