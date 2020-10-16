package io.micronaut.inject.factory.beanwithfactory

import io.micronaut.context.event.{BeanInitializedEventListener, BeanInitializingEvent}
import javax.inject.Singleton

@Singleton
class MyListener extends BeanInitializedEventListener[BFactory] {
  override def onInitialized(event: BeanInitializingEvent[BFactory]): BFactory = {
    val bean = event.getBean
    assert(bean.getMethodInjected != null)
    assert(bean.getFieldA != null)
    assert(bean.getAnotherField != null)
    assert(bean.a != null)
    assert(!bean.postConstructCalled)
    assert(!bean.getCalled)
    bean.name = "changed"
    bean
  }
}
