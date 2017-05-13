package org.particleframework.tck.accessories

import groovy.transform.PackageScope

import javax.inject.Inject

class RoundThing {

    public boolean packagePrivateMethod2Injected

    @Inject @PackageScope void injectPackagePrivateMethod2() {
        packagePrivateMethod2Injected = true
    }

    public boolean packagePrivateMethod3Injected

    @Inject @PackageScope  void injectPackagePrivateMethod3() {
        packagePrivateMethod3Injected = true
    }

    public boolean packagePrivateMethod4Injected

    @Inject @PackageScope void injectPackagePrivateMethod4() {
        packagePrivateMethod4Injected = true
    }
}
