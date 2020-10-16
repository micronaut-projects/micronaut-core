package io.micronaut.scala

import java.lang.annotation.Annotation
import java.util
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

import io.micronaut.annotation.processing.ExecutableElementParamInfo
import io.micronaut.aop.writer.AopProxyWriter
import io.micronaut.context.annotation._
import io.micronaut.core.annotation.{AnnotationMetadata, AnnotationValue, Internal}
import io.micronaut.core.naming.NameUtils
import io.micronaut.core.value.OptionalValues
import io.micronaut.inject.annotation.{AnnotationMetadataHierarchy, DefaultAnnotationMetadata}
import io.micronaut.inject.configuration.PropertyMetadata
import io.micronaut.inject.processing.ProcessedTypes
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.inject.writer.{BeanDefinitionReferenceWriter, BeanDefinitionVisitor, BeanDefinitionWriter, ExecutableMethodWriter}
import io.micronaut.scala.AnnBeanElementVisitor._
import javax.inject.{Inject, Qualifier, Scope}
import javax.lang.model.`type`.{TypeKind, TypeMirror}
import javax.lang.model.element.ElementKind.FIELD
import javax.lang.model.element.{ExecutableElement, Name, PackageElement, TypeElement, VariableElement}

import scala.collection.mutable
import scala.tools.nsc.Global

object AnnBeanElementVisitor {
  val AROUND_TYPE = "io.micronaut.aop.Around"
  val ANN_CONSTRAINT = "javax.validation.Constraint"
  val ANN_VALID = "javax.validation.Valid"
  val ANN_CONFIGURATION_ADVICE = "io.micronaut.runtime.context.env.ConfigurationAdvice"
}

class AnnBeanElementVisitor(concreteClass:Global#ClassDef, visitorContext:VisitorContext) {
  private val concreteClassMetadata = Globals.metadataBuilder.getOrCreate(new SymbolFacade(concreteClass.symbol))

  private val isFactoryType = concreteClassMetadata.hasStereotype(classOf[Factory])

  private val isConfigurationPropertiesType = concreteClassMetadata.hasDeclaredStereotype(classOf[ConfigurationReader]) || concreteClassMetadata.hasDeclaredStereotype(classOf[EachProperty])

  private val factoryMethodIndex = new AtomicInteger(0)

  private val originatingElement = new ScalaClassElement(concreteClass.symbol.asInstanceOf[Global#ClassSymbol], concreteClassMetadata, visitorContext)

  private val isAopProxyType = concreteClassMetadata.hasStereotype("io.micronaut.aop.Around") || concreteClassMetadata.hasStereotype(classOf[Executable])

  private val isExecutableType = isAopProxyType || concreteClassMetadata.hasStereotype(classOf[Executable]);

  private val constructorParameterInfo = ExecutableElementParamInfo.populateParameterData(concreteConstructorFor(concreteClass))

  private val hasQualifier = concreteClassMetadata.hasStereotype(classOf[Qualifier]) && !concreteClass.symbol.isAbstract

  private val isDeclaredBean = isExecutableType || concreteClassMetadata.hasStereotype(classOf[Scope]) || concreteClassMetadata.hasStereotype(classOf[DefaultScope]) || constructorParameterInfo.metadata.hasStereotype(classOf[Inject]) || hasQualifier

  private val metadataBuilder = new ScalaConfigurationMetadataBuilder()

  val beanDefinitionWriters = new mutable.HashMap[ElementFacade, BeanDefinitionVisitor]

  private def concreteConstructorFor(classDef: Global#ClassDef): Option[Global#DefDef] = {
    val constructors = classDef.impl.body.filter(_.symbol.isConstructor)

    constructors.headOption.map(_.asInstanceOf[Global#DefDef])
  }

  private def isExecutableThroughType(
    enclosingElement: Global#ClassSymbol,
    annotationMetadataHierarchy: AnnotationMetadata,
    declaredMetadata: AnnotationMetadata,
    isPublic: Boolean) = (isExecutableType && (isPublic || concreteClass.symbol == enclosingElement)) ||
              annotationMetadataHierarchy.hasDeclaredStereotype(classOf[Executable]) ||
              declaredMetadata.hasStereotype(classOf[Executable])

  private def visitAnnotatedMethod(method: Global#DefDef,
                                   annotationMetadata: AnnotationMetadata,
                                   beanDefinitionWriter: BeanDefinitionWriter): Unit = {
//    val params = populateParameterData(null, method, Collections.emptyMap)
//    val returnType = method.getReturnType
//    val declaringClass = modelUtils.classElementFor(method)
//
//    if (declaringClass == null) return
//
//    val isParent = !(declaringClass.getQualifiedName == this.concreteClass.getQualifiedName)
//    val overridingMethod = modelUtils.overridingOrHidingMethod(method, this.concreteClass, false).orElse(method)
//    val overridingClass = modelUtils.classElementFor(overridingMethod)
//    val overridden = isParent && overridingClass != null && !(overridingClass.getQualifiedName == declaringClass.getQualifiedName)
//
//    val isPackagePrivate = modelUtils.isPackagePrivate(method)
//    val isPrivate = modelUtils.isPrivate(method)
//    if (overridden && !(isPrivate || isPackagePrivate)) { // bail out if the method has been overridden, since it will have already been handled
//      return
//    }
//
//    val packageOfOverridingClass = elementUtils.getPackageOf(overridingMethod)
//    val packageOfDeclaringClass = elementUtils.getPackageOf(declaringClass)
//    val isPackagePrivateAndPackagesDiffer = overridden && isPackagePrivate && !(packageOfOverridingClass.getQualifiedName == packageOfDeclaringClass.getQualifiedName)
//    var requiresReflection = isPrivate || isPackagePrivateAndPackagesDiffer
//    val overriddenInjected = overridden && annotationUtils.getAnnotationMetadata(overridingMethod).hasDeclaredStereotype(classOf[Inject])
//
//    if (isParent && isPackagePrivate && !isPackagePrivateAndPackagesDiffer && overriddenInjected) { // bail out if the method has been overridden by another method annotated with @Inject
//      return
//    }
//    if (isParent && overridden && !overriddenInjected && !isPackagePrivateAndPackagesDiffer && !isPrivate) { // bail out if the overridden method is package private and in the same package
//      // and is not annotated with @Inject
//      return
//    }
//    if (!requiresReflection && modelUtils.isInheritedAndNotPublic(this.concreteClass, declaringClass, method)) requiresReflection = true
//
    val requiresReflection = method.symbol.isPrivate // || isPackagePrivateAndPackagesDiffer

    val params = ExecutableElementParamInfo.populateParameterData(Some(method))

    if (annotationMetadata.hasDeclaredStereotype(ProcessedTypes.POST_CONSTRUCT)) {
      //      final AopProxyWriter aopWriter = resolveAopWriter(writer);
      //      if (aopWriter != null && !aopWriter.isProxyTarget()) writer = aopWriter
      beanDefinitionWriter.visitPostConstructMethod(
        concreteClass.symbol.fullName,
        requiresReflection,
        Globals.argTypeForTypeTree(method.tpt.asInstanceOf[Global#TypeTree]),
        method.symbol.nameString,
        params.parameters,
        params.parameterMetadata,
        params.genericTypes,
        annotationMetadata
      )
    } else if (annotationMetadata.hasDeclaredStereotype(ProcessedTypes.PRE_DESTROY)) {
//        val aopWriter = resolveAopWriter(writer)
//        if (aopWriter != null && !aopWriter.isProxyTarget) writer = aopWriter
      beanDefinitionWriter.visitPreDestroyMethod(
            concreteClass.symbol.fullName,
            requiresReflection,
            Globals.argTypeForTypeTree(method.tpt.asInstanceOf[Global#TypeTree]),
            method.symbol.nameString,
            params.parameters,
            params.parameterMetadata,
            params.genericTypes,
            annotationMetadata)
    } else if (annotationMetadata.hasDeclaredStereotype(classOf[Inject]) || annotationMetadata.hasDeclaredStereotype(classOf[ConfigurationInject])) {
        //val writer = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName)
       beanDefinitionWriter.visitMethodInjectionPoint(
        concreteClass.symbol.fullName,
        requiresReflection,
        Globals.argTypeForTypeTree(method.tpt.asInstanceOf[Global#TypeTree]),
        method.symbol.nameString,
        params.parameters,
        params.parameterMetadata,
        params.genericTypes,
        annotationMetadata)
      } //      else error("Unexpected call to visitAnnotatedMethod(%s)", method)
  }

  private def addAnnotation(annotationMetadata: AnnotationMetadata, annotation: String) =
    Globals.metadataBuilder.annotate(annotationMetadata, io.micronaut.core.annotation.AnnotationValue.builder(annotation).build)

  private def visitType(classElement: Global#ClassDef, classAnnotationMetadata: AnnotationMetadata):Unit = {
    val classElementQualifiedName = classElement.symbol.fullName
//    if (visitedTypes.contains(classElementQualifiedName)) { // bail out if already visited
//      return o
//    }
    val isInterface = classElement.symbol.isInterface
//    visitedTypes.add(classElementQualifiedName)
    var typeAnnotationMetadata = classAnnotationMetadata
    if (isConfigurationPropertiesType) { // TODO: populate documentation
      val configurationMetadata = metadataBuilder.visitProperties(concreteClass.symbol, null)
      if (isInterface) {
        typeAnnotationMetadata = addAnnotation(typeAnnotationMetadata, ANN_CONFIGURATION_ADVICE)
      }
    }
//    if (typeAnnotationMetadata.hasStereotype(INTRODUCTION_TYPE)) {
//      val aopProxyWriter = createIntroductionAdviceWriter(classElement)
//      val constructor = if (JavaModelUtils.resolveKind(classElement, ElementKind.CLASS).isPresent) modelUtils.concreteConstructorFor(classElement, annotationUtils)
//      else null
//      val constructorData = if (constructor != null) populateParameterData(null, constructor, Collections.emptyMap)
//      else null
//      if (constructorData != null) aopProxyWriter.visitBeanDefinitionConstructor(constructorData.getAnnotationMetadata, constructorData.isRequiresReflection, constructorData.getParameters, constructorData.getParameterMetadata, constructorData.getGenericTypes)
//      else aopProxyWriter.visitBeanDefinitionConstructor(AnnotationMetadata.EMPTY_METADATA, false)
//      beanDefinitionWriters.put(classElementQualifiedName, aopProxyWriter)
//      visitIntroductionAdviceInterface(classElement, typeAnnotationMetadata, aopProxyWriter)
//      if (!isInterface) {
//        val elements = classElement.getEnclosedElements.stream.filter // already handled the public ctor
//        ((element: Element) => element.getKind ne CONSTRUCTOR).collect(Collectors.toList)
//        scan(elements, o)
//      }
//      else null
//    }
//    else {
      val enclosingElement = classElement.symbol.owner
      // don't process inner class unless this is the visitor for it
      val qualifiedName = concreteClass.symbol.fullName
      if (!enclosingElement.isClass || qualifiedName == classElementQualifiedName) {
        if (qualifiedName == classElementQualifiedName) if (isDeclaredBean) { // we know this class has supported annotations so we need a beandef writer for it
          val packageElement = classElement.symbol.owner
//          if (packageElement.isUnnamed) {
//            error(classElement, "Micronaut beans cannot be in the default package")
//            return null
//          }
//          val beanDefinitionWriter = getOrCreateBeanDefinitionWriter(classElement, qualifiedName)
//          if (isAopProxyType) {
//            if (classElement.symbol.isFinal) {
//              //error(classElement, "Cannot apply AOP advice to final class. Class must be made non-final to support proxying: " + classElement)
//              return null
//            }
//            val interceptorTypes = classAnnotationMetadata.getAnnotationNamesByStereotype(AROUND_TYPE).toArray
//            resolveAopProxyWriter(beanDefinitionWriter, aopSettings, false, this.constructorParameterInfo, interceptorTypes)
//          }
//        }
//        else if (modelUtils.isAbstract(classElement)) return null
//        val elements = classElement.getEnclosedElements.stream.filter((element: Element) => element.getKind ne CONSTRUCTOR).collect(Collectors.toList)
//        if (isConfigurationPropertiesType) { // handle non @Inject, @Value fields as config properties
//          val members = elementUtils.getAllMembers(classElement)
//          ElementFilter.fieldsIn(members).forEach((field: VariableElement) => {
//            def foo(field: VariableElement) = {
//              val fieldAnnotationMetadata = annotationUtils.getAnnotationMetadata(field)
//              val isConfigBuilder = fieldAnnotationMetadata.hasStereotype(classOf[ConfigurationBuilder])
//              if (modelUtils.isStatic(field)) return
//              // its common for builders to be initialized, so allow final
//              if (!modelUtils.isFinal(field) || isConfigBuilder) visitConfigurationProperty(field, fieldAnnotationMetadata)
//            }
//
//            foo(field)
//          })
//          ElementFilter.methodsIn(members).forEach((method: ExecutableElement) => {
//            def foo(method: ExecutableElement) = {
//              val isCandidateMethod = !modelUtils.isStatic(method) && !modelUtils.isPrivate(method) && !modelUtils.isAbstract(method)
//              if (isCandidateMethod) {
//                val e = method.getEnclosingElement
//                if (e.isInstanceOf[TypeElement] && !(e == classElement)) {
//                  val methodName = method.getSimpleName.toString
//                  if (method.getParameters.size == 1 && NameUtils.isSetterName(methodName)) visitConfigurationPropertySetter(method)
//                  else if (NameUtils.isGetterName(methodName)) {
//                    val writer = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName)
//                    if (!writer.isValidated) writer.setValidated(IS_CONSTRAINT.test(annotationUtils.getAnnotationMetadata(method)))
//                  }
//                }
//              }
//            }
//
//            foo(method)
//          })
//        }
//        else {
//          val superClass = modelUtils.superClassFor(classElement)
//          if (superClass != null && !modelUtils.isObjectClass(superClass)) superClass.accept(this, o)
//        }
//        scan(elements, o)
      }
    }
  }

  private def visitExecutableMethod(method: Global#DefDef, methodAnnotationMetadata: AnnotationMetadata, beanDefinitionWriter: BeanDefinitionWriter):Unit = {
    val returnType = method.symbol.thisType

    val declaringClass = method.symbol.owner
    if (declaringClass == null || declaringClass.isRoot) {
      return
    }

    val isOwningClass: Boolean = declaringClass.fullName == concreteClass.symbol.fullName

    if (isOwningClass && concreteClass.symbol.isAbstract) { // && !(concreteClassMetadata.hasStereotype(INTRODUCTION_TYPE))) {
      return
    }

//    if (!(isOwningClass) && modelUtils.overridingOrHidingMethod(method, concreteClass, true).isPresent) {
//      return
//    }

    val returnTypeGenerics: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef]
    //genericUtils.resolveBoundGenerics(method.getEnclosingElement.asInstanceOf[TypeElement], returnType, genericUtils.buildGenericTypeArgumentElementInfo(concreteClass)).forEach((key: String, value: TypeMirror) => returnTypeGenerics.put(key, modelUtils.resolveTypeReference(value)))

    val resolvedReturnType: Any = returnType //modelUtils.resolveTypeReference(returnType)

    val enclosingElement = method.symbol.enclClass

//    var boundTypes: Map[String, AnyRef] = genericUtils.buildGenericTypeArgumentInfo(concreteClass).get(enclosingElement.getQualifiedName.toString)
//    if (boundTypes == null) {
//     var boundTypes: Map[String, AnyRef]  = Collections.emptyMap()
//    }

    val params = ExecutableElementParamInfo.populateParameterData(Some(method)) //, boundTypes)

    // This method requires pre-processing. See Executable#processOnStartup()
    val preprocess: Boolean = methodAnnotationMetadata.isTrue(classOf[Executable], "processOnStartup")
    if (preprocess) {
      beanDefinitionWriter.setRequiresMethodProcessing(true)
    }

    var typeRef: Any = enclosingElement.fullName //modelUtils.resolveTypeReference(method.getEnclosingElement)
//    if (typeRef == null) {
//      typeRef = modelUtils.resolveTypeReference(concreteClass)
//    }

    //val proxyWriter: AopProxyWriter = resolveAopWriter(beanWriter)
    var executableMethodWriter:ExecutableMethodWriter = null
 //   if (proxyWriter == null || proxyWriter.isProxyTarget) {
      executableMethodWriter = beanDefinitionWriter.visitExecutableMethod(
        typeRef,
        resolvedReturnType,
        resolvedReturnType,
        returnTypeGenerics,
        method.name.toString,
        params.parameters,
        params.genericParameters,
        params.parameterMetadata,
        params.genericTypes,
        methodAnnotationMetadata,
        enclosingElement.isInterface,
        false) //m ethod.isDefault)
//    }


//    if (methodAnnotationMetadata.hasStereotype(classOf[Adapter])) {
//      visitAdaptedMethod(method, methodAnnotationMetadata)
//    }


    // shouldn't visit around advice on an introduction advice instance
    if (!((beanDefinitionWriter.isInstanceOf[AopProxyWriter]))) {
      val isConcrete: Boolean = concreteClass.symbol.isConcreteClass
      val isPublic: Boolean = method.symbol.isPublic || method.symbol.hasPackageFlag
//      if ((isAopProxyType && isPublic) || (!(isAopProxyType) && methodAnnotationMetadata.hasStereotype(AROUND_TYPE)) || (methodAnnotationMetadata.hasDeclaredStereotype(AROUND_TYPE) && isConcrete)) {
//        val interceptorTypes: Array[AnyRef] = methodAnnotationMetadata.getAnnotationNamesByStereotype(AROUND_TYPE).toArray
//        val settings: OptionalValues[Boolean] = methodAnnotationMetadata.getValues(AROUND_TYPE, classOf[Boolean])
//        val aopProxyWriter: AopProxyWriter = resolveAopProxyWriter(beanWriter, settings, false, this.constructorParameterInfo, interceptorTypes)
//        aopProxyWriter.visitInterceptorTypes(interceptorTypes)
//        val isAnnotationReference: Boolean = methodAnnotationMetadata.isInstanceOf[AnnotationMetadataReference]
//        var aroundMethodMetadata: AnnotationMetadata = null
//        if (!(isAnnotationReference) && executableMethodWriter != null) {
//          aroundMethodMetadata = new AnnotationMetadataReference(executableMethodWriter.getClassName, methodAnnotationMetadata)
//        }
//        else {
//          if (methodAnnotationMetadata.isInstanceOf[AnnotationMetadataHierarchy]) {
//            aroundMethodMetadata = methodAnnotationMetadata
//          }
//          else {
//            aroundMethodMetadata = new AnnotationMetadataHierarchy(concreteClassMetadata, methodAnnotationMetadata)
//          }
//        }
//        if (method.symbol.isFinal) {
//          if (methodAnnotationMetadata.hasDeclaredStereotype(AROUND_TYPE)) {
//            error(method, "Method defines AOP advice but is declared final. Change the method to be non-final in order for AOP advice to be applied.")
//          }
//          else {
//            if (isAopProxyType && isPublic && !(declaringClass == concreteClass)) {
//              if (executableMethodWriter == null) {
//                beanDefinitionWriter.visitExecutableMethod(
//                  typeRef,
//                  resolvedReturnType,
//                  resolvedReturnType,
//                  returnTypeGenerics,
//                  method.symbol.name.toString,
//                  params.parameters,
//                  params.genericParameters,
//                  params.annotationMetadata,
//                  params.genericTypes,
//                  methodAnnotationMetadata,
//                  enclosingElement.isInterface, false) //isDefault)
//              }
//            }
//            else {
//              error(method, "Public method inherits AOP advice but is declared final. Either make the method non-public or apply AOP advice only to public methods declared on the class.")
//            }
//          }
//        }
//        else {
//          if (aroundMethodMetadata.hasStereotype(classOf[Executable])) {
//            aopProxyWriter.visitExecutableMethod(typeRef, resolvedReturnType, resolvedReturnType, returnTypeGenerics, method.getSimpleName.toString, params.getParameters, params.getGenericParameters, params.getParameterMetadata, params.getGenericTypes, aroundMethodMetadata, JavaModelUtils.isInterface(enclosingElement), method.isDefault)
//          }
//          aopProxyWriter.visitAroundMethod(typeRef, resolvedReturnType, resolvedReturnType, returnTypeGenerics, method.getSimpleName.toString, params.getParameters, params.getGenericParameters, params.getParameterMetadata, params.getGenericTypes, aroundMethodMetadata, JavaModelUtils.isInterface(enclosingElement), method.isDefault)
//        }
      }
      else {
        if (executableMethodWriter == null) {
          beanDefinitionWriter.visitExecutableMethod(
            typeRef,
            resolvedReturnType,
            resolvedReturnType,
            returnTypeGenerics,
            method.name.toString,
            params.parameters,
            params.genericParameters,
            params.parameterMetadata,
            params.genericTypes,
            methodAnnotationMetadata,
            enclosingElement.isInterface,
            false) //method.isDefault)
        }
      }
   }

  private def  visitExecutable(method: Global#DefDef,
                                   beanDefinitionWriter: BeanDefinitionWriter):Unit = {
    val annotationMetadata = Globals.metadataBuilder.getOrCreate(SymbolFacade(method.symbol))

    val methodAnnotationMetadata = if (annotationMetadata.isInstanceOf[AnnotationMetadataHierarchy]) annotationMetadata
    else new AnnotationMetadataHierarchy(concreteClassMetadata, annotationMetadata)

    if (isFactoryType && methodAnnotationMetadata.hasDeclaredStereotype(classOf[Bean], classOf[Scope]) /* && (returnKind eq TypeKind.DECLARED) */) {
      visitBeanFactoryMethod(method, methodAnnotationMetadata)
    } else {
      val injected = annotationMetadata.hasDeclaredStereotype(classOf[Inject])
      val postConstruct = annotationMetadata.hasDeclaredStereotype(ProcessedTypes.POST_CONSTRUCT)
      val preDestroy = annotationMetadata.hasDeclaredStereotype(ProcessedTypes.PRE_DESTROY)
      if (injected || postConstruct || preDestroy || annotationMetadata.hasDeclaredStereotype(classOf[ConfigurationInject])) {
        if (isDeclaredBean) {
          visitAnnotatedMethod(method, annotationMetadata, beanDefinitionWriter)
        } else if (injected) { // DEPRECATE: This behaviour should be deprecated in 2.0
          visitAnnotatedMethod(method, annotationMetadata, beanDefinitionWriter)
        }
      }

      val hasInvalidModifiers = method.symbol.isAbstract || method.symbol.isStatic || method.symbol.isPrivate || methodAnnotationMetadata.hasAnnotation(classOf[Internal])
      val isPublic = method.symbol.isPublic && !hasInvalidModifiers
      val isExecutable = !hasInvalidModifiers && (isExecutableThroughType(method.symbol.owner.asInstanceOf[Global#ClassSymbol], methodAnnotationMetadata, annotationMetadata, isPublic) || annotationMetadata.hasStereotype(AROUND_TYPE))

      var hasConstraints = false
//      if (isDeclaredBean && !methodAnnotationMetadata.hasStereotype(ANN_VALID) && method.getParameters.stream.anyMatch((p: VariableElement) => annotationUtils.hasStereotype(p, ANN_CONSTRAINT) || annotationUtils.hasStereotype(p, ANN_VALID))) {
//        hasConstraints = true
//        methodAnnotationMetadata = javaVisitorContext.getAnnotationUtils.newAnnotationBuilder.annotate(methodAnnotationMetadata, io.micronaut.core.annotation.AnnotationValue.builder(ANN_VALIDATED).build)
//      }

      if (isDeclaredBean && isExecutable) {
        visitExecutableMethod(method, methodAnnotationMetadata, beanDefinitionWriter)
      } else if (isConfigurationPropertiesType && !method.symbol.isPrivate && !method.symbol.isStatic) {
        val methodName = method.symbol.nameString
        if (NameUtils.isSetterName(methodName) && method.symbol.paramss.size == 1) {
          visitConfigurationPropertySetter(method, methodAnnotationMetadata, beanDefinitionWriter)
        } else if (NameUtils.isGetterName(methodName)) {
            if (!beanDefinitionWriter.isValidated) {
              beanDefinitionWriter.setValidated(
                methodAnnotationMetadata.hasStereotype(ANN_CONSTRAINT) ||
                methodAnnotationMetadata.hasStereotype(ANN_VALID))
            }
        }
      } else if (isPublic && hasConstraints) {
        visitExecutableMethod(method, methodAnnotationMetadata, beanDefinitionWriter)
      }
    }
  }

  private def visitConfigurationPropertySetter(method: Global#DefDef, methodAnnotationMetadata:AnnotationMetadata, beanDefinitionVisitor: BeanDefinitionVisitor): Unit = {
    val parameter = method.vparamss.head.head
    val fieldType = Globals.argTypeForValDef(parameter)
//    val fieldType: Any = Globals.argTypeForTree(valueType.)
    var genericTypes: util.Map[String, AnyRef] = Collections.emptyMap()
//    val typeKind: TypeKind = valueType.getKind

//    if (!(typeKind.isPrimitive || (typeKind eq ARRAY))) genericTypes = genericUtils.resolveGenericTypes(valueType, Collections.emptyMap)
    val declaringClass = method.symbol.owner.fullName
    if (declaringClass != null) {
      val propertyName = NameUtils.getPropertyNameForSetter(method.symbol.nameString)
      val isInterface = parameter.symbol.isInterface
      if (methodAnnotationMetadata.hasStereotype(classOf[ConfigurationBuilder])) {
          beanDefinitionVisitor.visitConfigBuilderMethod(fieldType, NameUtils.getterNameFor(propertyName), methodAnnotationMetadata, metadataBuilder, isInterface)
         // try visitConfigurationBuilder(declaringClass, method, valueType, beanDefinitionVisitor)
         // finally beanDefinitionVisitor.visitConfigBuilderEnd()
      } else {
//        if (shouldExclude(configurationMetadata, propertyName)) return
        val docComment: String = "" //elementUtils.getDocComment(method)
        val setterName: String = method.symbol.nameString
        val propertyMetadata = metadataBuilder.visitProperty(
          concreteClass.symbol,
          method.symbol.owner,
          getPropertyMetadataTypeReference(parameter.symbol).toString,
          propertyName, docComment, null)
        val annotationMetadata: AnnotationMetadata = addPropertyMetadata(AnnotationMetadata.EMPTY_METADATA, propertyMetadata)
        val requiresReflection = if (method.symbol.isPublic) {
            false
          } else if (method.symbol.hasPackageFlag || method.symbol.isProtected) {
            val declaringPackage = method.symbol.owner.packageObject
            val concretePackage = concreteClass.symbol.packageObject
            !(declaringPackage.fullName == concretePackage.fullName)
          } else {
            true
          }
        beanDefinitionVisitor.visitSetterValue(
          declaringClass,
          Globals.argTypeForTypeTree(method.tpt.asInstanceOf[Global#TypeTree]),
          annotationMetadata,
          requiresReflection,
          fieldType,
          setterName,
          genericTypes,
          Globals.metadataBuilder.getOrCreate(SymbolFacade(parameter.symbol)),
          true)
      }
    }
  }

  private def getPropertyMetadataTypeReference(valueType: Global#Symbol) =
    //if (modelUtils.isOptional(valueType)) genericUtils.getFirstTypeArgument(valueType).map((typeMirror: TypeMirror) => modelUtils.resolveTypeName(typeMirror)).orElseGet(() => modelUtils.resolveTypeName(valueType))
    //else
    valueType

  private def addPropertyMetadata(annotationMetadata: AnnotationMetadata, propertyMetadata: PropertyMetadata) =
    DefaultAnnotationMetadata.mutateMember(
      annotationMetadata,
      classOf[PropertySource].getName,
      AnnotationMetadata.VALUE_MEMBER,
      Collections.singletonList(
        new AnnotationValue[Annotation](
          classOf[Property].getName,
          Collections.singletonMap[CharSequence, Object]("name", propertyMetadata.getPath))
      )
    )


  private def createFactoryBeanMethodWriterFor(method: Global#DefDef, producedElement: Global#Type) = {
    val annotationMetadata = Globals.metadataBuilder.buildForParent(
      SymbolFacade(producedElement.typeSymbol),
      SymbolFacade(method.symbol), true)
    val producedPackageElement = producedElement.typeSymbol.enclosingPackage.fullName
    val definingPackageElement = method.symbol.owner.enclosingPackage.fullName
    val isInterface = producedElement.typeSymbol.isInterface
    val packageName = producedPackageElement.toString
    val beanDefinitionPackage = definingPackageElement.toString
    val shortClassName = producedElement.typeSymbol.nameString
    val upperCaseMethodName = NameUtils.capitalize(method.symbol.name.toString())
    val factoryMethodBeanDefinitionName = beanDefinitionPackage + ".$" + concreteClass.symbol.nameString + "$" + upperCaseMethodName + factoryMethodIndex.getAndIncrement + "Definition"
    new BeanDefinitionWriter(
      packageName,
      shortClassName,
      factoryMethodBeanDefinitionName,
      Globals.argTypeForTypeTree(method.tpt.asInstanceOf[Global#TypeTree]).toString, //modelUtils.resolveTypeReference(producedElement).toString,
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
      beanDefinitionWriters += new SymbolFacade(beanMethod.symbol) -> beanMethodWriter
      val beanMethodDeclaringType: Any = beanMethod.symbol.owner.fullName
      val methodAnnotationMetadata: AnnotationMetadata = Globals.metadataBuilder.buildForParent(SymbolFacade(producedElement.typeSymbol), new SymbolFacade(beanMethod.symbol))
      beanMethodWriter.visitBeanFactoryMethod(
        beanMethodDeclaringType,
        Globals.argTypeForTypeTree(beanMethod.tpt.asInstanceOf[Global#TypeTree]),
        beanMethodName,
        methodAnnotationMetadata,
        beanMethodParameters,
        beanMethodParams.parameterMetadata,
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

  def visitVariable(valDef: Global#ValDef, beanDefinitionWriter:BeanDefinitionWriter):Unit = { // assuming just fields, visitExecutable should be handling params for method calls
    val fieldAnnotationMetadata = Globals.metadataBuilder.getOrCreate(SymbolFacade(valDef.symbol))

    val isInjected = fieldAnnotationMetadata.hasStereotype(classOf[Inject])
    val isValue = !isInjected && (fieldAnnotationMetadata.hasStereotype(classOf[Value]) || fieldAnnotationMetadata.hasStereotype(classOf[Property]))
    if (isInjected || isValue) {

      val isPrivate = valDef.mods.isPrivate
      val requiresReflection = isPrivate // TODO || modelUtils.isInheritedAndNotPublic(this.concreteClass, declaringClass, variable)

      val genericTypeMap = Globals.genericTypesForTree(
        Globals.argTypeForTree(valDef.tpt).toString,
        valDef.tpt
      )

      if (isValue) {
        beanDefinitionWriter.visitFieldValue(
          concreteClass.symbol.fullName,
          Globals.argTypeForValDef(valDef),
          valDef.name.toString.trim,
          requiresReflection,
          fieldAnnotationMetadata,
          genericTypeMap,
          false)
      } else {
        beanDefinitionWriter.visitFieldInjectionPoint(
          concreteClass.symbol.fullName,
          Globals.argTypeForValDef(valDef),
          valDef.name.toString.trim,
          requiresReflection,
          fieldAnnotationMetadata,
          genericTypeMap
        )
      }
    }
//    if (variable.getKind ne FIELD) return null
//    if (modelUtils.isStatic(variable) || modelUtils.isFinal(variable)) return null
//    var fieldAnnotationMetadata = annotationUtils.getAnnotationMetadata(variable)
//    if (fieldAnnotationMetadata.hasDeclaredAnnotation("org.jetbrains.annotations.Nullable")) fieldAnnotationMetadata = DefaultAnnotationMetadata.mutateMember(fieldAnnotationMetadata, "javax.annotation.Nullable", Collections.emptyMap)
//    val isInjected = fieldAnnotationMetadata.hasStereotype(classOf[Inject])
//    val isValue = !isInjected && (fieldAnnotationMetadata.hasStereotype(classOf[Value]) || fieldAnnotationMetadata.hasStereotype(classOf[Property]))
//    if (isInjected || isValue) {
//      val fieldName = variable.getSimpleName
//      val writer = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName)
//      val declaringClass = modelUtils.classElementFor(variable)
//      if (declaringClass == null) return null
//      val isPrivate = modelUtils.isPrivate(variable)
//      val requiresReflection = isPrivate || modelUtils.isInheritedAndNotPublic(this.concreteClass, declaringClass, variable)
//      if (!writer.isValidated) writer.setValidated(IS_CONSTRAINT.test(fieldAnnotationMetadata))
//      val `type` = variable.asType
//      if ((`type`.getKind eq TypeKind.ERROR) && !processingOver) throw new BeanDefinitionInjectProcessor.PostponeToNextRoundException
//      val fieldType = modelUtils.resolveTypeReference(`type`)
//      if (isValue) writer.visitFieldValue(modelUtils.resolveTypeReference(declaringClass), fieldType, fieldName.toString, requiresReflection, fieldAnnotationMetadata, genericUtils.resolveGenericTypes(`type`, Collections.emptyMap), isConfigurationPropertiesType)
//      else writer.visitFieldInjectionPoint(modelUtils.resolveTypeReference(declaringClass), fieldType, fieldName.toString, requiresReflection, fieldAnnotationMetadata, genericUtils.resolveGenericTypes(`type`, Collections.emptyMap))
//    }
  }

  def visit(): Unit = {
    if (concreteClass.symbol.annotations.nonEmpty && !concreteClass.symbol.isAbstractType) {
      val concreteClassMetadata = Globals.metadataBuilder.getOrCreate(SymbolFacade(concreteClass.symbol))

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
        constructorParameterInfo.parameterMetadata,
        constructorParameterInfo.genericTypes
      )

      visitType(concreteClass, Globals.metadataBuilder.getOrCreate(SymbolFacade(concreteClass.symbol)))

      concreteClass.impl.body.foreach {
        case defDef: Global#DefDef if !defDef.symbol.isConstructor => {
          visitExecutable(defDef, beanDefinitionWriter)
        }
        case valDef: Global#ValDef if valDef.mods.isMutable => {
          visitVariable(valDef, beanDefinitionWriter)
        }
        case _ => ()
      }

      beanDefinitionWriter.visitBeanDefinitionEnd()
      beanDefinitionWriter.accept(visitorContext)

      beanDefinitionReferenceWriter.accept(visitorContext)
    }
  }
}
