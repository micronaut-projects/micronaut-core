package io.micronaut.inject.factory.beanwithfactory

import io.micronaut.context.event.{BeanCreatedEvent, BeanCreatedEventListener, BeanInitializedEventListener, BeanInitializingEvent}
import javax.inject.Singleton

@Singleton
class DualListener extends BeanCreatedEventListener[BFactory] with BeanInitializedEventListener [BFactory ]{
  var initialized = false
  var created = false

  override def onCreated(event: BeanCreatedEvent[BFactory]): BFactory = {
    this.created = true
    event.getBean
  }

  override def onInitialized(event: BeanInitializingEvent[BFactory]): BFactory = {
    this.initialized = true
    event.getBean
  }
}
