package org.atinject.tck.auto.accessories


import javax.inject.Inject

open class RoundThing {

    var roundThingPackagePrivateMethod2Injected: Boolean = false
        private set

    var roundThingPackagePrivateMethod3Injected: Boolean = false
        private set

    var roundThingPackagePrivateMethod4Injected: Boolean = false
        private set

    @Inject open internal fun injectPackagePrivateMethod2() {
        roundThingPackagePrivateMethod2Injected = true
    }

    @Inject open internal fun injectPackagePrivateMethod3() {
        roundThingPackagePrivateMethod3Injected = true
    }

    @Inject open internal fun injectPackagePrivateMethod4() {
        roundThingPackagePrivateMethod4Injected = true
    }
}
