package io.micronaut.inject.executable.inheritance

import io.micronaut.inject.AbstractCompilerTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InheritedExecutableSpec extends AbstractCompilerTest {

  @Test
  def `test extending an abstract class with an executable method`():Unit = {
    val definition = buildBeanDefinition("test.$GenericController", """
    |package test;
    |
    |import io.micronaut.inject.annotation._
    |import io.micronaut.context.annotation._
    |
    |abstract class GenericController[T] {
    |
    |    def getPath():String
    |
    |    @Executable
    |    def save(entity:T):String = "parent"
    |}
    |""".stripMargin)

    assertThat(definition).isNull()
  }

  @Test
  def `test the same method isn't written twice`():Unit = {
    val definition = buildBeanDefinition("test.$StatusController","""
|package test;
|
|import io.micronaut.inject.annotation._
|import io.micronaut.context.annotation._
|import javax.inject.Singleton
|
|@Executable
|@Singleton
|class StatusController extends GenericController[String] {
|
|    override def getPath():String = "/statuses"
|
|    override def save(entity:String):String = "child"
|}
|
|abstract class GenericController[T] {
|
|    def getPath():String
|
|    @Executable
|    def save(entity:T):String = "parent"
|
|    @Executable
|    def save():String ="parent"
|}
""".stripMargin)

    assertThat(definition.getExecutableMethods.stream).anyMatch(it =>
      it.getMethodName == "getPath"
    )
    assertThat(definition.getExecutableMethods.stream).anyMatch(it =>
      it.getMethodName == "save" && it.getArgumentTypes.sameElements(Array(classOf[String]))
    )
    assertThat(definition.getExecutableMethods.stream).anyMatch(it =>
      it.getMethodName == "save" && it.getArgumentTypes.length == 0
    )
  }

  @Test
  def `test multiple inheritance`():Unit = {
    val definition = buildBeanDefinition("test.$Z", """
|package test;
|
|import io.micronaut.inject.annotation._
|import io.micronaut.context.annotation._
|import javax.inject.Singleton
|
|trait X[T1] {
|    @Executable def test():Unit
|
|    @Executable def test1(t1:T1):Unit
|}
|
|trait Y[T2] extends X[String] {
|    override def test():Unit
|
|    override def test1(t1:String) { }
|
|    @Executable def test2(t2:T2):Unit
|}
|
|@Singleton
|class Z extends Y[Int] {
|    override def test():Unit = { }
|
|    override def test2(t2:Int) = { }
|}
""".stripMargin)
    assertThat(definition).isNotNull
    assertThat(definition.getExecutableMethods).hasSize(3)

    assertThat(definition.getExecutableMethods.stream).anyMatch(it =>
      it.getMethodName == "test" && it.getArgumentTypes.length == 0
    )
    assertThat(definition.getExecutableMethods.stream).anyMatch(it =>
      it.getMethodName == "test1" && it.getArgumentTypes.sameElements(Array(classOf[String]))
    )
    assertThat(definition.getExecutableMethods.stream).anyMatch(it =>
      it.getMethodName == "test2" && it.getArgumentTypes.sameElements(Array(classOf[Int]))
    )
  }
}
