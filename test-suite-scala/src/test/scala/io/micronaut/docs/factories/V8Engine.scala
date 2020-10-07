package io.micronaut.docs.factories

class V8Engine(crankShaft: CrankShaft) extends Engine {

  override def start() = "Starting V8"
}
