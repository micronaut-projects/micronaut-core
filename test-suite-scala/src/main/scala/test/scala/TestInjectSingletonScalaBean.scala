package test.scala

import java.util.Optional

import javax.inject.{Inject, Named, Singleton}
import test.java.TestSingletonBean

@Singleton
@Inject
class TestInjectSingletonScalaBean(
  val singletonBean: TestSingletonBean,
  val singletonScalaBean: TestSingletonScalaBean,
  val engines: Array[TestEngine],
  val v8Engine: V8Engine,
  val v6Engine: V6Engine,
  @Named("v6") val namedV6Engine: TestEngine,
  @Named("v8") val namedV8Engine: TestEngine,
  //@Named("v8") val existingOptionEngine: Option[TestEngine],
  //@Named("dne") val nonExistingOptionEngine: Option[TestEngine]
  @Named("v8") val existingOptionalEngine: Optional[TestEngine],
  @Named("dne") val nonExistingOptionalEngine: Optional[TestEngine],
//  val listOfEngines: List[TestEngine]
) {
  @Inject var injectedFieldSingletonBean:TestSingletonBean = _
  @Inject var injectedFieldSingletonScalaBean:TestSingletonScalaBean = _
}
