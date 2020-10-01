package test.java;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;

import javax.inject.Singleton;

@Singleton
public class TestSingletonBean {
    public String getHost() {
        return "not injected";
    }
}
