package io.micronaut.docs.factories

import io.micronaut.context.annotation.Factory
import javax.inject.Singleton

@Factory
class EngineFactory {
  @Singleton def v8Engine(crankShaft: CrankShaft):Engine = new V8Engine(crankShaft)
}
