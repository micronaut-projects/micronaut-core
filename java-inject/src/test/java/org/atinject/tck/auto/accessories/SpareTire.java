package org.atinject.tck.auto.accessories;

import org.atinject.tck.auto.FuelTank;
import org.atinject.tck.auto.Tire;

import javax.inject.Inject;

public class SpareTire extends Tire {

    FuelTank constructorInjection = NEVER_INJECTED;
    @Inject protected FuelTank fieldInjection = NEVER_INJECTED;
    FuelTank methodInjection = NEVER_INJECTED;
    @Inject static FuelTank staticFieldInjection = NEVER_INJECTED;
    static FuelTank staticMethodInjection = NEVER_INJECTED;

    @Inject
    public SpareTire(FuelTank forSupertype, FuelTank forSubtype) {
        super(forSupertype);
        this.constructorInjection = forSubtype;
    }

    @Inject void subtypeMethodInjection(FuelTank methodInjection) {
        if (!hasSpareTireBeenFieldInjected()) {
            methodInjectedBeforeFields = true;
        }
        this.methodInjection = methodInjection;
    }

    @Inject static void subtypeStaticMethodInjection(FuelTank methodInjection) {
        if (!hasBeenStaticFieldInjected()) {
            staticMethodInjectedBeforeStaticFields = true;
        }
        staticMethodInjection = methodInjection;
    }

    @Inject private void injectPrivateMethod() {
        if (subPrivateMethodInjected) {
            similarPrivateMethodInjectedTwice = true;
        }
        subPrivateMethodInjected = true;
    }

    @Inject void injectPackagePrivateMethod() {
        if (subPackagePrivateMethodInjected) {
            similarPackagePrivateMethodInjectedTwice = true;
        }
        subPackagePrivateMethodInjected = true;
    }

    @Inject protected void injectProtectedMethod() {
        if (subProtectedMethodInjected) {
            overriddenProtectedMethodInjectedTwice = true;
        }
        subProtectedMethodInjected = true;
    }

    @Inject
    public void injectPublicMethod() {
        if (subPublicMethodInjected) {
            overriddenPublicMethodInjectedTwice = true;
        }
        subPublicMethodInjected = true;
    }

    private void injectPrivateMethodForOverride() {
        superPrivateMethodForOverrideInjected = true;
    }

    void injectPackagePrivateMethodForOverride() {
        superPackagePrivateMethodForOverrideInjected = true;
    }

    protected void injectProtectedMethodForOverride() {
        protectedMethodForOverrideInjected = true;
    }

    public void injectPublicMethodForOverride() {
        publicMethodForOverrideInjected = true;
    }

    public boolean hasSpareTireBeenFieldInjected() {
        return fieldInjection != NEVER_INJECTED;
    }

    public boolean hasSpareTireBeenMethodInjected() {
        return methodInjection != NEVER_INJECTED;
    }

    public static boolean hasBeenStaticFieldInjected() {
        return staticFieldInjection != NEVER_INJECTED;
    }

    public static boolean hasBeenStaticMethodInjected() {
        return staticMethodInjection != NEVER_INJECTED;
    }

    private boolean spareTirePackagePrivateMethod2Injected;

    public boolean getSpareTirePackagePrivateMethod2Injected() {
        return spareTirePackagePrivateMethod2Injected;
    }

//    @Override
    @Inject void injectPackagePrivateMethod2() {
        spareTirePackagePrivateMethod2Injected = true;
    }

    private boolean spareTirePackagePrivateMethod3Injected;

    public boolean getSpareTirePackagePrivateMethod3Injected() {
        return spareTirePackagePrivateMethod3Injected;
    }

    void injectPackagePrivateMethod3() {
        spareTirePackagePrivateMethod3Injected = true;
    }
}
