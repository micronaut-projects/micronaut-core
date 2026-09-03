package io.micronaut.docs.inject.anninheritance

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class AnnotationInheritanceSpec:

  @Test
  def testAnnotationInheritance(): Unit =
    val context = ApplicationContext.run(
      Map[String, Object]("datasource.url" -> "jdbc://someurl").asJava
    )
    try
      val beanDefinition = context.getBeanDefinition(classOf[BookRepository])
      val name = beanDefinition.stringValue(AnnotationUtil.NAMED).orElse(null)
      assertEquals("bookRepository", name)
      assertTrue(beanDefinition.isSingleton)
    finally context.close()
