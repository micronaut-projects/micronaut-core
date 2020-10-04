package test.java;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TestInjectConstructorSingletonBean {
    public final TestSingletonBean singletonBean;

    @Inject
    public TestInjectConstructorSingletonBean(TestSingletonBean singletonBean) {
        this.singletonBean = singletonBean;
    }
}
