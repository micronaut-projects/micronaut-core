package io.micronaut.inject.property

import io.micronaut.context.annotation.Property
import io.micronaut.core.convert.format.MapFormat
import javax.annotation.Nullable
import javax.inject.Singleton

@Singleton
class FieldPropertyInject {
  @Property(name = "my.map")
  @MapFormat(transformation = MapFormat.MapTransformation.FLAT)
  private[property] var values: java.util.Map[String, String] = null

  @Property(name = "my.map")
  private[property] var defaultInject: java.util.Map[String, String] = null

  @Property(name = "my.multi-value-map")
  private[property] var multiMap: java.util.Map[String, java.util.List[String]] = null

  @Property(name = "my.string")
  private[property] var str: String = null

  @Property(name = "my.int")
  private[property] var integer: Int = 0

  @Property(name = "my.nullable")
  @Nullable private[property] val nullable: String = null
}
