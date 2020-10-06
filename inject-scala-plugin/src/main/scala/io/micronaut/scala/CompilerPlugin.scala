package io.micronaut.scala

import java.util
import java.util.Collections

import io.micronaut.context.annotation.{Property, Value}
import io.micronaut.core.io.service.SoftServiceLoader
import io.micronaut.core.order.OrderUtil
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.{BeanDefinitionReferenceWriter, BeanDefinitionWriter}
import javax.inject.Inject

import scala.collection.JavaConverters._
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
    override def traverse(tree: Tree) = tree match {
      case classDef:ClassDef => {
        new AnnBeanElementVisitor(classDef, new ScalaVisitorContext(global, unit.source)).visit()
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






