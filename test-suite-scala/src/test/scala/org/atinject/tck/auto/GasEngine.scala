package org.atinject.tck.auto

import javax.inject.Inject

abstract class GasEngine extends Engine {
  override def injectTwiceOverriddenWithOmissionInMiddle(): Unit = {
    overriddenTwiceWithOmissionInMiddleInjected = true
  }

  @Inject override def injectTwiceOverriddenWithOmissionInSubclass(): Unit = {
    overriddenTwiceWithOmissionInSubclassInjected = true
  }
}
