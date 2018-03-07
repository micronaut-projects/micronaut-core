package io.micronaut.javax.inject.tck.accessories

import groovy.transform.PackageScope

import javax.inject.Inject

class RoundThing {

    private boolean roundThingPackagePrivateMethod2Injected

    boolean getRoundThingPackagePrivateMethod2Injected() {
        return roundThingPackagePrivateMethod2Injected
    }

    @Inject @PackageScope void injectPackagePrivateMethod2() {
        roundThingPackagePrivateMethod2Injected = true
    }

    private  boolean roundThingPackagePrivateMethod3Injected

    boolean getRoundThingPackagePrivateMethod3Injected() {
        return roundThingPackagePrivateMethod3Injected
    }

    @Inject @PackageScope void injectPackagePrivateMethod3() {
        roundThingPackagePrivateMethod3Injected = true
    }

    private  boolean roundThingPackagePrivateMethod4Injected

    boolean getRoundThingPackagePrivateMethod4Injected() {
        return roundThingPackagePrivateMethod4Injected
    }

    @Inject @PackageScope void injectPackagePrivateMethod4() {
        roundThingPackagePrivateMethod4Injected = true
    }
}
