package test.scala

import io.micronaut.context.annotation.Value

@javax.inject.Singleton
class TestSingletonInjectValueConstructorBean(
   @Value("injected String") val injectedString: String,
   @Value("41") val injectedByte: Byte,
   @Value("42") val injectedShort: Short,
   @Value("43") val injectedInt: Int,
   @Value("44") val injectedLong: Long,
   @Value("44.1f") val injectedFloat: Float,
   @Value("44.2f") val injectedDouble: Double,
   @Value("#") val injectedChar: Char,
   @Value("true") val injectedBoolean: Boolean,
   @Value("${lookup.integer}") val lookUpInteger: Int
) {
  @Value("46") var injectIntField:Int = _
}
