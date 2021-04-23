package io.micronaut.inject.beans;

import io.micronaut.core.annotation.Introspected;

import java.net.URL;

@Introspected
public class MyBean extends SuperClass {

    private boolean bool;
    private URL URL;
    private String str;
    private final String readOnly;
    private String foo;

    MyBean(String readOnly) {
        this.readOnly = readOnly;
    }

    public String getFoo() {
        return foo;
    }

    public void setFoo(String foo) {
        this.foo = foo;
    }

    public String getReadOnly() {
        return readOnly;
    }

    public String getStr() {
        return str;
    }

    public void setStr(String str) {
        this.str = str;
    }

    public java.net.URL getURL() {
        return URL;
    }

    public void setURL(java.net.URL URL) {
        this.URL = URL;
    }

    public boolean isBool() {
        return bool;
    }

    public void setBool(boolean bool) {
        this.bool = bool;
    }
}
