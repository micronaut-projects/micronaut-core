package test.scala

import javax.inject.{Inject, Singleton}

@Singleton
@Inject
class TestInjectConstructorSingletonBean(val singletonBean: TestSingletonBean)
