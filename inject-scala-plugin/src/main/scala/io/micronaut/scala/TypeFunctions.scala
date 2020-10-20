package io.micronaut.scala

import java.util.Collections

import io.micronaut.core.reflect.ClassUtils

import scala.tools.nsc.Global

object TypeFunctions {
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
      case "scala.Array" => typeArgs.headOption.map(arg => "[" + arrayClassForSymbol(arg.typeSymbol, arg.typeArgs)).getOrElse(symbol.fullName)
      case _: String => symbol.fullName
    }
    case aliasTypeSymbol:Global#AliasTypeSymbol => {
      aliasTypeSymbol.fullName match {
        case "scala.List" => {
          typeArgs.headOption.map(arg => "scala.collection.immutable.Nil$").getOrElse(symbol.fullName)
        }
        case _ => aliasTypeSymbol.fullName
      }
    }
    case _ => symbol.fullName
  }

  def arrayClassForSymbol(symbol:Global#Symbol, typeArgs:List[Global#Type]): String = {
    symbol.fullName match {
      case "scala.Boolean" => "Z"
      case "scala.Int" => "I"
      case "scala.Float" => "F"
      case "scala.Double" => "D"
      case "scala.Long" => "J"
      case "scala.Byte" => "B"
      case "scala.Short" => "S"
      case "scala.Char" => "C"
      case "scala.Array" =>
        // Array: typeArgs have to length == 1
        "[" + arrayClassForSymbol(typeArgs.head.typeSymbol, typeArgs.head.typeArgs)
      case _ => "L" + symbol.fullName + ";"
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
      case "scala.Array" =>  tree.original.asInstanceOf[Global#AppliedTypeTree].args.head.toString + "[]"
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
      val paramNames = classSymbol.originalInfo.typeParams
      var idx = 0
      for (arg <- classSymbol.originalInfo.typeArgs) {
        genericTypeMap.put(paramNames(idx).nameString, argTypeForTypeSymbol(arg.typeSymbol, List()))
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
        genericTypeMap.put(paramNames(idx).getName, argTypeForTree(arg))
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
