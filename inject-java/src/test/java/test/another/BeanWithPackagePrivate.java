package test.another;

import jakarta.inject.Singleton;
import test.Middle;

@Singleton
public class BeanWithPackagePrivate extends Middle {
    public boolean root;
    void injectPackagePrivateMethod() {
        root = true;
    }
}
