package io.micronaut.scala

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.inject.writer.{BeanDefinitionReferenceWriter, BeanDefinitionVisitor}

import scala.collection.mutable
import scala.tools.nsc.plugins._
import scala.tools.nsc.{Global, Phase}

class CompilerPlugin(override val global: Global)
  extends Plugin {
  override val name = "compiler-plugin"
  override val description = "Compiler plugin"
  override val components =
    List(
      new InitPluginComponent(global),
      new BeanDefinitionInjectPluginComponent(global),
    )
}

class InitPluginComponent(val global: Global) extends PluginComponent {
  import global._
  override val phaseName = "init-plugin"
  override val runsAfter = List("explicitouter")
  override def newPhase(prev: Phase) =
    new StdPhase(prev) {
      override def apply(unit: CompilationUnit):Unit = {
        new InitTraverser(unit).traverse(unit.body)
      }
    }

  class InitTraverser(unit: CompilationUnit) extends Traverser {
    override def traverse(tree: Tree) = {
      tree match {
        case classDef:ClassDef => {
          processSymbolAnnotations(classDef.symbol, classDef.symbol)
          buildMethodSymbolBridgeOverride(classDef)
        }
        case defDef:DefDef => processSymbolAnnotations(defDef.symbol, defDef.symbol.enclClass)
        case valDef:ValDef => processSymbolAnnotations(valDef.symbol, valDef.symbol.enclClass)
        case _ => ()
      }
      super.traverse(tree)
    }
  }

  /*
  This is needed because after the explicit-outer phase, overridden methods that require a bridge
  method (extends a method with a generic) are no longer returned in the Global#MethodSymbol.overrides method
  This could be eliminated if this can be moved sooner, but that broke thing last time I tried.
   */
  def buildMethodSymbolBridgeOverride(classDef:Global#ClassDef): Unit = {
    classDef.impl.body.foreach {
      case defDef:Global#DefDef if defDef.symbol.isBridge && defDef.symbol.overrides.nonEmpty => {
        defDef.children.foreach {
          case treeApply: Global#Apply => Globals.methodsToBridgeOverrides.addOne((treeApply.symbol, defDef.symbol.overrides))
          case _ => ()
        }
      }
      case _ => ()
    }
  }

  /*
  This determine whether a class should processed in a later phases by looking at the class, methods and fields
  within to see if micronaut type stuff is being done.
   */
  def processSymbolAnnotations(symbol:Global#Symbol, owner:Global#Symbol) = {
    if (symbol.annotations.exists(annotationInfo => {
      val annotationName = annotationInfo.symbol.thisType.toString
        hasStereotype(annotationInfo.symbol, Globals.ANNOTATION_STEREOTYPES) ||
        AbstractAnnotationMetadataBuilder.isAnnotationMapped(annotationName)
    })) {
        Globals.beanableSymbols += owner
    }
  }

  protected def hasStereotype(element: Global#Symbol, stereotypes: Array[String]): Boolean = {
    if (stereotypes.contains(element.thisType.toString)) {
      true
    } else {
      try {
        val annotationMetadata: AnnotationMetadata = Globals.metadataBuilder(global).getOrCreate(element)
        stereotypes.exists(annotationMetadata.hasStereotype)
      } catch {
        case _:ClassNotFoundException => false
        case other:Exception => throw other
      }
    }
  }
}

class BeanDefinitionInjectPluginComponent(val global: Global) extends PluginComponent {
  import global._

  override val phaseName = "bean-definition-inject"
  override val runsAfter = List("init-plugin")

  override def newPhase(prev: Phase) =
    new StdPhase(prev) {
      override def apply(unit: CompilationUnit):Unit = {
        new BeanDefinitionInjectTraverser(unit).traverse(unit.body)
      }
    }

  class BeanDefinitionInjectTraverser(unit:CompilationUnit) extends Traverser {
    val processed = new mutable.HashSet[Global#Symbol]

    private def processBeanDefinitions(beanDefinitionWriter: BeanDefinitionVisitor, visitorContext: VisitorContext): Unit = {
        beanDefinitionWriter.visitBeanDefinitionEnd()
        beanDefinitionWriter.accept(visitorContext)
        val beanDefinitionName = beanDefinitionWriter.getBeanDefinitionName
        val beanTypeName = beanDefinitionWriter.getBeanTypeName
        val annotationMetadata = beanDefinitionWriter.getAnnotationMetadata
        val beanDefinitionReferenceWriter = new BeanDefinitionReferenceWriter(beanTypeName, beanDefinitionName, beanDefinitionWriter.getOriginatingElement, annotationMetadata)
        beanDefinitionReferenceWriter.setRequiresMethodProcessing(beanDefinitionWriter.requiresMethodProcessing)
//        beanDefinitionReferenceWriter.setContextScope(annotationUtils.hasStereotype(beanClassElement, classOf[Context]))
        beanDefinitionReferenceWriter.accept(visitorContext)
    }

    override def traverse(tree: Tree) = tree match {
      case classDef:ClassDef => {
        if (Globals.beanableSymbols.contains(classDef.symbol)) {
          val visitorContext = new ScalaVisitorContext(global, unit.source)
          val visitor = new AnnBeanElementVisitor(global, classDef, visitorContext)
          visitor.visit()
          visitor.beanDefinitionWriters.iterator.foreach { tuple =>
            if (!processed.contains(tuple._1)) {
              processed.add(tuple._1)
              processBeanDefinitions(tuple._2, visitorContext)
            }
          }
        }
        super.traverse(tree)
      }
      case _ => super.traverse(tree)
    }
  }
}







