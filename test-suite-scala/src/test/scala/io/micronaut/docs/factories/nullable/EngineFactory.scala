package io.micronaut.docs.factories.nullable

import io.micronaut.context.annotation.{EachBean, Factory}
import io.micronaut.context.exceptions.DisabledBeanException

// tag::class[]
@Factory
class EngineFactory {

  @EachBean(classOf[EngineConfiguration])
  def buildEngine(engineConfiguration: EngineConfiguration): Engine =
    if (engineConfiguration.isEnabled) {
      new Engine() {
        override def getCylinders: Integer = engineConfiguration.getCylinders
      }
    } else throw new DisabledBeanException("Engine configuration disabled")
}

// end::class[]
