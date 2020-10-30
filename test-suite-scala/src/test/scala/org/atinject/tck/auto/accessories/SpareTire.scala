package org.atinject.tck.auto.accessories

import org.atinject.tck.auto.FuelTank
import org.atinject.tck.auto.Tire
import javax.inject.Inject


//object SpareTire {
//  @Inject private[accessories] val staticFieldInjection = Tire.NEVER_INJECTED
//  private[accessories] var staticMethodInjection = Tire.NEVER_INJECTED
//
//  @Inject private[accessories] def subtypeStaticMethodInjection(methodInjection: FuelTank): Unit = {
//    if (!hasBeenStaticFieldInjected) staticMethodInjectedBeforeStaticFields = true
//    staticMethodInjection = methodInjection
//  }
//
//  def hasBeenStaticFieldInjected: Boolean = staticFieldInjection ne Tire.NEVER_INJECTED
//
//  def hasBeenStaticMethodInjected: Boolean = staticMethodInjection ne Tire.NEVER_INJECTED
//}

@Inject
class SpareTire(val forSupertype: FuelTank, override val constructorInjection: FuelTank) extends Tire(forSupertype) {
//    @Inject override protected var fieldInjection: FuelTank = Tire.NEVER_INJECTED
    //override private[auto] var methodInjection: FuelTank = Tire.NEVER_INJECTED
    @Inject private[accessories] def subtypeMethodInjection (methodInjection: FuelTank): Unit = {
      if (! (hasSpareTireBeenFieldInjected) ) {
        methodInjectedBeforeFields = true
      }
      this.methodInjection = methodInjection
    }
    @Inject private def injectPrivateMethod (): Unit = {
      if (subPrivateMethodInjected) {
        similarPrivateMethodInjectedTwice = true
      }
      subPrivateMethodInjected = true
    }
    @Inject override private[auto] def injectPackagePrivateMethod(): Unit = {
      if (subPackagePrivateMethodInjected) {
        similarPackagePrivateMethodInjectedTwice = true
      }
      subPackagePrivateMethodInjected = true
    }
    @Inject override protected def injectProtectedMethod (): Unit = {
      if (subProtectedMethodInjected) {
        overriddenProtectedMethodInjectedTwice = true
      }
      subProtectedMethodInjected = true
    }
    @Inject override def injectPublicMethod (): Unit = {
      if (subPublicMethodInjected) {
        overriddenPublicMethodInjectedTwice = true
      }
      subPublicMethodInjected = true
    }
    private def injectPrivateMethodForOverride (): Unit = {
      superPrivateMethodForOverrideInjected = true
    }
    override private[auto] def injectPackagePrivateMethodForOverride(): Unit = {
      superPackagePrivateMethodForOverrideInjected = true
    }
    override protected def injectProtectedMethodForOverride (): Unit = {
      protectedMethodForOverrideInjected = true
    }
    override def injectPublicMethodForOverride (): Unit = {
      publicMethodForOverrideInjected = true
    }
    override def hasSpareTireBeenFieldInjected: Boolean = {
      return fieldInjection ne Tire.NEVER_INJECTED
    }
    override def hasSpareTireBeenMethodInjected: Boolean = {
      return methodInjection ne Tire.NEVER_INJECTED
    }
    private var spareTirePackagePrivateMethod2Injected: Boolean = false
    def getSpareTirePackagePrivateMethod2Injected: Boolean = {
      return spareTirePackagePrivateMethod2Injected
    }
    @Inject override private[auto] def injectPackagePrivateMethod2(): Unit = {
      spareTirePackagePrivateMethod2Injected = true
    }
    private var spareTirePackagePrivateMethod3Injected: Boolean = false
    def getSpareTirePackagePrivateMethod3Injected: Boolean = {
      return spareTirePackagePrivateMethod3Injected
    }
    override private[auto] def injectPackagePrivateMethod3(): Unit = {
      spareTirePackagePrivateMethod3Injected = true
    }
}
