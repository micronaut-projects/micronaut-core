package org.atinject.tck.auto.accessories

import org.atinject.tck.auto.FuelTank
import org.atinject.tck.auto.Tire

import javax.inject.Inject

open class SpareTire @Inject
constructor(forSupertype: FuelTank, forSubtype: FuelTank) : Tire(forSupertype) {

    override var constructorInjection = Tire.NEVER_INJECTED
    @Inject override var fieldInjection = Tire.NEVER_INJECTED
    override var methodInjection = Tire.NEVER_INJECTED

    var spareTirePackagePrivateMethod2Injected: Boolean = false
        private set

    var spareTirePackagePrivateMethod3Injected: Boolean = false
        private set

    init {
        this.constructorInjection = forSubtype
    }

    @Inject internal fun subtypeMethodInjection(methodInjection: FuelTank) {
        if (!hasSpareTireBeenFieldInjected()) {
            methodInjectedBeforeFields = true
        }
        this.methodInjection = methodInjection
    }

    @Inject private fun injectPrivateMethod() {
        if (subPrivateMethodInjected) {
            similarPrivateMethodInjectedTwice = true
        }
        subPrivateMethodInjected = true
    }

    @Inject override fun injectPackagePrivateMethod() {
        if (subPackagePrivateMethodInjected) {
            similarPackagePrivateMethodInjectedTwice = true
        }
        subPackagePrivateMethodInjected = true
    }

    @Inject override fun injectProtectedMethod() {
        if (subProtectedMethodInjected) {
            overriddenProtectedMethodInjectedTwice = true
        }
        subProtectedMethodInjected = true
    }

    @Inject
    override fun injectPublicMethod() {
        if (subPublicMethodInjected) {
            overriddenPublicMethodInjectedTwice = true
        }
        subPublicMethodInjected = true
    }

    private fun injectPrivateMethodForOverride() {
        superPrivateMethodForOverrideInjected = true
    }

    override fun injectPackagePrivateMethodForOverride() {
        superPackagePrivateMethodForOverrideInjected = true
    }

    override fun injectProtectedMethodForOverride() {
        protectedMethodForOverrideInjected = true
    }

    override fun injectPublicMethodForOverride() {
        publicMethodForOverrideInjected = true
    }

    public override fun hasSpareTireBeenFieldInjected(): Boolean {
        return fieldInjection !== Tire.NEVER_INJECTED
    }

    @Override
    public override fun hasSpareTireBeenMethodInjected(): Boolean {
        return methodInjection !== Tire.NEVER_INJECTED
    }

    @Inject override fun injectPackagePrivateMethod2() {
        spareTirePackagePrivateMethod2Injected = true
    }

    override fun injectPackagePrivateMethod3() {
        spareTirePackagePrivateMethod3Injected = true
    }

    companion object {
        @Inject internal var staticFieldInjection = Tire.NEVER_INJECTED
        internal var staticMethodInjection = Tire.NEVER_INJECTED

        @Inject internal fun subtypeStaticMethodInjection(methodInjection: FuelTank) {
            if (!hasBeenStaticFieldInjected()) {
                Tire.staticMethodInjectedBeforeStaticFields = true
            }
            staticMethodInjection = methodInjection
        }

        fun hasBeenStaticFieldInjected(): Boolean {
            return staticFieldInjection !== Tire.NEVER_INJECTED
        }

        fun hasBeenStaticMethodInjected(): Boolean {
            return staticMethodInjection !== Tire.NEVER_INJECTED
        }
    }
}
