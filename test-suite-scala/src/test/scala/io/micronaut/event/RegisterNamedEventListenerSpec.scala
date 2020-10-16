package io.micronaut.event

import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.{ApplicationEventListener, StartupEvent}
import io.micronaut.inject.qualifiers.Qualifiers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RegisterNamedEventListenerSpec {

  @Test
  def `test register event listener singleton`(): Unit = {
    val context = ApplicationContext.run()
    context.getBeansOfType(classOf[ApplicationEventListener[_]])
    context.registerSingleton(classOf[ApplicationEventListener[_]],
      new MyEventListener, Qualifiers.byName("foo"))

    val listener = context.getBean(classOf[ApplicationEventListener[_]])

    assertThat(listener.isInstanceOf[MyEventListener]).isTrue
    assertThat(context.getBeansOfType(classOf[ApplicationEventListener[_]])).contains(listener)

    val event = new StartupEvent(context)
    context.publishEvent(event)

    assertThat(listener.asInstanceOf[MyEventListener].lastEvent).isSameAs(event)
  }
}

class MyEventListener extends ApplicationEventListener[Any] {
  var lastEvent:Any = null
  /**
   * Handle an application event.
   *
   * @param event the event to respond to
   */
  override def onApplicationEvent(event: scala.Any): Unit = lastEvent = event
}
