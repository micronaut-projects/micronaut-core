package io.micronaut.docs.ioc.introspection

import io.micronaut.core.beans.BeanIntrospection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PersonSpec:

  @Test
  def testPersonIntrospection(): Unit =
    // tag::usage[]
    val introspection = BeanIntrospection.getIntrospection(classOf[Person])
    val person = introspection.instantiate("John", 42)

    assertEquals("John", person.name())
    assertEquals(42, person.age())
    // end::usage[]
