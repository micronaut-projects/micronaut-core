package io.micronaut.scala

import java.util
import java.util.Collections

import io.micronaut.core.annotation.AnnotationMetadata

import scala.tools.nsc.Global

class ExecutableElementParamInfo(requiresReflection: Boolean, val metadata: AnnotationMetadata) {
  val parameters = new util.LinkedHashMap[String, AnyRef]
  val genericParameters = new util.LinkedHashMap[String, AnyRef]
  val parameterMetadata = new util.LinkedHashMap[String, AnnotationMetadata]
  val genericTypes = new util.LinkedHashMap[String, util.Map[String, AnyRef]]

  def this(requiresReflection: Boolean, metadata: Option[AnnotationMetadata]) = {
    this(requiresReflection, metadata.getOrElse(AnnotationMetadata.EMPTY_METADATA))
  }

  def addParameter(name: String, argType: AnyRef, genericType: AnyRef): Unit = {
    parameters.put(name, argType)
    genericParameters.put(name, genericType)
  }

  def addAnnotationMetadata(name: String, valDefMetadata: AnnotationMetadata): Unit = {
    parameterMetadata.put(name, valDefMetadata)
  }

  def addGenericTypes(paramName: String, generics: util.Map[String, AnyRef]): Unit = {
    genericTypes.put(paramName, generics)
  }

}

object ExecutableElementParamInfo {

  def populateParameterData(element:Global#Symbol):ExecutableElementParamInfo = populateParameterData(Option.empty, Some(element), Collections.emptyMap())

  def populateParameterData(
      declaringTypeName: Option[String],
      element: Option[Global#Symbol],
      boundTypes: java.util.Map[String, AnyRef]
  ): ExecutableElementParamInfo = {
    element.map { defSymbol =>
      val elementMetadata = if (declaringTypeName.isDefined) {
        new ScalaAnnotationMetadataBuilder().build(declaringTypeName.get, SymbolFacade(defSymbol))
      } else {
        Globals.metadataBuilder.getOrCreate(SymbolFacade(defSymbol))
      }
      val params = new ExecutableElementParamInfo(false, elementMetadata)

      defSymbol.originalInfo.params.foreach { paramSymbol =>
          val valDefMetadata = Globals.metadataBuilder.getOrCreate(SymbolFacade(paramSymbol))

          val argName = paramSymbol.nameString

          params.addAnnotationMetadata(argName, valDefMetadata)

          val argType = TypeFunctions.argTypeForTypeSymbol(
            paramSymbol.originalInfo.typeSymbol,
            paramSymbol.originalInfo.typeArgs
          )

//          if (/*isConstructBinding &&*/ Stream.of(classOf[Property], classOf[Value], classOf[Parameter]).noneMatch(annotationMetadata.hasAnnotation)) {
//            val parameterElement = typeUtils.asElement(typeMirror)
//            val parameterTypeMetadata = if (parameterElement != null) annotationUtils.getAnnotationMetadata(parameterElement)
//            else AnnotationMetadata.EMPTY_METADATA
//            if (!parameterTypeMetadata.hasStereotype(classOf[Scope])) annotationMetadata = addPropertyMetadata(annotationMetadata, paramElement, argName)
//          }

            val kind = paramSymbol.originalInfo.typeSymbol

            if (kind.isClass || kind.isInterface) {
              params.addParameter(paramSymbol.nameString, argType, argType)
              val genericTypeMap = TypeFunctions.genericTypesForSymbol(
                paramSymbol
              )
              // TODO still work to be done here. This is probably not correct, but passes tests
              genericTypeMap.putAll(boundTypes)
              if (!genericTypeMap.isEmpty) {
                params.addGenericTypes(paramSymbol.nameString, genericTypeMap)
              }
            } else if (kind.isTypeParameter) {
                params.addParameter(
                  argName,
                  paramSymbol.typeOfThis.typeSymbol.fullName,
                  //boundTypes.getOrDefault(kind.nameString, paramSymbol.typeOfThis.typeSymbol.fullName),
                  boundTypes.getOrDefault(kind.nameString, paramSymbol.originalInfo.bounds.hi.typeSymbol.fullName)
                )

                if (boundTypes.containsKey(kind.nameString)) {
                  params.addGenericTypes(
                    argName,
                    Collections.singletonMap(
                      kind.nameString,
                      boundTypes.get(kind.nameString)))
                }
            } else {

            }

//             if (kind.isPrimitive) {
//               var typeName = null
//               if (typeMirror.isInstanceOf[DeclaredType]) {
//                 val dt = typeMirror.asInstanceOf[DeclaredType]
//                 typeName = dt.asElement.getSimpleName.toString
//               }
//               else typeName = modelUtils.resolveTypeName(typeMirror)
//               val argType = modelUtils.classOfPrimitiveFor(typeName)
//               params.addParameter(argName, argType, argType)
//             }


          // if ((kind eq TypeKind.ERROR) && !processingOver) throw new BeanDefinitionInjectProcessor.PostponeToNextRoundException
          //        kind match {
          //          case ARRAY =>
          //            val arrayType = typeMirror.asInstanceOf[ArrayType]
          //            val componentType = arrayType.getComponentType
          //            val resolvedType = modelUtils.resolveTypeReference(arrayType)
          //            params.addParameter(argName, resolvedType, genericUtils.resolveTypeReference(arrayType, boundTypes))
          //            params.addGenericTypes(argName, Collections.singletonMap("E", modelUtils.resolveTypeReference(componentType)))
          //
          //          case TYPEVAR =>
          //            val typeVariable = typeMirror.asInstanceOf[TypeVariable]
          //            val parameterType = genericUtils.resolveTypeVariable(paramElement, typeVariable)
          //            if (parameterType != null) {
          //              params.addParameter(argName, modelUtils.resolveTypeReference(typeVariable), genericUtils.resolveTypeReference(typeVariable, boundTypes))
          //              params.addGenericTypes(argName, Collections.singletonMap(typeVariable.toString, genericUtils.resolveTypeReference(typeVariable, boundTypes)))
          //            }
          //            else error(element, "Unprocessable generic type [%s] for param [%s] of element %s", typeVariable, paramElement, element)
          //
          //          case DECLARED =>
          //            val declaredType = typeMirror.asInstanceOf[DeclaredType]
          //            var typeElement = elementUtils.getTypeElement(typeUtils.erasure(declaredType).toString)
          //            if (typeElement == null) typeElement = declaredType.asElement.asInstanceOf[TypeElement]
          //            val `type` = modelUtils.resolveTypeReference(typeElement)
          //            params.addParameter(argName, `type`, `type`)
          //            val resolvedParameters = genericUtils.resolveGenericTypes(declaredType, typeElement, boundTypes)
          //            if (!resolvedParameters.isEmpty) params.addGenericTypes(argName, resolvedParameters)
          //
          //
          //            else error(element, "Unprocessable element type [%s] for param [%s] of element %s", kind, paramElement, element)
          //        }
          //      }
      }


      //    if (element == null) return new ExecutableElementParamInfo(false, null)
      //    var elementMetadata = null
      //    if (declaringTypeName == null) elementMetadata = annotationUtils.getAnnotationMetadata(element)
      //    else elementMetadata = annotationUtils.newAnnotationBuilder.build(declaringTypeName, element)
      //    val params = new ExecutableElementParamInfo(modelUtils.isPrivate(element), elementMetadata)
      //    val isConstructBinding = elementMetadata.hasDeclaredStereotype(classOf[ConfigurationInject])
      //    if (isConstructBinding) this.configurationMetadata = metadataBuilder.visitProperties(concreteClass, null)
      //    element.getParameters.forEach((paramElement: VariableElement) => {
      //      def foo(paramElement: VariableElement) = {
      //        val argName = paramElement.getSimpleName.toString
      //        var annotationMetadata = annotationUtils.getAnnotationMetadata(paramElement)
      //        if (annotationMetadata.hasDeclaredAnnotation("org.jetbrains.annotations.Nullable")) annotationMetadata = DefaultAnnotationMetadata.mutateMember(annotationMetadata, "javax.annotation.Nullable", Collections.emptyMap)
      //        if (annotationMetadata.hasStereotype(ANN_CONSTRAINT)) params.setValidated(true)
      //        val typeMirror = paramElement.asType
      //        if (isConstructBinding && Stream.of(classOf[Property], classOf[Value], classOf[Parameter]).noneMatch(annotationMetadata.hasAnnotation)) {
      //          val parameterElement = typeUtils.asElement(typeMirror)
      //          val parameterTypeMetadata = if (parameterElement != null) annotationUtils.getAnnotationMetadata(parameterElement)
      //          else AnnotationMetadata.EMPTY_METADATA
      //          if (!parameterTypeMetadata.hasStereotype(classOf[Scope])) annotationMetadata = addPropertyMetadata(annotationMetadata, paramElement, argName)
      //        }
      //        params.addAnnotationMetadata(argName, annotationMetadata)
      //

      //
      //      foo(paramElement)
      //    })
          params
    }.getOrElse(new ExecutableElementParamInfo(false, Option.empty))
  }
}
