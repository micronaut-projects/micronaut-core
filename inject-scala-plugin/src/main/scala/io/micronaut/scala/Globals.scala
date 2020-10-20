package io.micronaut.scala

import java.util.Collections

import io.micronaut.core.convert.value.MutableConvertibleValuesMap
import io.micronaut.core.reflect.ClassUtils
import io.micronaut.inject.processing.ProcessedTypes

import scala.collection.mutable
import scala.tools.nsc.Global

object Globals {
  val AROUND_TYPE = "io.micronaut.aop.Around"
  val INTRODUCTION_TYPE = "io.micronaut.aop.Introduction"
  val ANNOTATION_STEREOTYPES = Array[String](
    ProcessedTypes.POST_CONSTRUCT,
    ProcessedTypes.PRE_DESTROY,
    "javax.inject.Inject",
    "javax.inject.Qualifier",
    "javax.inject.Singleton",
    "io.micronaut.context.annotation.Bean",
    "io.micronaut.context.annotation.Replaces",
    "io.micronaut.context.annotation.Value",
    "io.micronaut.context.annotation.Property",
    "io.micronaut.context.annotation.Executable",
    AROUND_TYPE,
    INTRODUCTION_TYPE)

  val metadataBuilder = new ScalaAnnotationMetadataBuilder()
  val loadedVisitors = new mutable.LinkedHashMap[String, LoadedVisitor]
  val visitorAttributes = new MutableConvertibleValuesMap[AnyRef]
  val beanableSymbols = new mutable.HashSet[Global#Symbol]

  private def classOfPrimitiveFor(primitiveType: String) = ClassUtils.getPrimitiveType(primitiveType).orElseThrow(() => new IllegalArgumentException("Unknown primitive type: " + primitiveType))

  def argTypeForTypeSymbol(symbol:Global#Symbol, typeArgs:List[Global#Type]):AnyRef = symbol match {
    case classSymbol:Global#ClassSymbol => classSymbol.fullName match {
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
        // TODO - multi-dimensional arrays
      case "scala.Array" => typeArgs.headOption.map(arg => arrayClassForSymbol(arg.typeSymbol)).getOrElse(symbol.fullName)
      case _: String => symbol.fullName
    }
    case _ => symbol.fullName
  }

  def arrayClassForSymbol(symbol:Global#Symbol): String = {
    symbol.fullName match {
      case "scala.Boolean" => "[Z"
      case "scala.Int" => "[I"
      case "scala.Float" => "[F"
      case "scala.Double" => "[D"
      case "scala.Long" => "[L"
      case "scala.Byte" => "[B"
      case "scala.Short" => "[S"
      case "scala.Char" => "[C"
      case _ => "[" + symbol.fullName
    }
  }

  def argTypeForTypeTree(tree:Global#TypeTree):AnyRef = {
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

  def genericTypesForTree(cls:String, tree:Global#Tree):java.util.Map[String, AnyRef] = tree match {
    case tree:Global#TypeTree => genericTypesForTypeTree(cls, tree)
    case _ => Collections.emptyMap()
  }

  def genericTypesForSymbol(symbol:Global#Symbol):java.util.Map[String, AnyRef] = symbol match {
    case classSymbol: Global#ClassSymbol if "scala.Array" != classSymbol.fullName => {
      val genericTypeMap = new java.util.HashMap[String, AnyRef]()
      val paramNames = Class.forName(classSymbol.fullName).getTypeParameters
      var idx = 0
      for (arg <- classSymbol.originalInfo.typeArgs) {
        genericTypeMap.put(paramNames(idx).getName, Globals.argTypeForTypeSymbol(arg.typeSymbol, List()))
        idx = idx + 1
      }
      genericTypeMap
    }
  }

  def genericTypesForTypeTree(cls:String, typeTree:Global#TypeTree):java.util.Map[String, AnyRef] = typeTree.original match {
    case appliedTree:Global#AppliedTypeTree if "scala.Array" != appliedTree.tpt.toString => {
      val genericTypeMap = new java.util.HashMap[String, AnyRef]()
      val paramNames = Class.forName(cls).getTypeParameters
      var idx = 0
      for (arg <- appliedTree.args) {
        genericTypeMap.put(paramNames(idx).getName, Globals.argTypeForTree(arg))
        idx = idx + 1
      }
      genericTypeMap
    }
    case _ => Collections.emptyMap()
  }

  def argTypeForTree(typeTree:Global#Tree) = typeTree match {
    case tree:Global#TypeTree => argTypeForTypeTree(tree)
    case _ => typeTree.toString() // TODO? something else
  }

  def argTypeForValDef(valDef:Global#ValDef) =
    argTypeForTypeTree(valDef.tpt.asInstanceOf[Global#TypeTree])
}