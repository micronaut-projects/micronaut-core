/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.ioc.beans

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanWrapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IntrospectionSpec:

  @Test
  def testRetrieveInspection(): Unit =

    // tag::usage[]
    val introspection = BeanIntrospection.getIntrospection(classOf[Person]) // <1>
    val person = introspection.instantiate("John", Int.box(18)) // <2>
    println("Hello " + person.name)

    val property = introspection.getRequiredProperty("name", classOf[String]) // <3>
    val name = property.get(person) // <4>
    println("Hello " + name)
    // end::usage[]

    assertEquals("John", name)

  @Test
  def testBeanWrapper(): Unit =
    // tag::wrapper[]
    val wrapper = BeanWrapper.getWrapper(MutablePerson("Fred")) // <1>

    wrapper.setProperty("age", "20") // <2>
    val newAge = wrapper.getRequiredProperty("age", classOf[Int]) // <3>

    println("Person's age now " + newAge)
    // end::wrapper[]
    assertEquals(20, newAge)

  @Test
  def testVehicle(): Unit =
    val introspection = BeanIntrospection.getIntrospection(classOf[Vehicle])
    val vehicle = introspection.instantiate("Subaru", "WRX", 2)
    assertEquals("Subaru", vehicle.make)
    assertEquals("WRX", vehicle.model)
    assertEquals(2, vehicle.axles)

  @Test
  def testBusiness(): Unit =
    val introspection = BeanIntrospection.getIntrospection(classOf[Business])
    val business = introspection.instantiate("Apple")
    assertEquals("Apple", business.name)
