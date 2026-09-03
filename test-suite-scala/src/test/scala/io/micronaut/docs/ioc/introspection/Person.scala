package io.micronaut.docs.ioc.introspection

// tag::class[]
import io.micronaut.core.annotation.AccessorsStyle
import io.micronaut.core.annotation.Introspected

@Introspected
@AccessorsStyle(readPrefixes = Array(""), writePrefixes = Array("")) // <1>
class Person(private var currentName: String, private var currentAge: Int):

  def name(): String = currentName // <2>

  def name(name: String): Unit = // <2>
    currentName = name

  def age(): Int = currentAge // <2>

  def age(age: Int): Unit = // <2>
    currentAge = age
// end::class[]
