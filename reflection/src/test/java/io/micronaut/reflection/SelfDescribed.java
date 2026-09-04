package io.micronaut.reflection;

import io.micronaut.core.annotation.Introspected;

/**
 * A type saying how it is to be described, which a configuration naming it does not displace.
 */
@Introspected(excludes = "password")
public class SelfDescribed {

    private String kept;
    private String password;
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
}
