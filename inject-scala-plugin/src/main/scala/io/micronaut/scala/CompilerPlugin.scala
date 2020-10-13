package io.micronaut.scala

import java.util

import io.micronaut.core.io.service.SoftServiceLoader
import io.micronaut.core.order.OrderUtil
import io.micronaut.inject.visitor.{TypeElementVisitor, VisitorContext}
import io.micronaut.inject.writer.{BeanDefinitionReferenceWriter, BeanDefinitionVisitor}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.tools.nsc.plugins._
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

class InitPluginComponent(val global: Global) extends PluginComponent {
  import global._
  override val phaseName = "compiler-plugin-type-element-init"
  override val runsAfter = List("jvm")
  override def newPhase(prev: Phase) =
    new StdPhase(prev) {
      override def apply(unit: CompilationUnit) {
        new InitTraverser(unit).traverse(unit.body)
      }
    }

  class InitTraverser(unit: CompilationUnit) extends Traverser {
    override def traverse(tree: Tree) = tree match {
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
        super.traverse(tree)
      }
      case _ => super.traverse(tree)
    }
  }
}

class TypeElementVisitorPluginComponent(val global: Global) extends PluginComponent {
  import global._
  override val phaseName = "compiler-plugin-type-element-visitor"
  override val runsAfter = List("compiler-plugin-type-element-init")
  override def newPhase(prev: Phase) =
    new StdPhase(prev) {
      override def apply(unit: CompilationUnit) {
        new TypeElementVisitorTraverser().traverse(unit.body)
      }
    }

  class TypeElementVisitorTraverser extends Traverser {

    override def traverse(tree: Tree) = tree match {
      case classDef: ClassDef => {
        Globals.loadedVisitors.values./*filter(v.matches)*/foreach{ loadedVisitor =>
          new ScalaElementVisitor(classDef.symbol, List(loadedVisitor)).visitType(classDef.symbol.asInstanceOf[Global#ClassSymbol], null)
        }
        super.traverse(tree)
      }
      case _ => super.traverse(tree)
    }
  }
}

class BeanDefinitionInjectPluginComponent(val global: Global) extends PluginComponent {
  import global._

  override val phaseName = "compiler-plugin-bean-definition-inject"
  override val runsAfter = List("compiler-plugin-type-element-visitor")

  override def newPhase(prev: Phase) =
    new StdPhase(prev) {
      override def apply(unit: CompilationUnit) {
        new BeanDefinitionInjectTraverser(unit).traverse(unit.body)
      }
    }

  class BeanDefinitionInjectTraverser(unit:CompilationUnit) extends Traverser {
    val processed = new mutable.HashSet[ElementFacade]

    private def processBeanDefinitions(beanClassElement: Global#ClassDef, beanDefinitionWriter: BeanDefinitionVisitor, visitorContext: VisitorContext): Unit = {
        beanDefinitionWriter.visitBeanDefinitionEnd()
        beanDefinitionWriter.accept(visitorContext)
        val beanDefinitionName = beanDefinitionWriter.getBeanDefinitionName
        var beanTypeName = beanDefinitionWriter.getBeanTypeName
//        val interfaces = beanClassElement.impl.ingetInterfaces
//        for (anInterface <- interfaces) {
//          if (anInterface.isInstanceOf[DeclaredType]) {
//            val declaredType = anInterface.asInstanceOf[DeclaredType]
//            val element = declaredType.asElement
//            if (element.isInstanceOf[TypeElement]) {
//              val te = element.asInstanceOf[TypeElement]
//              val name = te.getQualifiedName.toString
//              if (classOf[Provider[_]].getName == name) {
//                val typeArguments = declaredType.getTypeArguments
//                if (!typeArguments.isEmpty) beanTypeName = genericUtils.resolveTypeReference(typeArguments.get(0)).toString
//              }
//            }
//          }
//        }
          val annotationMetadata = beanDefinitionWriter.getAnnotationMetadata
        val beanDefinitionReferenceWriter = new BeanDefinitionReferenceWriter(beanTypeName, beanDefinitionName, beanDefinitionWriter.getOriginatingElement, annotationMetadata)
        beanDefinitionReferenceWriter.setRequiresMethodProcessing(beanDefinitionWriter.requiresMethodProcessing)
        val className = beanDefinitionReferenceWriter.getBeanDefinitionQualifiedClassName
//        beanDefinitionReferenceWriter.setContextScope(annotationUtils.hasStereotype(beanClassElement, classOf[Context]))
        beanDefinitionReferenceWriter.accept(visitorContext)
    }

    override def traverse(tree: Tree) = tree match {
      case classDef:ClassDef => {
        val visitorContext = new ScalaVisitorContext(global, unit.source)
        val visitor = new AnnBeanElementVisitor(classDef, visitorContext)
          visitor.visit()
          visitor.beanDefinitionWriters.iterator.foreach{tuple =>
            if (!processed.contains(tuple._1)) {
              processed.add(tuple._1)
              processBeanDefinitions(classDef, tuple._2, visitorContext)
            }
          }
          super.traverse(tree)
      }
      case _ => super.traverse(tree)
    }
  }
}

class FinalizePluginComponent(val global: Global) extends PluginComponent {
  import global._

  override val phaseName = "compiler-plugin-type-element-end"
  override val runsAfter = List("compiler-plugin-bean-definition-inject")

  override def newPhase(prev: Phase) = {
    new StdPhase(prev) {
      override def apply(unit: CompilationUnit) {
        val values = new util.ArrayList[LoadedVisitor]()
        values.addAll(Globals.loadedVisitors.values.asJavaCollection)
        OrderUtil.reverseSort(values)
        for (loadedVisitor <- asScalaIterator(values.iterator())) {
          loadedVisitor.finish()
        }
      }
    }
  }
}






