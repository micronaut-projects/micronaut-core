package org.atinject.tck.auto.accessories;


import javax.inject.Inject;

public class RoundThing {

    private boolean roundThingPackagePrivateMethod2Injected;

    public boolean getRoundThingPackagePrivateMethod2Injected() {
        return roundThingPackagePrivateMethod2Injected;
    }

    @Inject void injectPackagePrivateMethod2() {
        roundThingPackagePrivateMethod2Injected = true;
    }

    private boolean roundThingPackagePrivateMethod3Injected;

    public boolean getRoundThingPackagePrivateMethod3Injected() {
        return roundThingPackagePrivateMethod3Injected;
    }

    @Inject void injectPackagePrivateMethod3() {
        roundThingPackagePrivateMethod3Injected = true;
    }

    private boolean roundThingPackagePrivateMethod4Injected;

    public boolean getRoundThingPackagePrivateMethod4Injected() {
        return roundThingPackagePrivateMethod4Injected;
    }

    @Inject void injectPackagePrivateMethod4() {
        roundThingPackagePrivateMethod4Injected = true;
    }
}
