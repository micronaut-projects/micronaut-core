package io.micronaut.scala

import java.util

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.convert.value.MutableConvertibleValuesMap
import io.micronaut.core.io.service.SoftServiceLoader
import io.micronaut.core.order.OrderUtil
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.{BeanDefinitionReferenceWriter, BeanDefinitionWriter}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.tools.nsc.plugins._
import scala.tools.nsc.transform._
import scala.tools.nsc.{Global, Phase}

class CompilerPlugin(override val global: Global)
  extends Plugin {
  override val name = "compiler-plugin"
  override val description = "Compiler plugin"
  override val components =
    List(new InitPluginComponent(global),
         new TypeElementVisitorPluginComponent(global),
         new BeanDefinitionInjectPluginComponent(global),
         new FinalizePluginComponent(global)
    )
}

object Globals {
  val metadataBuilder = new ScalaAnnotationMetadataBuilder()
  val loadedVisitors: mutable.Map[String, LoadedVisitor] = new mutable.LinkedHashMap[String, LoadedVisitor]
  val visitorAttributes = new MutableConvertibleValuesMap[AnyRef]
  val beanDefinitionWriters = new mutable.HashMap[String, BeanDefinitionWriter] {}
  val beanDefinitionReferenceWriters = new mutable.HashMap[String, BeanDefinitionReferenceWriter] {}
}

class InitPluginComponent(val global: Global) extends PluginComponent with TypingTransformers {
  import global._
  override val phaseName = "compiler-plugin-type-element-init"
  override val runsAfter = List("jvm")
  override def newPhase(prev: Phase) =
    new StdPhase(prev) {
      override def apply(unit: CompilationUnit) {
        unit.body = new InitTransformer(unit).transform(unit.body)
      }
    }

  class InitTransformer(unit: CompilationUnit) extends TypingTransformer(unit) {
    override def transform(tree: Tree) = tree match {
      // Annotation are available in symbols at this point
      case classDef: ClassDef => {
        val serviceLoader = SoftServiceLoader.load(classOf[TypeElementVisitor[_, _]])
        for (definition <- asScalaIterator(serviceLoader.iterator())) {
          if (definition.isPresent) {
            val visitor = definition.load()
            Globals.loadedVisitors +=
              definition.getName -> new LoadedVisitor(classDef, visitor, new ScalaVisitorContext(global, unit.source))

            val values = new java.util.ArrayList[LoadedVisitor]()
            values.addAll(Globals.loadedVisitors.values.asJavaCollection)
            OrderUtil.reverseSort(values)
            for(loadedVisitor <- asScalaIterator(values.iterator())) {
              loadedVisitor.start()
            }
          }
        }
        super.transform(tree)
      }
      case _ => super.transform(tree)
    }
  }

  def newTransformer(unit: CompilationUnit) =
    new InitTransformer(unit)
}

class TypeElementVisitorPluginComponent(val global: Global) extends PluginComponent with TypingTransformers {
  import global._
  override val phaseName = "compiler-plugin-bean-definition"
  override val runsAfter = List("compiler-plugin-type-element-init")
  override def newPhase(prev: Phase) =
    new StdPhase(prev) {
      override def apply(unit: CompilationUnit) {
        unit.body = new TypeElementVisitorTransformer(unit).transform(unit.body)
      }
    }

  class TypeElementVisitorTransformer(unit: CompilationUnit) extends TypingTransformer(unit) {

    override def transform(tree: Tree) = tree match {
      case classDef: ClassDef => {
        Globals.loadedVisitors.values./*filter(v.matches)*/foreach{ loadedVisitor =>
          new ScalaElementVisitor(classDef.symbol, List(loadedVisitor)).visitType(classDef.symbol.asInstanceOf[Global#ClassSymbol], null)
        }
        super.transform(tree)
      }
      case _ => super.transform(tree)
    }
  }

  def newTransformer(unit: CompilationUnit) =
    new TypeElementVisitorTransformer(unit)
}

class BeanDefinitionInjectPluginComponent(val global: Global) extends PluginComponent with TypingTransformers {
  import global._

  override val phaseName = "compiler-plugin-inject-transform"
  override val runsAfter = List("compiler-plugin-bean-definition")

  override def newPhase(prev: Phase) =
    new StdPhase(prev) {
      override def apply(unit: CompilationUnit) {
        unit.body = new BeanDefinitionInjectTransformer(unit).transform(unit.body)
      }
    }

  class BeanDefinitionInjectTransformer(unit: CompilationUnit) extends TypingTransformer(unit) {
    override def transform(tree: Tree) = tree match {
      case classDef:ClassDef => {
        if (classDef.symbol.annotations.nonEmpty) {
          if (!classDef.symbol.isAbstractType) {
            val visitorContext = new ScalaVisitorContext(global, unit.source)
            val concreteClassMetadata = Globals.metadataBuilder.build(classDef.symbol)

            val constructor = concreteConstructorFor(classDef)

            val constructorParameterInfo = ExecutableElementParamInfo.populateParameterData(constructor)

            val annotationMetadata = new AnnotationMetadataHierarchy(concreteClassMetadata)

            val beanDefinitionWriter = new BeanDefinitionWriter(
              classDef.symbol.enclosingPackageClass.fullName,
              classDef.name.toString,
              new ScalaClassElement(classDef.symbol, annotationMetadata, visitorContext),
              annotationMetadata
            )
            val beanDefinitionReferenceWriter = new BeanDefinitionReferenceWriter(
              beanDefinitionWriter.getBeanTypeName,
              beanDefinitionWriter.getBeanDefinitionName,
              beanDefinitionWriter.getOriginatingElement,
              beanDefinitionWriter.getAnnotationMetadata
            )

            beanDefinitionWriter.visitBeanDefinitionConstructor(
              beanDefinitionWriter.getAnnotationMetadata,
              false,
              constructorParameterInfo.parameters,
              constructorParameterInfo.annotationMetadata,
              constructorParameterInfo.genericTypes
            )
            beanDefinitionWriter.visitBeanDefinitionEnd()
            beanDefinitionWriter.accept(visitorContext)

            beanDefinitionReferenceWriter.accept(visitorContext)

            Globals.beanDefinitionWriters += classDef.symbol.fullName -> beanDefinitionWriter
            Globals.beanDefinitionReferenceWriters += classDef.symbol.fullName -> beanDefinitionReferenceWriter
          }
        }
        super.transform(tree)
        }
      case _ => super.transform(tree)
    }
  }

  def newTransformer(unit: CompilationUnit) = new BeanDefinitionInjectTransformer(unit)

  def concreteConstructorFor(classDef:ClassDef): Option[DefDef] = {
    val constructors = classDef.impl.body.filter(_.symbol.isConstructor)

    constructors.headOption.map(_.asInstanceOf[DefDef])
  }
}

class FinalizePluginComponent(val global: Global) extends PluginComponent with TypingTransformers {
  import global._

  override val phaseName = "compiler-plugin-type-element-end"
  override val runsAfter = List("compiler-plugin-inject-transform")

  override def newPhase(prev: Phase) = {
    new StdPhase(prev) {
      override def apply(unit: CompilationUnit) {
        val values = new util.ArrayList[LoadedVisitor]()
        values.addAll(Globals.loadedVisitors.values.asJavaCollection)
        OrderUtil.reverseSort(values)
        for (loadedVisitor <- asScalaIterator(values.iterator())) {
          loadedVisitor.finish()
        }
        unit.body = new TypeElementEndTransformer(unit).transform(unit.body)
      }
    }
  }

  class TypeElementEndTransformer(unit: CompilationUnit) extends TypingTransformer(unit) {
    override def transform(tree: Tree) = tree match {
      case classDef:ClassDef => {
        super.transform(tree)
      }
      case _ => super.transform(tree)
    }
  }

  def newTransformer(unit: CompilationUnit) = new TypeElementEndTransformer(unit)
}






