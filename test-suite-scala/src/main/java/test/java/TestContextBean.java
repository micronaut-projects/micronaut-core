package test.java;

import io.micronaut.context.annotation.Context;

@Context
public class TestContextBean {
    public String getNotInjected() {
        return "not injected";
    }
}
