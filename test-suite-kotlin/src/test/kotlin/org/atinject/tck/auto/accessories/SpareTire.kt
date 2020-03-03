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
