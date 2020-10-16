package io.micronaut.inject.property

import io.micronaut.context.annotation.Property
import io.micronaut.core.convert.format.MapFormat
import javax.annotation.Nullable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MethodPropertyInject {
  private var values: java.util.Map[String, String] = null
  private var str: String = null
  private var integer: Int = 0
  private var nullable: String = null

  def getValues: java.util.Map[String, String] = values

  def getStr: String = str

  def getInteger: Int = integer

  def getNullable: String = nullable

  @Inject def setValues(@Property(name = "my.map") @MapFormat(transformation = MapFormat.MapTransformation.FLAT) values: java.util.Map[String, String]): Unit = {
    this.values = values
  }

  @Inject def setStr(@Property(name = "my.string") str: String): Unit = {
    this.str = str
  }

  @Inject def setInteger(@Property(name = "my.int") integer: Int): Unit = {
    this.integer = integer
  }

  @Inject def setNullable(@Property(name = "my.nullable") @Nullable nullable: String): Unit = {
    this.nullable = nullable
  }
}
