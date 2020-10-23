package io.micronaut.scala

import io.micronaut.core.reflect.ClassUtils

import scala.tools.nsc.Global

object TypeFunctions {
  private def classOfPrimitiveFor(primitiveType: String) = ClassUtils.getPrimitiveType(primitiveType).orElseThrow(() => new IllegalArgumentException("Unknown primitive type: " + primitiveType))

  def argTypeForTypeSymbol(symbol:Global#Symbol, typeArgs:List[Global#Type], useL:Boolean):AnyRef = symbol match {
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
      case "scala.Array" => typeArgs.headOption.map { arg =>
            if (useL) {
              "[" + arrayClassForSymbol(arg.typeSymbol, arg.typeArgs, useL)
            } else {
               arrayClassForSymbol(arg.typeSymbol, arg.typeArgs, useL) + "[]"
            }
      }.get
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
    case _:Global#NoSymbol => classOfPrimitiveFor("void")
    case _ => symbol.fullName
  }

  def arrayClassForSymbol(symbol:Global#Symbol, typeArgs:List[Global#Type], useL:Boolean): String = {
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
        if (useL) {
          "[" + arrayClassForSymbol(typeArgs.head.typeSymbol, typeArgs.head.typeArgs, useL)
        } else {
          arrayClassForSymbol(typeArgs.head.typeSymbol, typeArgs.head.typeArgs, useL) + "[]"
        }
      case _ => if (useL) {
        "L" + symbol.fullName + ";"
      } else {
        symbol.fullName
      }
    }
  }

  def typeGenericsForParamsAndArgs(params:List[Global#Symbol], args:List[Global#Type]): java.util.Map[String, AnyRef] = {
    val genericTypeMap = new java.util.LinkedHashMap[String, AnyRef]()
    (args, params).zipped.foreach { (arg, param) =>
      genericTypeMap.put(param.nameString, argTypeForTypeSymbol(arg.typeSymbol, arg.typeArgs, false))
    }
    genericTypeMap
  }

  def genericTypesForSymbol(symbol:Global#Symbol):java.util.Map[String, AnyRef] =
    typeGenericsForParamsAndArgs(
      symbol.originalInfo.typeSymbol.originalInfo.typeParams,
      symbol.originalInfo.typeArgs
    )
}
