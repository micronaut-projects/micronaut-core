package io.micronaut.scala

import java.util
import java.util.{Collections, Map}
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors

import io.micronaut.annotation.processing.BeanDefinitionInjectProcessor
import io.micronaut.context.annotation._
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.naming.NameUtils
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy
import io.micronaut.inject.processing.ProcessedTypes
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.inject.writer.{BeanDefinitionReferenceWriter, BeanDefinitionVisitor, BeanDefinitionWriter}
import javax.inject.{Inject, Scope}

import scala.collection.mutable
import scala.tools.nsc.Global

class AnnBeanElementVisitor(concreteClass:Global#ClassDef, visitorContext:VisitorContext) {
  private val concreteClassMetadata = Globals.metadataBuilder.getOrCreate(new ScalaSymbolElement(concreteClass.symbol))

  private val isFactoryType = concreteClassMetadata.hasStereotype(classOf[Factory])

  private val factoryMethodIndex = new AtomicInteger(0)

  private val originatingElement = new ScalaClassElement(concreteClass.symbol.asInstanceOf[Global#ClassSymbol], concreteClassMetadata, visitorContext)

  val beanDefinitionWriters = new mutable.HashMap[ScalaElement, BeanDefinitionVisitor]

  private def concreteConstructorFor(classDef: Global#ClassDef): Option[Global#DefDef] = {
    val constructors = classDef.impl.body.filter(_.symbol.isConstructor)

    constructors.headOption.map(_.asInstanceOf[Global#DefDef])
  }

  private def visitAnnotatedMethod(method: Global#DefDef,
                                   annotationMetadata: AnnotationMetadata,
                                   beanDefinitionWriter: BeanDefinitionWriter): Unit = {
    val requiresReflection = method.symbol.isPrivate // || isPackagePrivateAndPackagesDiffer

    if (annotationMetadata.hasDeclaredStereotype(ProcessedTypes.POST_CONSTRUCT)) {
      //      final AopProxyWriter aopWriter = resolveAopWriter(writer);
      //      if (aopWriter != null && !aopWriter.isProxyTarget()) writer = aopWriter
      val params = ExecutableElementParamInfo.populateParameterData(Some(method))


      beanDefinitionWriter.visitPostConstructMethod(
        concreteClass.symbol.fullName,
        requiresReflection,
        Globals.argTypeForTree(method.tpt.asInstanceOf[Global#TypeTree]),
        method.symbol.nameString,
        params.parameters,
        params.annotationMetadata,
        params.genericTypes,
        annotationMetadata
      )
    }
  }

  private def visitExecutableMethod(method: Global#DefDef,
                                   beanDefinitionWriter: BeanDefinitionWriter) {
    val annotationMetadata = Globals.metadataBuilder.getOrCreate(new ScalaSymbolElement(method.symbol))

    val methodAnnotationMetadata = if (annotationMetadata.isInstanceOf[AnnotationMetadataHierarchy]) annotationMetadata
    else new AnnotationMetadataHierarchy(concreteClassMetadata, annotationMetadata)

    if (isFactoryType && methodAnnotationMetadata.hasDeclaredStereotype(classOf[Bean], classOf[Scope]) /* && (returnKind eq TypeKind.DECLARED) */) {
      visitBeanFactoryMethod(method, methodAnnotationMetadata)
    } else {
      val injected = annotationMetadata.hasDeclaredStereotype(classOf[Inject])
      val postConstruct = annotationMetadata.hasDeclaredStereotype(ProcessedTypes.POST_CONSTRUCT)
      val preDestroy = annotationMetadata.hasDeclaredStereotype(ProcessedTypes.PRE_DESTROY)
      if (injected || postConstruct || preDestroy || annotationMetadata.hasDeclaredStereotype(classOf[ConfigurationInject])) {
        //      if (isDeclaredBean) visitAnnotatedMethod(method, o)
        //      else if (injected) { // DEPRECATE: This behaviour should be deprecated in 2.0
        visitAnnotatedMethod(method, annotationMetadata, beanDefinitionWriter)
        //      }
      }
    }
  }

  private def createFactoryBeanMethodWriterFor(method: Global#DefDef, producedElement: Global#Type) = {
    val annotationMetadata = Globals.metadataBuilder.buildForParent(
      new ScalaSymbolElement(producedElement.typeSymbol),
      new ScalaSymbolElement(method.symbol), true)
    val producedPackageElement = producedElement.typeSymbol.enclosingPackage.fullName
    val definingPackageElement = method.symbol.owner.enclosingPackage.fullName
    val isInterface = producedElement.typeSymbol.isInterface
    val packageName = producedPackageElement.toString
    val beanDefinitionPackage = definingPackageElement.toString
    val shortClassName = producedElement.typeSymbol.nameString
    val upperCaseMethodName = NameUtils.capitalize(method.symbol.fullName)
    val factoryMethodBeanDefinitionName = beanDefinitionPackage + ".$" + concreteClass.symbol.nameString + "$" + upperCaseMethodName + factoryMethodIndex.getAndIncrement + "Definition"
    new BeanDefinitionWriter(
      packageName,
      shortClassName,
      factoryMethodBeanDefinitionName,
      Globals.argTypeForTree(method.tpt.asInstanceOf[Global#TypeTree]).toString, //modelUtils.resolveTypeReference(producedElement).toString,
      isInterface,
      originatingElement,
      annotationMetadata)
  }

  private def visitBeanFactoryMethod(beanMethod: Global#DefDef, methodAnnotationMetadata:AnnotationMetadata): Unit = {
//    if (isFactoryType && methodAnnotationMetadata.hasStereotype(beanMethod.symbol.owner, AROUND_TYPE)) {
//      visitExecutableMethod(beanMethod, annotationUtils.getAnnotationMetadata(beanMethod))
//    }
    val producedElement = beanMethod.symbol.asInstanceOf[Global#MethodSymbol].returnType
//    val producedElement: TypeElement = modelUtils.classElementFor(typeUtils.asElement(returnType))
    if (producedElement != null) {
      val producedTypeName = producedElement.toLongString
      val beanMethodParams = ExecutableElementParamInfo.populateParameterData(Some(beanMethod)) //, Collections.emptyMap)
      val beanMethodWriter = createFactoryBeanMethodWriterFor(beanMethod, producedElement)
      var beanTypeArguments: java.util.Map[String, java.util.Map[String, AnyRef]] = null
      //    if (returnType.isInstanceOf[DeclaredType]) {
      //      val dt: DeclaredType = returnType.asInstanceOf[DeclaredType]
      //      beanTypeArguments = genericUtils.buildGenericTypeArgumentInfo(dt)
      //      beanMethodWriter.visitTypeArguments(beanTypeArguments)
      //    }
      val beanMethodName = beanMethod.symbol.nameString
      val beanMethodParameters = beanMethodParams.parameters
      //val methodKey: StringBuilder = new StringBuilder(beanMethodName).append("(").append(beanMethodParameters.values.stream.map(_.toString).collect(Collectors.joining(","))).append(")")
      beanDefinitionWriters += new ScalaSymbolElement(beanMethod.symbol) -> beanMethodWriter
      val beanMethodDeclaringType: Any = beanMethod.symbol.owner.fullName
      val methodAnnotationMetadata: AnnotationMetadata = Globals.metadataBuilder.buildForParent(new ScalaSymbolElement(producedElement.typeSymbol), new ScalaSymbolElement(beanMethod.symbol))
      beanMethodWriter.visitBeanFactoryMethod(
        beanMethodDeclaringType,
        Globals.argTypeForTree(beanMethod.tpt.asInstanceOf[Global#TypeTree]),
        beanMethodName,
        methodAnnotationMetadata,
        beanMethodParameters,
        beanMethodParams.annotationMetadata,
        beanMethodParams.genericTypes
      )
      //    if (methodAnnotationMetadata.hasStereotype(AROUND_TYPE) && !modelUtils.isAbstract(concreteClass)) {
      //      val interceptorTypes: Array[AnyRef] = methodAnnotationMetadata.getAnnotationNamesByStereotype(AROUND_TYPE).toArray
      //      val returnTypeElement: TypeElement = beanMethod.getReturnType.asInstanceOf[DeclaredType].asElement.asInstanceOf[TypeElement]
      //      if (modelUtils.isFinal(returnTypeElement)) {
      //        error(returnTypeElement, "Cannot apply AOP advice to final class. Class must be made non-final to support proxying: " + returnTypeElement)
      //        return
      //      }
      //      val constructor: ExecutableElement = if (JavaModelUtils.isClass(returnTypeElement)) modelUtils.concreteConstructorFor(returnTypeElement, annotationUtils)
      //      else null
      //      val constructorData: ExecutableElementParamInfo = if (constructor != null) populateParameterData(null, constructor, Collections.emptyMap)
      //      else null
      //      val aopSettings: OptionalValues[Boolean] = methodAnnotationMetadata.getValues(AROUND_TYPE, classOf[Boolean])
      //      val finalSettings: util.Map[CharSequence, Boolean] = new util.LinkedHashMap[CharSequence, Boolean]
      //      import scala.collection.JavaConversions._
      //      for (setting <- aopSettings) {
      //        val entry: Optional[Boolean] = aopSettings.get(setting)
      //        entry.ifPresent((`val`: Boolean) => finalSettings.put(setting, `val`))
      //      }
      //      finalSettings.put(Interceptor.PROXY_TARGET, true)
      //      val proxyWriter: AopProxyWriter = resolveAopProxyWriter(beanMethodWriter, OptionalValues.of(classOf[Boolean], finalSettings), true, constructorData, interceptorTypes)
      //      if (beanTypeArguments != null) proxyWriter.visitTypeArguments(beanTypeArguments)
      //      returnType.accept(new PublicMethodVisitor[AnyRef, AopProxyWriter](typeUtils) {
      //        override protected def accept(`type`: DeclaredType, element: Element, aopProxyWriter: AopProxyWriter): Unit = {
      //          val method: ExecutableElement = element.asInstanceOf[ExecutableElement]
      //          val owningType: Any = modelUtils.resolveTypeReference(method.getEnclosingElement)
      //          if (owningType == null) throw new IllegalStateException("Owning type cannot be null")
      //          val returnTypeMirror: TypeMirror = method.getReturnType
      //          val returnType: TypeMirror = method.getReturnType
      //          val returnTypeGenerics: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
      //          genericUtils.resolveBoundGenerics(method.getEnclosingElement.asInstanceOf[TypeElement], returnType, genericUtils.buildGenericTypeArgumentElementInfo(concreteClass)).forEach((key: String, value: TypeMirror) => returnTypeGenerics.put(key, modelUtils.resolveTypeReference(value)))
      //          //Object resolvedReturnType = genericUtils.resolveTypeReference(returnType, returnTypeGenerics);
      //          val resolvedReturnType: Any = modelUtils.resolveTypeReference(returnType)
      //          val enclosingElement: TypeElement = method.getEnclosingElement.asInstanceOf[TypeElement]
      //          var boundTypes: util.Map[String, AnyRef] = genericUtils.buildGenericTypeArgumentInfo(concreteClass).get(enclosingElement.getQualifiedName.toString)
      //          if (boundTypes == null) boundTypes = Collections.emptyMap
      //          val params: ExecutableElementParamInfo = populateParameterData(null, method, boundTypes)
      //          val methodName: String = method.getSimpleName.toString
      //          val methodParameters: util.Map[String, AnyRef] = params.getParameters
      //          val methodQualifier: util.Map[String, AnnotationMetadata] = params.getParameterMetadata
      //          val methodGenericTypes: util.Map[String, util.Map[String, AnyRef]] = params.getGenericTypes
      //          val genericParameters: util.Map[String, AnyRef] = params.getGenericParameters
      //          var annotationMetadata: AnnotationMetadata = null
      //          var isAnnotationReference: Boolean = false
      //          // if the method is annotated we build metadata for the method
      //          if (annotationUtils.isAnnotated(producedTypeName, method)) annotationMetadata = annotationUtils.getAnnotationMetadata(beanMethod, method)
      //          else { // otherwise we setup a reference to the parent metadata (essentially the annotations declared on the bean factory method)
      //            isAnnotationReference = true
      //            annotationMetadata = new AnnotationMetadataReference(beanMethodWriter.getBeanDefinitionName + BeanDefinitionReferenceWriter.REF_SUFFIX, methodAnnotationMetadata)
      //          }
      //          val executableMethodWriter: ExecutableMethodWriter = beanMethodWriter.visitExecutableMethod(owningType, modelUtils.resolveTypeReference(returnTypeMirror), resolvedReturnType, returnTypeGenerics, methodName, methodParameters, genericParameters, methodQualifier, methodGenericTypes, annotationMetadata, JavaModelUtils.isInterface(method.getEnclosingElement), method.isDefault)
      //          aopProxyWriter.visitAroundMethod(owningType, resolvedReturnType, resolvedReturnType, returnTypeGenerics, methodName, methodParameters, genericParameters, methodQualifier, methodGenericTypes, if (!isAnnotationReference) new AnnotationMetadataReference(executableMethodWriter.getClassName, annotationMetadata)
      //          else annotationMetadata, JavaModelUtils.isInterface(method.getEnclosingElement), method.isDefault)
      //        }
      //      }, proxyWriter)
      //    }
      //    else if (methodAnnotationMetadata.hasStereotype(classOf[Executable])) returnType.accept(new PublicMethodVisitor[AnyRef, BeanDefinitionWriter](typeUtils) {
      //      override protected def accept(`type`: DeclaredType, element: Element, beanWriter: BeanDefinitionWriter): Unit = {
      //        val method: ExecutableElement = element.asInstanceOf[ExecutableElement]
      //        val owningType: Any = modelUtils.resolveTypeReference(method.getEnclosingElement)
      //        if (owningType == null) throw new IllegalStateException("Owning type cannot be null")
      //        val returnTypeMirror: TypeMirror = method.getReturnType
      //        val methodName: String = method.getSimpleName.toString
      //        val annotationMetadata: AnnotationMetadata = new AnnotationMetadataReference(beanMethodWriter.getBeanDefinitionName + BeanDefinitionReferenceWriter.REF_SUFFIX, methodAnnotationMetadata)
      //        val returnTypeGenerics: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
      //        genericUtils.resolveBoundGenerics(method.getEnclosingElement.asInstanceOf[TypeElement], returnType, genericUtils.buildGenericTypeArgumentElementInfo(`type`.asElement)).forEach((key: String, value: TypeMirror) => returnTypeGenerics.put(key, modelUtils.resolveTypeReference(value)))
      //        val enclosingElement: TypeElement = method.getEnclosingElement.asInstanceOf[TypeElement]
      //        var boundTypes: util.Map[String, AnyRef] = genericUtils.buildGenericTypeArgumentInfo(`type`).get(enclosingElement.getQualifiedName.toString)
      //        if (boundTypes == null) boundTypes = Collections.emptyMap
      //        val resolvedReturnType: Any = genericUtils.resolveTypeReference(returnTypeMirror, boundTypes)
      //        val params: ExecutableElementParamInfo = populateParameterData(producedTypeName, method, boundTypes)
      //        val methodParameters: util.Map[String, AnyRef] = params.getParameters
      //        val genericParameters: util.Map[String, AnyRef] = params.getGenericParameters
      //        val methodQualifier: util.Map[String, AnnotationMetadata] = params.getParameterMetadata
      //        val methodGenericTypes: util.Map[String, util.Map[String, AnyRef]] = params.getGenericTypes
      //        beanMethodWriter.visitExecutableMethod(owningType, modelUtils.resolveTypeReference(returnTypeMirror), resolvedReturnType, returnTypeGenerics, methodName, methodParameters, genericParameters, methodQualifier, methodGenericTypes, annotationMetadata, JavaModelUtils.isInterface(enclosingElement), method.isDefault)
      //      }
      //    }, beanMethodWriter)
      //    if (methodAnnotationMetadata.isPresent(classOf[Bean], "preDestroy")) {
      //      val preDestroyMethod: Optional[String] = methodAnnotationMetadata.getValue(classOf[Bean], "preDestroy", classOf[String])
      //      preDestroyMethod.ifPresent((destroyMethodName: String) => {
      //        def foo(destroyMethodName: String) = if (StringUtils.isNotEmpty(destroyMethodName)) {
      //          val destroyMethodDeclaringClass: TypeElement = typeUtils.asElement(returnType).asInstanceOf[TypeElement]
      //          val destroyMethodRef: Optional[ExecutableElement] = modelUtils.findAccessibleNoArgumentInstanceMethod(destroyMethodDeclaringClass, destroyMethodName)
      //          if (destroyMethodRef.isPresent) beanMethodWriter.visitPreDestroyMethod(destroyMethodDeclaringClass.getQualifiedName.toString, genericUtils.resolveTypeReference(destroyMethodRef.get.getReturnType), destroyMethodName)
      //          else error(beanMethod, "@Bean method defines a preDestroy method that does not exist or is not public: " + destroyMethodName)
      //        }
      //
      //        foo(destroyMethodName)
      //      })
      //    }
    }
  }

  def visit(): Unit = {
    if (concreteClass.symbol.annotations.nonEmpty) if (!concreteClass.symbol.isAbstractType) {
      val concreteClassMetadata = Globals.metadataBuilder.getOrCreate(ScalaSymbolElement(concreteClass.symbol))

      val constructor = concreteConstructorFor(concreteClass)

      val constructorParameterInfo = ExecutableElementParamInfo.populateParameterData(constructor)

      val beanDefinitionWriter = new BeanDefinitionWriter(
        concreteClass.symbol.enclosingPackageClass.fullName,
        concreteClass.name.toString,
        originatingElement,
        concreteClassMetadata
      )

      val beanDefinitionReferenceWriter = new BeanDefinitionReferenceWriter(
        beanDefinitionWriter.getBeanTypeName,
        beanDefinitionWriter.getBeanDefinitionName,
        beanDefinitionWriter.getOriginatingElement,
        beanDefinitionWriter.getAnnotationMetadata
      )

      val annotationMetadata = new AnnotationMetadataHierarchy(concreteClassMetadata)

      beanDefinitionWriter.visitBeanDefinitionConstructor(
        annotationMetadata,
        false,
        constructorParameterInfo.parameters,
        constructorParameterInfo.annotationMetadata,
        constructorParameterInfo.genericTypes
      )

      concreteClass.impl.body.foreach {
        case defDef: Global#DefDef if !(defDef.symbol.isConstructor || defDef.symbol.isAbstract || defDef.symbol.isStable) => {
          visitExecutableMethod(defDef, beanDefinitionWriter)
        }
        case valDef: Global#ValDef if valDef.mods.isMutable => {
          val fieldAnnotationMetadata = Globals.metadataBuilder.getOrCreate(ScalaSymbolElement(valDef.symbol))

          val isInjected = fieldAnnotationMetadata.hasStereotype(classOf[Inject])
          val isValue = !isInjected && (fieldAnnotationMetadata.hasStereotype(classOf[Value]) || fieldAnnotationMetadata.hasStereotype(classOf[Property]))
          if (isInjected || isValue) {

            val isPrivate = valDef.mods.isPrivate
            val requiresReflection = isPrivate // TODO || modelUtils.isInheritedAndNotPublic(this.concreteClass, declaringClass, variable)

            if (isValue) {
              beanDefinitionWriter.visitFieldValue(
                concreteClass.symbol.fullName,
                Globals.argTypeForValDef(valDef),
                valDef.name.toString.trim,
                requiresReflection,
                fieldAnnotationMetadata,
                //TODO genericUtils.resolveGenericTypes(`type`, Collections.emptyMap), TODO
                Collections.emptyMap(),
                false)
            } else {
              beanDefinitionWriter.visitFieldInjectionPoint(
                concreteClass.symbol.fullName,
                Globals.argTypeForValDef(valDef),
                valDef.name.toString.trim,
                requiresReflection,
                fieldAnnotationMetadata,
                //TODO genericUtils.resolveGenericTypes(`type`, Collections.emptyMap), TODO
                Collections.emptyMap()
              )
            }
          }
        }
        case _ => ()
      }

      beanDefinitionWriter.visitBeanDefinitionEnd()
      beanDefinitionWriter.accept(visitorContext)

      beanDefinitionReferenceWriter.accept(visitorContext)
    }
  }
}
