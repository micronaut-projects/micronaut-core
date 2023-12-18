package test.another;

import jakarta.inject.Inject;

public class Base {
    public boolean base;
    @Inject
    void injectPackagePrivateMethod() {
        base = true;
    }
}
