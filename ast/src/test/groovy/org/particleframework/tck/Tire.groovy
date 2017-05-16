package org.particleframework.tck

import groovy.transform.PackageScope
import org.particleframework.tck.accessories.RoundThing
import org.particleframework.tck.accessories.SpareTire

import javax.inject.Inject

class Tire extends RoundThing {

    protected static final FuelTank NEVER_INJECTED = new FuelTank()

    protected static final Set<String> moreProblems = new LinkedHashSet<String>()

    FuelTank constructorInjection = NEVER_INJECTED
    @Inject protected FuelTank fieldInjection = NEVER_INJECTED
    FuelTank methodInjection = NEVER_INJECTED
    @Inject static FuelTank staticFieldInjection = NEVER_INJECTED
    static FuelTank staticMethodInjection = NEVER_INJECTED

    boolean constructorInjected

    protected boolean superPrivateMethodInjected
    protected boolean superPackagePrivateMethodInjected
    protected boolean superProtectedMethodInjected
    protected boolean superPublicMethodInjected
    protected boolean subPrivateMethodInjected
    protected boolean subPackagePrivateMethodInjected
    protected boolean subProtectedMethodInjected
    protected boolean subPublicMethodInjected

    protected boolean superPrivateMethodForOverrideInjected
    protected boolean superPackagePrivateMethodForOverrideInjected
    protected boolean subPrivateMethodForOverrideInjected
    protected boolean subPackagePrivateMethodForOverrideInjected
    protected boolean protectedMethodForOverrideInjected
    protected boolean publicMethodForOverrideInjected

    public boolean methodInjectedBeforeFields
    public boolean subtypeFieldInjectedBeforeSupertypeMethods
    public boolean subtypeMethodInjectedBeforeSupertypeMethods
    public static boolean staticMethodInjectedBeforeStaticFields
    public static boolean subtypeStaticFieldInjectedBeforeSupertypeStaticMethods
    public static boolean subtypeStaticMethodInjectedBeforeSupertypeStaticMethods
    public boolean similarPrivateMethodInjectedTwice
    public boolean similarPackagePrivateMethodInjectedTwice
    public boolean overriddenProtectedMethodInjectedTwice
    public boolean overriddenPublicMethodInjectedTwice

    @Inject
    Tire(FuelTank constructorInjection) {
        this.constructorInjection = constructorInjection
    }

    @Inject void supertypeMethodInjection(FuelTank methodInjection) {
        if (!hasTireBeenFieldInjected()) {
            methodInjectedBeforeFields = true
        }
        if (hasSpareTireBeenFieldInjected()) {
            subtypeFieldInjectedBeforeSupertypeMethods = true
        }
        if (hasSpareTireBeenMethodInjected()) {
            subtypeMethodInjectedBeforeSupertypeMethods = true
        }
        this.methodInjection = methodInjection
    }

    @Inject static void supertypeStaticMethodInjection(FuelTank methodInjection) {
        if (!Tire.hasBeenStaticFieldInjected()) {
            staticMethodInjectedBeforeStaticFields = true
        }
        if (SpareTire.hasBeenStaticFieldInjected()) {
            subtypeStaticFieldInjectedBeforeSupertypeStaticMethods = true
        }
        if (SpareTire.hasBeenStaticMethodInjected()) {
            subtypeStaticMethodInjectedBeforeSupertypeStaticMethods = true
        }
        staticMethodInjection = methodInjection
    }

    @Inject private void injectPrivateMethod() {
        if (superPrivateMethodInjected) {
            similarPrivateMethodInjectedTwice = true
        }
        superPrivateMethodInjected = true
    }

    @Inject @PackageScope void injectPackagePrivateMethod() {
        if (superPackagePrivateMethodInjected) {
            similarPackagePrivateMethodInjectedTwice = true
        }
        superPackagePrivateMethodInjected = true
    }

    @Inject protected void injectProtectedMethod() {
        if (superProtectedMethodInjected) {
            overriddenProtectedMethodInjectedTwice = true
        }
        superProtectedMethodInjected = true
    }

    @Inject
    void injectPublicMethod() {
        if (superPublicMethodInjected) {
            overriddenPublicMethodInjectedTwice = true
        }
        superPublicMethodInjected = true
    }

    @Inject private void injectPrivateMethodForOverride() {
        subPrivateMethodForOverrideInjected = true
    }

    @Inject @PackageScope void injectPackagePrivateMethodForOverride() {
        subPackagePrivateMethodForOverrideInjected = true
    }

    @Inject protected void injectProtectedMethodForOverride() {
        protectedMethodForOverrideInjected = true
    }

    @Inject
    void injectPublicMethodForOverride() {
        publicMethodForOverrideInjected = true
    }

    protected final boolean hasTireBeenFieldInjected() {
        return fieldInjection != NEVER_INJECTED
    }

    protected boolean hasSpareTireBeenFieldInjected() {
        return false
    }

    protected final boolean hasTireBeenMethodInjected() {
        return methodInjection != NEVER_INJECTED
    }

    protected static boolean hasBeenStaticFieldInjected() {
        return staticFieldInjection != NEVER_INJECTED
    }

    protected static boolean hasBeenStaticMethodInjected() {
        return staticMethodInjection != NEVER_INJECTED
    }

    protected boolean hasSpareTireBeenMethodInjected() {
        return false
    }

    boolean tirePackagePrivateMethod2Injected

    boolean getTirePackagePrivateMethod2Injected() {
        return tirePackagePrivateMethod2Injected
    }

    @Inject @PackageScope void injectPackagePrivateMethod2() {
        tirePackagePrivateMethod2Injected = true
    }

    public boolean tirePackagePrivateMethod3Injected

    boolean getTirePackagePrivateMethod3Injected() {
        return tirePackagePrivateMethod3Injected
    }

    @Inject @PackageScope void injectPackagePrivateMethod3() {
        tirePackagePrivateMethod3Injected = true
    }

    public boolean tirePackagePrivateMethod4Injected

    boolean getTirePackagePrivateMethod4Injected() {
        return tirePackagePrivateMethod4Injected
    }

    @PackageScope
    void injectPackagePrivateMethod4() {
        tirePackagePrivateMethod4Injected = true
    }
}