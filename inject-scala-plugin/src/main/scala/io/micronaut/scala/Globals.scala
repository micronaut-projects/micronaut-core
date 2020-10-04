package io.micronaut.scala

import io.micronaut.core.convert.value.MutableConvertibleValuesMap
import io.micronaut.core.reflect.ClassUtils

import scala.collection.mutable
import scala.tools.nsc.Global

object Globals {
  val metadataBuilder = new ScalaAnnotationMetadataBuilder()
  val loadedVisitors: mutable.Map[String, LoadedVisitor] = new mutable.LinkedHashMap[String, LoadedVisitor]
  val visitorAttributes = new MutableConvertibleValuesMap[AnyRef]

  private def classOfPrimitiveFor(primitiveType: String) = ClassUtils.getPrimitiveType(primitiveType).orElseThrow(() => new IllegalArgumentException("Unknown primitive type: " + primitiveType))

  def argTypeForValDef(valDef:Global#ValDef):AnyRef = valDef.tpt.asInstanceOf[Global#TypeTree].original.toString match {
    case "scala.Boolean" => classOfPrimitiveFor("boolean")
    case "scala.Int" => classOfPrimitiveFor("int")
    case "scala.Float" => classOfPrimitiveFor("float")
    case "scala.Double" => classOfPrimitiveFor("double")
    case "scala.Long" => classOfPrimitiveFor("long")
    case "scala.Byte" => classOfPrimitiveFor("byte")
    case "scala.Short" => classOfPrimitiveFor("short")
    case "scala.Char" => classOfPrimitiveFor("char")
    case "scala.Predef.String" => "java.lang.String"
    case other: String => if (other.startsWith("scala.Array[")) {
      valDef.tpt.asInstanceOf[Global#TypeTree].original
        .asInstanceOf[Global#AppliedTypeTree].args(0).toString + "[]"
    } else {
      valDef.tpt.toString
    }
  }
}