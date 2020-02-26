/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atinject.tck.auto

import org.atinject.tck.auto.accessories.RoundThing
import org.atinject.tck.auto.accessories.SpareTire

import javax.inject.Inject
import java.util.LinkedHashSet

open class Tire @Inject
constructor(constructorInjection: FuelTank) : RoundThing() {

    internal open var constructorInjection = NEVER_INJECTED
    @Inject protected open var fieldInjection = NEVER_INJECTED
    internal open var methodInjection = NEVER_INJECTED

    internal var constructorInjected: Boolean = false

    var superPrivateMethodInjected: Boolean = false
    var superPackagePrivateMethodInjected: Boolean = false
    var superProtectedMethodInjected: Boolean = false
    var superPublicMethodInjected: Boolean = false
    var subPrivateMethodInjected: Boolean = false
    var subPackagePrivateMethodInjected: Boolean = false
    var subProtectedMethodInjected: Boolean = false
    var subPublicMethodInjected: Boolean = false

    var superPrivateMethodForOverrideInjected: Boolean = false
    var superPackagePrivateMethodForOverrideInjected: Boolean = false
    var subPrivateMethodForOverrideInjected: Boolean = false
    var subPackagePrivateMethodForOverrideInjected: Boolean = false
    var protectedMethodForOverrideInjected: Boolean = false
    var publicMethodForOverrideInjected: Boolean = false

    var methodInjectedBeforeFields: Boolean = false
    var subtypeFieldInjectedBeforeSupertypeMethods: Boolean = false
    var subtypeMethodInjectedBeforeSupertypeMethods: Boolean = false
    var similarPrivateMethodInjectedTwice: Boolean = false
    var similarPackagePrivateMethodInjectedTwice: Boolean = false
    var overriddenProtectedMethodInjectedTwice: Boolean = false
    var overriddenPublicMethodInjectedTwice: Boolean = false

    var tirePackagePrivateMethod2Injected: Boolean = false

    var tirePackagePrivateMethod3Injected: Boolean = false

    var tirePackagePrivateMethod4Injected: Boolean = false

    init {
        this.constructorInjection = constructorInjection
    }

    @Inject internal fun supertypeMethodInjection(methodInjection: FuelTank) {
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

    @Inject private fun injectPrivateMethod() {
        if (superPrivateMethodInjected) {
            similarPrivateMethodInjectedTwice = true
        }
        superPrivateMethodInjected = true
    }

    @Inject internal open fun injectPackagePrivateMethod() {
        if (superPackagePrivateMethodInjected) {
            similarPackagePrivateMethodInjectedTwice = true
        }
        superPackagePrivateMethodInjected = true
    }

    @Inject protected open fun injectProtectedMethod() {
        if (superProtectedMethodInjected) {
            overriddenProtectedMethodInjectedTwice = true
        }
        superProtectedMethodInjected = true
    }

    @Inject
    open fun injectPublicMethod() {
        if (superPublicMethodInjected) {
            overriddenPublicMethodInjectedTwice = true
        }
        superPublicMethodInjected = true
    }

    @Inject private fun injectPrivateMethodForOverride() {
        subPrivateMethodForOverrideInjected = true
    }

    @Inject internal open fun injectPackagePrivateMethodForOverride() {
        subPackagePrivateMethodForOverrideInjected = true
    }

    @Inject protected open fun injectProtectedMethodForOverride() {
        protectedMethodForOverrideInjected = true
    }

    @Inject
    open fun injectPublicMethodForOverride() {
        publicMethodForOverrideInjected = true
    }

    fun hasTireBeenFieldInjected(): Boolean {
        return fieldInjection != NEVER_INJECTED
    }

    protected open fun hasSpareTireBeenFieldInjected(): Boolean {
        return false
    }

    fun hasTireBeenMethodInjected(): Boolean {
        return methodInjection != NEVER_INJECTED
    }

    protected open fun hasSpareTireBeenMethodInjected(): Boolean {
        return false
    }

    @Inject override fun injectPackagePrivateMethod2() {
        tirePackagePrivateMethod2Injected = true
    }

    @Inject override fun injectPackagePrivateMethod3() {
        tirePackagePrivateMethod3Injected = true
    }

    override fun injectPackagePrivateMethod4() {
        tirePackagePrivateMethod4Injected = true
    }

    companion object {

        val NEVER_INJECTED = FuelTank()

        protected val moreProblems: Set<String> = LinkedHashSet()
        @Inject internal var staticFieldInjection = NEVER_INJECTED
        internal var staticMethodInjection = NEVER_INJECTED
        var staticMethodInjectedBeforeStaticFields: Boolean = false
        var subtypeStaticFieldInjectedBeforeSupertypeStaticMethods: Boolean = false
        var subtypeStaticMethodInjectedBeforeSupertypeStaticMethods: Boolean = false

        @Inject internal fun supertypeStaticMethodInjection(methodInjection: FuelTank) {
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

        protected fun hasBeenStaticFieldInjected(): Boolean {
            return staticFieldInjection != NEVER_INJECTED
        }

        protected fun hasBeenStaticMethodInjected(): Boolean {
            return staticMethodInjection != NEVER_INJECTED
        }
    }
}
