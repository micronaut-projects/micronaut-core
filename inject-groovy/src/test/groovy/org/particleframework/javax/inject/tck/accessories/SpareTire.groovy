package org.particleframework.javax.inject.tck.accessories

import groovy.transform.PackageScope
import org.particleframework.javax.inject.tck.FuelTank
import org.particleframework.javax.inject.tck.Tire

import javax.inject.Inject

class SpareTire extends Tire {

    FuelTank constructorInjection = NEVER_INJECTED
    @Inject protected FuelTank fieldInjection = NEVER_INJECTED
    FuelTank methodInjection = NEVER_INJECTED
    @Inject static FuelTank staticFieldInjection = NEVER_INJECTED
    static FuelTank staticMethodInjection = NEVER_INJECTED

    @Inject
    SpareTire(FuelTank forSupertype, FuelTank forSubtype) {
        super(forSupertype)
        this.constructorInjection = forSubtype
    }

    @Inject void subtypeMethodInjection(FuelTank methodInjection) {
        if (!hasSpareTireBeenFieldInjected()) {
            methodInjectedBeforeFields = true
        }
        this.methodInjection = methodInjection
    }

    @Inject static void subtypeStaticMethodInjection(FuelTank methodInjection) {
        if (!hasBeenStaticFieldInjected()) {
            staticMethodInjectedBeforeStaticFields = true
        }
        staticMethodInjection = methodInjection
    }

    @Inject private void injectPrivateMethod() {
        if (subPrivateMethodInjected) {
            similarPrivateMethodInjectedTwice = true
        }
        subPrivateMethodInjected = true
    }

    @Inject @PackageScope void injectPackagePrivateMethod() {
        if (subPackagePrivateMethodInjected) {
            similarPackagePrivateMethodInjectedTwice = true
        }
        subPackagePrivateMethodInjected = true
    }

    @Inject protected void injectProtectedMethod() {
        if (subProtectedMethodInjected) {
            overriddenProtectedMethodInjectedTwice = true
        }
        subProtectedMethodInjected = true
    }

    @Inject
    void injectPublicMethod() {
        if (subPublicMethodInjected) {
            overriddenPublicMethodInjectedTwice = true
        }
        subPublicMethodInjected = true
    }

    private void injectPrivateMethodForOverride() {
        superPrivateMethodForOverrideInjected = true
    }

    @PackageScope void injectPackagePrivateMethodForOverride() {
        superPackagePrivateMethodForOverrideInjected = true
    }

    protected void injectProtectedMethodForOverride() {
        protectedMethodForOverrideInjected = true
    }

    void injectPublicMethodForOverride() {
        publicMethodForOverrideInjected = true
    }

    boolean hasSpareTireBeenFieldInjected() {
        return fieldInjection != NEVER_INJECTED
    }

    boolean hasSpareTireBeenMethodInjected() {
        return methodInjection != NEVER_INJECTED
    }

    static boolean hasBeenStaticFieldInjected() {
        return staticFieldInjection != NEVER_INJECTED
    }

    static boolean hasBeenStaticMethodInjected() {
        return staticMethodInjection != NEVER_INJECTED
    }

    private boolean spareTirePackagePrivateMethod2Injected

    boolean getSpareTirePackagePrivateMethod2Injected() {
        return spareTirePackagePrivateMethod2Injected
    }

    @Override @Inject @PackageScope void injectPackagePrivateMethod2() {
        spareTirePackagePrivateMethod2Injected = true
    }

    private boolean spareTirePackagePrivateMethod3Injected

    boolean getSpareTirePackagePrivateMethod3Injected() {
        return spareTirePackagePrivateMethod3Injected
    }

    @PackageScope void injectPackagePrivateMethod3() {
        spareTirePackagePrivateMethod3Injected = true
    }
}
