package org.atinject.tck.auto.accessories

import javax.inject.Inject

class RoundThing {
  private var roundThingPackagePrivateMethod2Injected = false

  def getRoundThingPackagePrivateMethod2Injected: Boolean = roundThingPackagePrivateMethod2Injected

  @Inject private[accessories] def injectPackagePrivateMethod2(): Unit = {
    roundThingPackagePrivateMethod2Injected = true
  }

  private var roundThingPackagePrivateMethod3Injected = false

  def getRoundThingPackagePrivateMethod3Injected: Boolean = roundThingPackagePrivateMethod3Injected

  @Inject private[accessories] def injectPackagePrivateMethod3(): Unit = {
    roundThingPackagePrivateMethod3Injected = true
  }

  private var roundThingPackagePrivateMethod4Injected = false

  def getRoundThingPackagePrivateMethod4Injected: Boolean = roundThingPackagePrivateMethod4Injected

  @Inject private[accessories] def injectPackagePrivateMethod4(): Unit = {
    roundThingPackagePrivateMethod4Injected = true
  }
}

