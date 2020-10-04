package test.scala

import javax.inject.{Inject, Singleton}
import test.java.TestSingletonBean

@Singleton
@Inject
class TestInjectConstructorSingletonScalaBean(
  val singletonBean: TestSingletonBean,
  val singletonScalaBean: TestSingletonScalaBean,
  val engines: Array[TestEngine],
  val v8Engine: V8Engine,
  val v6Engine: V6Engine
)
