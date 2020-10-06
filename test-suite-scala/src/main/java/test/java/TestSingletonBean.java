package test.java;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;

@Singleton
public class TestSingletonBean {
    public boolean postConstructInvoked = false;

    public String getNotInjected() {
        return "not injected";
    }

    @PostConstruct
    public void postConstruct() {
        postConstructInvoked = true;
    }
}
