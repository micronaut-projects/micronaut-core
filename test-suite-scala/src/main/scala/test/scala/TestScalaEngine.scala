package test.scala

import io.micronaut.context.annotation.Prototype

abstract class TestEngine(val numCylinders:Int)

@Prototype
class V6Engine extends TestEngine(6)
@Prototype
class V8Engine extends TestEngine(8)