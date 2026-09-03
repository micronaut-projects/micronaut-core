package io.micronaut.docs.ioc.builders

import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.ReflectiveAccess

import java.util.Objects

// tag::class[]
@ReflectiveAccess
@Introspected(builder = new Introspected.IntrospectionBuilder(
  builderClass = classOf[Person.Builder]
))
class Person private (val name: String, val age: Int):

  override def equals(other: Any): Boolean =
    other match
      case person: Person => age == person.age && Objects.equals(name, person.name)
      case _ => false

  override def hashCode(): Int =
    Objects.hash(name, Integer.valueOf(age))

object Person:
  def builder(): Builder =
    Builder()

  final class Builder:
    private var nameValue: String | Null = null
    private var ageValue: Int = 0

    def name(name: String): Builder =
      nameValue = name
      this

    def age(age: Int): Builder =
      ageValue = age
      this

    def build(): Person =
      Objects.requireNonNull(nameValue)
      if ageValue < 1 then
        throw IllegalArgumentException("Age must be a positive number")
      Person(nameValue.nn, ageValue)
// end::class[]
