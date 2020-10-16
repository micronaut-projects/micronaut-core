package io.micronaut.inject.property

import io.micronaut.context.annotation.Property
import io.micronaut.core.convert.format.MapFormat
import javax.annotation.Nullable
import javax.inject.Singleton

@Singleton
class ConstructorPropertyInject(
   @Property(name = "my.map")
   @MapFormat(transformation = MapFormat.MapTransformation.FLAT)
   val values: java.util.Map[String, String],
   @Property(name = "my.string")
   val str: String,
   @Property(name = "my.int")
   val integer: Int,
   @Property(name = "my.nullable")
   @Nullable val nullable: String)
