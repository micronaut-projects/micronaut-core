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
    val valAsString = if (tree.original == null) tree.symbol.fullName else tree.original.symbol.fullName
    valAsString match {
      case "scala.Boolean" => classOfPrimitiveFor("boolean")
      case "scala.Int" => classOfPrimitiveFor("int")
      case "scala.Float" => classOfPrimitiveFor("float")
      case "scala.Double" => classOfPrimitiveFor("double")
      case "scala.Long" => classOfPrimitiveFor("long")
      case "scala.Byte" => classOfPrimitiveFor("byte")
      case "scala.Short" => classOfPrimitiveFor("short")
      case "scala.Char" => classOfPrimitiveFor("char")
      case "scala.Unit" => classOfPrimitiveFor("void")
      case "scala.Predef.String" => "java.lang.String"
      case "scala.Array" =>  tree.asInstanceOf[Global#TypeTree].original.asInstanceOf[Global#AppliedTypeTree].args(0).toString + "[]"
      case _: String => valAsString
    }
  }

  def argTypeForValDef(valDef:Global#ValDef):AnyRef =
    argTypeForTree(valDef.tpt.asInstanceOf[Global#TypeTree])
}