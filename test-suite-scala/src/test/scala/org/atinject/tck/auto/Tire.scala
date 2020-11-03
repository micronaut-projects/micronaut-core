package org.atinject.tck.auto

import javax.inject.Inject
import java.util

object Tire {
  val NEVER_INJECTED = new FuelTank
  protected val moreProblems = new util.LinkedHashSet[String]
  @Inject private[auto] val staticFieldInjection = NEVER_INJECTED
  private[auto] var staticMethodInjection = NEVER_INJECTED
  var staticMethodInjectedBeforeStaticFields = false
  var subtypeStaticFieldInjectedBeforeSupertypeStaticMethods = false
  var subtypeStaticMethodInjectedBeforeSupertypeStaticMethods = false

  //  @Inject private[auto] def supertypeStaticMethodInjection(methodInjection: FuelTank): Unit = {
  //    if (!Tire.hasBeenStaticFieldInjected) staticMethodInjectedBeforeStaticFields = true
  //    if (SpareTire.hasBeenStaticFieldInjected) subtypeStaticFieldInjectedBeforeSupertypeStaticMethods = true
  //    if (SpareTire.hasBeenStaticMethodInjected) subtypeStaticMethodInjectedBeforeSupertypeStaticMethods = true
  //    staticMethodInjection = methodInjection
  //  }

  protected def hasBeenStaticFieldInjected: Boolean = staticFieldInjection ne NEVER_INJECTED

  protected def hasBeenStaticMethodInjected: Boolean = staticMethodInjection ne NEVER_INJECTED
}

@Inject
class Tire (val constructorInjection: FuelTank) extends accessories.RoundThing {
  @Inject protected var fieldInjection: FuelTank = Tire.NEVER_INJECTED
  private[auto] var methodInjection: FuelTank = Tire.NEVER_INJECTED
  private[auto] val constructorInjected: Boolean = false
  protected var superPrivateMethodInjected: Boolean = false
  private[auto] var superPackagePrivateMethodInjected: Boolean = false
  private[auto] var superProtectedMethodInjected: Boolean = false
  private[auto] var superPublicMethodInjected: Boolean = false
  protected var subPrivateMethodInjected: Boolean = false
  private[auto] var subPackagePrivateMethodInjected: Boolean = false
  private[auto] var subProtectedMethodInjected: Boolean = false
  private[auto] var subPublicMethodInjected: Boolean = false
  private[auto] var superPrivateMethodForOverrideInjected: Boolean = false
  private[auto] var superPackagePrivateMethodForOverrideInjected: Boolean = false
  protected var subPrivateMethodForOverrideInjected: Boolean = false
  private[auto] var subPackagePrivateMethodForOverrideInjected: Boolean = false
  private[auto] var protectedMethodForOverrideInjected: Boolean = false
  private[auto] var publicMethodForOverrideInjected: Boolean = false
  var methodInjectedBeforeFields: Boolean = false
  var subtypeFieldInjectedBeforeSupertypeMethods: Boolean = false
  var subtypeMethodInjectedBeforeSupertypeMethods: Boolean = false
  var similarPrivateMethodInjectedTwice: Boolean = false
  var similarPackagePrivateMethodInjectedTwice: Boolean = false
  var overriddenProtectedMethodInjectedTwice: Boolean = false
  var overriddenPublicMethodInjectedTwice: Boolean = false

  @Inject private[auto] def supertypeMethodInjection (methodInjection: FuelTank): Unit = {
    if (! (hasTireBeenFieldInjected) ) {
      methodInjectedBeforeFields = true
    }
    if (hasSpareTireBeenFieldInjected) {
      subtypeFieldInjectedBeforeSupertypeMethods = true
    }
    if (hasSpareTireBeenMethodInjected) {
      subtypeMethodInjectedBeforeSupertypeMethods = true
    }
    this.methodInjection = methodInjection
  }

  @Inject private def injectPrivateMethod (): Unit = {
    if (superPrivateMethodInjected) {
      similarPrivateMethodInjectedTwice = true
    }
    superPrivateMethodInjected = true
  }

  @Inject private[auto] def injectPackagePrivateMethod (): Unit = {
    if (superPackagePrivateMethodInjected) {
      similarPackagePrivateMethodInjectedTwice = true
    }
    superPackagePrivateMethodInjected = true
  }

  @Inject protected def injectProtectedMethod (): Unit = {
    if (superProtectedMethodInjected) {
      overriddenProtectedMethodInjectedTwice = true
    }
    superProtectedMethodInjected = true
  }

  @Inject def injectPublicMethod (): Unit = {
    if (superPublicMethodInjected) {
      overriddenPublicMethodInjectedTwice = true
    }
    superPublicMethodInjected = true
  }

  @Inject private def injectPrivateMethodForOverride (): Unit = {
    subPrivateMethodForOverrideInjected = true
  }

  @Inject private[auto] def injectPackagePrivateMethodForOverride (): Unit = {
    subPackagePrivateMethodForOverrideInjected = true
  }

  @Inject protected def injectProtectedMethodForOverride (): Unit = {
    protectedMethodForOverrideInjected = true
  }

  @Inject def injectPublicMethodForOverride (): Unit = {
    publicMethodForOverrideInjected = true
  }

  final private[auto] def hasTireBeenFieldInjected: Boolean = fieldInjection ne Tire.NEVER_INJECTED

  protected def hasSpareTireBeenFieldInjected: Boolean = false

  final private[auto] def hasTireBeenMethodInjected: Boolean = methodInjection ne Tire.NEVER_INJECTED

  protected def hasSpareTireBeenMethodInjected: Boolean = false

  var tirePackagePrivateMethod2Injected: Boolean = false

  def getTirePackagePrivateMethod2Injected: Boolean = tirePackagePrivateMethod2Injected

  @Inject private[auto] override def injectPackagePrivateMethod2 (): Unit = {
    tirePackagePrivateMethod2Injected = true
  }
  var tirePackagePrivateMethod3Injected: Boolean = false

  def getTirePackagePrivateMethod3Injected: Boolean = tirePackagePrivateMethod3Injected

  @Inject private[auto] override def injectPackagePrivateMethod3 (): Unit = {
    tirePackagePrivateMethod3Injected = true
  }

  var tirePackagePrivateMethod4Injected: Boolean = false

  def getTirePackagePrivateMethod4Injected: Boolean = tirePackagePrivateMethod4Injected

  private[auto] override def injectPackagePrivateMethod4 (): Unit = {
    tirePackagePrivateMethod4Injected = true
  }
}
