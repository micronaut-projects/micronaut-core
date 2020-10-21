package io.micronaut.inject.inheritance

import io.micronaut.context.DefaultBeanContext
import org.junit.jupiter.api.Test
import javax.inject.{Inject, Singleton}
import org.assertj.core.api.Assertions.assertThat

@Singleton class A

abstract class AbstractB { // inject via field
  @Inject protected var a: A = null
  private var another:A = null
  private var packagePrivate:A = null

  // inject via method
  @Inject def setAnother(a: A): Unit = {
    this.another = a
  }

  // inject via package private method
  @Inject private[inheritance] def setPackagePrivate(a: A): Unit = {
    this.packagePrivate = a
  }

  def getA: A = a

  def getAnother: A = another

  private[inheritance] def getPackagePrivate = packagePrivate
}

@Singleton
class B extends AbstractB

class AbstractInheritanceSpec {

  @Test
  def `test values are injected for abstract parent class`():Unit = {
    val context = new DefaultBeanContext().start()

    val b = context.getBean(classOf[B])

    assertThat(b.getA).isNotNull
    assertThat(b.getAnother).isNotNull
    assertThat(b.getA).isSameAs(b.getAnother)
    assertThat(b.getPackagePrivate).isNotNull
    assertThat(b.getPackagePrivate).isSameAs(b.getAnother)
  }
}
