package io.micronaut.scala

import io.micronaut.core.reflect.ClassUtils

import scala.tools.nsc.Global

object TypeFunctions {
  private def classOfPrimitiveFor(primitiveType: String) = ClassUtils.getPrimitiveType(primitiveType).orElseThrow(() => new IllegalArgumentException("Unknown primitive type: " + primitiveType))

  private def isNestedArrayObjectJavaOrPrimitive(symbol:Global#Symbol, typeArgs:List[Global#Type]): Boolean = {
    if (symbol.fullName == "scala.Array") {
      val arg = typeArgs.head
      arg.typeSymbol.isPrimitiveValueClass ||
        isNestedArrayObjectJavaOrPrimitive(arg.typeSymbol, arg.typeArgs)
    } else {
      symbol.isPrimitiveValueClass || symbol.isJava
    }
  }

  /*
  Hic sunt dracones. Took a lot of trial and error to figure out when and how to translate
  types to and from Scala primitives and arrays
   */
  private def argTypeForTypeSymbol(symbol:Global#Symbol, typeArgs:List[Global#Type], usePrimitive:Boolean, inArray:Boolean):AnyRef = symbol match {
    case classSymbol:Global#ClassSymbol => classSymbol.fullName match {
      case "scala.Boolean" => if (usePrimitive) if (inArray) "Z" else classOfPrimitiveFor("boolean") else "java.lang.Boolean"
      case "scala.Int" =>     if (usePrimitive) if (inArray) "I" else classOfPrimitiveFor("int") else "java.lang.Integer"
      case "scala.Float" =>   if (usePrimitive) if (inArray) "F" else classOfPrimitiveFor("float") else "java.lang.Float"
      case "scala.Double" =>  if (usePrimitive) if (inArray) "D" else classOfPrimitiveFor("double") else "java.lang.Double"
      case "scala.Long" =>    if (usePrimitive) if (inArray) "J" else classOfPrimitiveFor("long") else "java.lang.Long"
      case "scala.Byte" =>    if (usePrimitive) if (inArray) "B" else classOfPrimitiveFor("byte") else "java.lang.Byte"
      case "scala.Short" =>   if (usePrimitive) if (inArray) "S" else classOfPrimitiveFor("short") else "java.lang.Short"
      case "scala.Char" =>    if (usePrimitive) if (inArray) "C" else classOfPrimitiveFor("char") else "java.lang.Character"
      case "scala.Unit" => classOfPrimitiveFor("void")
      case "scala.Predef.String" =>  if (inArray) "Ljava.lang.String;" else "java.lang.String"
      case "scala.Array" => {
        val result = typeArgs.headOption.map { arg =>
          if (usePrimitive && (inArray || arg.typeSymbol.fullName == "scala.Array" || isNestedArrayObjectJavaOrPrimitive(arg.typeSymbol, arg.typeArgs))) {
            "[" + argTypeForTypeSymbol(arg.typeSymbol, arg.typeArgs, true, true)
          } else {
            argTypeForTypeSymbol(arg.typeSymbol, arg.typeArgs, false, true) + "[]"
          }
        }.get
        if (!inArray && result.startsWith("[")) {
          Class.forName(result)
        } else {
          result
        }
      }
      case _ => if (inArray && usePrimitive) "L" + symbol.fullName + ";" else symbol.fullName
    }
    case aliasTypeSymbol:Global#AliasTypeSymbol => {
      aliasTypeSymbol.fullName match {
        case "scala.List" => {
          typeArgs.headOption.map(arg => "scala.collection.immutable.Nil$").getOrElse(symbol.fullName)
        }
        case _ => aliasTypeSymbol.fullName
      }
    }
    case abstractTypeSymbol:Global#AbstractTypeSymbol => abstractTypeSymbol.fullName
    case _:Global#NoSymbol => classOfPrimitiveFor("void")

  }

  def argTypeForTypeSymbol(symbol:Global#Symbol, typeArgs:List[Global#Type]):AnyRef = argTypeForTypeSymbol(symbol, typeArgs, true,false)

  def typeGenericsForParamsAndArgs(params:List[Global#Symbol], args:List[Global#Type]): java.util.Map[String, AnyRef] = {
    val genericTypeMap = new java.util.LinkedHashMap[String, AnyRef]()
    (args, params).zipped.foreach { (arg, param) =>
      genericTypeMap.put(param.nameString, argTypeForTypeSymbol(arg.typeSymbol, arg.typeArgs))
    }
    genericTypeMap
  }

  def genericTypesForSymbol(symbol:Global#Symbol):java.util.Map[String, AnyRef] =
    typeGenericsForParamsAndArgs(
      symbol.originalInfo.typeSymbol.originalInfo.typeParams,
      symbol.originalInfo.typeArgs
    )
}
