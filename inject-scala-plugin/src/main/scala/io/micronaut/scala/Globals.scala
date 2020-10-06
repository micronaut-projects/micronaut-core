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

  def argTypeForTree(tree:Global#TypeTree): AnyRef = {
    val valAsString = if (tree.original == null) tree.toString else tree.original.toString
    valAsString match { // TODO this can be less klunky
      case "scala.Boolean" | "Boolean" => classOfPrimitiveFor("boolean")
      case "scala.Int" | "Int" => classOfPrimitiveFor("int")
      case "scala.Float" | "Float" => classOfPrimitiveFor("float")
      case "scala.Double" | "Double" => classOfPrimitiveFor("double")
      case "scala.Long" | "Long" => classOfPrimitiveFor("long")
      case "scala.Byte" | "Byte" => classOfPrimitiveFor("byte")
      case "scala.Short" | "Short" => classOfPrimitiveFor("short")
      case "scala.Char" | "Char" => classOfPrimitiveFor("char")
      case "scala.Unit" | "Unit" => classOfPrimitiveFor("void")
      case "scala.Predef.String" => "java.lang.String"
      case other: String => if (other.startsWith("scala.Array[")) {
        tree.asInstanceOf[Global#TypeTree].original
          .asInstanceOf[Global#AppliedTypeTree].args(0).toString + "[]"
      } else {
        tree.toString
      }
    }
  }

  def argTypeForValDef(valDef:Global#ValDef):AnyRef =
    argTypeForTree(valDef.tpt.asInstanceOf[Global#TypeTree])
}