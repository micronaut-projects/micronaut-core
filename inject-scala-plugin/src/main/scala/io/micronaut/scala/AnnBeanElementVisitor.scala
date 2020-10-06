package io.micronaut.scala

import java.util.Collections

import io.micronaut.context.annotation.{ConfigurationInject, Property, Value}
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy
import io.micronaut.inject.processing.ProcessedTypes
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.inject.writer.{BeanDefinitionReferenceWriter, BeanDefinitionWriter}
import javax.inject.Inject

import scala.tools.nsc.Global

class AnnBeanElementVisitor(classDef:Global#ClassDef, visitorContext:VisitorContext) {

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
        classDef.symbol.fullName,
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
                                    concreteClassMetadata: AnnotationMetadata,
                                    beanDefinitionWriter: BeanDefinitionWriter) {
    val annotationMetadata = Globals.metadataBuilder.getOrCreate(new ScalaSymbolElement(method.symbol))

//    val methodAnnotationMetadata = if (annotationMetadata.isInstanceOf[AnnotationMetadataHierarchy]) annotationMetadata
//    else new AnnotationMetadataHierarchy(concreteClassMetadata, annotationMetadata)

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

  def visit(): Unit = {
    if (classDef.symbol.annotations.nonEmpty) if (!classDef.symbol.isAbstractType) {
      val concreteClassMetadata = Globals.metadataBuilder.getOrCreate(ScalaSymbolElement(classDef.symbol))

      val constructor = concreteConstructorFor(classDef)

      val constructorParameterInfo = ExecutableElementParamInfo.populateParameterData(constructor)

      val beanDefinitionWriter = new BeanDefinitionWriter(
        classDef.symbol.enclosingPackageClass.fullName,
        classDef.name.toString,
        new ScalaClassElement(classDef.symbol, concreteClassMetadata, visitorContext),
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

      classDef.impl.body.foreach {
        case defDef: Global#DefDef if !(defDef.symbol.isConstructor || defDef.symbol.isAbstract || defDef.symbol.isStable) => {
          visitExecutableMethod(defDef, annotationMetadata, beanDefinitionWriter)
        }
        case valDef: Global#ValDef if valDef.mods.isMutable => {
          val fieldAnnotationMetadata = Globals.metadataBuilder.getOrCreate(ScalaSymbolElement(valDef.symbol))

          val isInjected = fieldAnnotationMetadata.hasStereotype(classOf[Inject])
          val isValue = !isInjected && (fieldAnnotationMetadata.hasStereotype(classOf[Value]) || fieldAnnotationMetadata.hasStereotype(classOf[Property]))
          if (isInjected || isValue) {

            val isPrivate = valDef.mods.isPrivate
            val requiresReflection = isPrivate // TODO || modelUtils.isInheritedAndNotPublic(this.concreteClass, declaringClass, variable)

            if (isValue) beanDefinitionWriter.visitFieldValue(
              classDef.symbol.fullName,
              Globals.argTypeForValDef(valDef),
              valDef.name.toString.trim,
              requiresReflection,
              fieldAnnotationMetadata,
              //TODO genericUtils.resolveGenericTypes(`type`, Collections.emptyMap), TODO
              Collections.emptyMap(),
              false) else beanDefinitionWriter.visitFieldInjectionPoint(
              classDef.symbol.fullName,
              Globals.argTypeForValDef(valDef),
              valDef.name.toString.trim,
              requiresReflection,
              fieldAnnotationMetadata,
              //TODO genericUtils.resolveGenericTypes(`type`, Collections.emptyMap), TODO
              Collections.emptyMap()
            )
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
