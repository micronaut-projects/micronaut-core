package test.scala

import io.micronaut.context.annotation.Prototype
import javax.inject.Named

sealed abstract class TestEngine(val numCylinders:Int)

@Prototype
@Named("v6")
class V6Engine extends TestEngine(6)

@Prototype
@Named("v8")
class V8Engine extends TestEngine(8)