package test;

import jakarta.inject.Inject;
import test.another.Base;

public class Middle extends Base {
    public boolean middle;
    @Inject
    void injectPackagePrivateMethod() {
        middle = true;
    }
}
