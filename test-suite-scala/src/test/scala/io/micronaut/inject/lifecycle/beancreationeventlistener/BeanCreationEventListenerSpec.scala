package io.micronaut.inject.lifecycle.beancreationeventlistener

import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.event.{BeanCreatedEvent, BeanCreatedEventListener}
import javax.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@Singleton class B {
  private[beancreationeventlistener] var name:String = null

  def getName: String = name

  def setName(name: String): Unit = {
    this.name = name
  }
}

@Singleton class BCreationListener extends BeanCreatedEventListener[B] {
  override def onCreated(event: BeanCreatedEvent[B]): B = {
    val childB = new ChildB(event.getBean)
    childB.name = "good"
    childB
  }
}

class ChildB(var original: B) extends B {
  def getOriginal: B = original

  def setOriginal(original: B): Unit = {
    this.original = original
  }
}

class BeanCreationEventListenerSpec {
  @Test
  def `test bean creation listener`():Unit = {
    val context = new DefaultBeanContext().start()

    val b= context.getBean(classOf[B])

    assertThat(b.isInstanceOf[ChildB]).isTrue
    assertThat(b.name).isEqualTo("good")
  }
}
