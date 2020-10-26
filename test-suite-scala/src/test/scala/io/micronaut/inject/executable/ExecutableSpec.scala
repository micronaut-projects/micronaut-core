package io.micronaut.inject.executable

import io.micronaut.context.{AbstractExecutableMethod, DefaultApplicationContext}
import io.micronaut.inject.{AbstractCompilerTest, ExecutableMethod}
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.Test
import java.util

import io.micronaut.context.annotation.Executable
import javax.inject.{Inject, Singleton}


@Singleton class BookService

@Executable class BookController {
  @Inject private[executable] var bookService:BookService = null

  @Executable def show(id: Long): String = String.format("%d - The Stand", id)

  @Executable def showArray(id: Array[Long]): String = String.format("%d - The Stand", id(0))

  @Executable def showPrimitive(id: Long): String = String.format("%d - The Stand", id)

  @Executable def showPrimitiveArray(id: Array[Long]): String = String.format("%d - The Stand", id(0))

  @Executable def showVoidReturn(jobNames: util.List[String]): Unit = {
    jobNames.add("test")
  }

  @Executable def showPrimitiveReturn(values: Array[Int]): Int = values(0)
}

class ExecutableSpec extends AbstractCompilerTest {
  @Test
  def `test executable compile spec`():Unit = {
    val beanDefinition = buildBeanDefinition("test.$MyBean",
      """
        |package test
        |
        |import io.micronaut.context.annotation._
        |
        |@Executable
        |class MyBean {
        |  def methodOne(@javax.inject.Named("foo") one:String) = "good"
        |
        |  def methodTwo(one:String, two:String) = "good"
        |
        |  def methodZero() = "good"
        |}
        |""".stripMargin)

    assertThat(beanDefinition.getExecutableMethods.size()).isEqualTo(3)

    val methodNames = beanDefinition.getExecutableMethods.stream()
      .map { method:ExecutableMethod[_,_] => method.getMethodName }
      .toArray()

    assertThat(methodNames).contains("methodOne", "methodTwo", "methodZero")
  }

  @Test
  def `test executable metadata`():Unit = {
   val applicationContext = new DefaultApplicationContext().start()

    val method = applicationContext.findExecutionHandle(classOf[BookController], "show", classOf[Long])
    val executableMethod = applicationContext.findBeanDefinition(classOf[BookController]).get().findMethod("show", classOf[Long]).get()

    assertThat(method).isPresent

    val executionHandle = method.get()

    assertThat(executionHandle.getReturnType.getType).isSameAs(classOf[String])
    assertThat(executionHandle.invoke(1L).equals("1 - The Stand")).isTrue
    assertThat(executableMethod.getClass.getSuperclass).isSameAs(classOf[AbstractExecutableMethod])

    assertThatThrownBy(() => executionHandle.invoke("bad"))
      .isInstanceOf(classOf[IllegalArgumentException])
   //   .hasMessage("Invalid type [java.lang.String] for argument [Long id] of method: show")
  }

  @Test
  def `test executable responses`():Unit = {
    val applicationContext = new DefaultApplicationContext("test").start()

    List(
      ("show"                ,Array(classOf[Long])             ,Array(1L)         ,"1 - The Stand"),
      ("showArray"           ,Array(classOf[Array[Long]])      ,Array(Array(1L))  ,"1 - The Stand"),
      ("showPrimitive"       ,Array(classOf[Long])             ,Array(1L)         ,"1 - The Stand"),
      ("showPrimitiveArray"  ,Array(classOf[Array[Long]])      ,Array(Array(1L))  ,"1 - The Stand"),
      ("showVoidReturn"      ,Array(classOf[util.List[String]]),Array("test")     ,null),
      ("showPrimitiveReturn" ,Array(classOf[Array[Int]])       ,Array(Array(1))   ,1)
    ).foreach { tuple  =>
      val (methodName, argTypes, args, results) = tuple
      assertThat(applicationContext.findExecutionHandle(classOf[BookController], methodName, argTypes.toSeq: _*))
        .as(methodName)
        .isPresent()
      // val method = applicationContext.findExecutionHandle(classOf[BookController], methodName, argTypes).get()
      // method.invoke(args as Object[]) == result
    }
  }
}
