package io.micronaut.scala

import java.util

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy
import java.util.{List => JavaList}

import scala.jdk.CollectionConverters._
import scala.tools.nsc.Global

class ScalaElementVisitor(global: Global, concreteClass:Global#Symbol, visitors:List[LoadedVisitor]) {
  private def scan(symbol:Global#Symbol, o:Any): Any = {
    symbol match {
      case methodSymbol:Global#MethodSymbol => visitExecutable(methodSymbol, o)
      case _ => null
    }
  }

  def visitType (classElement: Global#ClassSymbol, o: Any): Any = {
    var typeAnnotationMetadata = Globals.metadataBuilder(global).getOrCreate(classElement)
    for (visitor <- visitors) {
      val resultingElement = visitor.visit(classElement, typeAnnotationMetadata)
      if (resultingElement != null) typeAnnotationMetadata = resultingElement.getAnnotationMetadata
    }

    val enclosingElement = classElement.owner
    // don't process inner class unless this is the visitor for it
    val shouldVisit = !enclosingElement.isClass || concreteClass.nameString == classElement.nameString
    if (shouldVisit) {
      //      if (typeAnnotationMetadata.hasStereotype(classOf[Introduction]) || (typeAnnotationMetadata.hasStereotype(classOf[Introspected]) && modelUtils.isAbstract(classElement))) {
      //        classElement.asType.accept(new PublicAbstractMethodVisitor[AnyRef, AnyRef]((classElement, modelUtils, elementUtils)) {
      //          override protected def accept(`type`: DeclaredType, element: Element, o: Any): Unit = {
      //            if (element.isInstanceOf[ExecutableElement]) thisElementVisitor.visitExecutable(element.asInstanceOf[ExecutableElement], o)
      //          }
      //        }, null)
      //        null
      //} else
//      if (classElement.isJavaEnum) {
//        scan(classElement.getEnclosedElements, o)
//      } //else {
        val elements = enclosedElements(classElement)
        var value:Any = null
        for (element <- elements.asScala) {
          value = scan(element, o)
//          if (element.isInstanceOf[TypeElement]) {
//            val typeElement = element.asInstanceOf[TypeElement]
//            import scala.collection.JavaConversions._
//            for (visitor <- visitors) {
//              if (visitor.matches(typeElement)) value = scan(enclosedElements(typeElement), o)
//            }
//          }
        }
        value
    } else {
      null
    }
  }

  def enclosedElements(classElement: Global#TypeSymbol): JavaList[_ <: Global#Symbol] = {
    val enclosedElements: JavaList[Global#Symbol] = new util.ArrayList[Global#Symbol](classElement.children.asJava)
    var superClass: Global#Symbol = classElement.superClass
    // collect fields and methods, skip overrides
    while (superClass != null && !superClass.isInstanceOf[Global#NoSymbol]) {
      val elements: List[_ <: Global#Symbol] = superClass.children.toList
      for (elt1 <- elements) {
        elt1 match {
          case _:Global#MethodSymbol => checkMethodOverride(enclosedElements, elt1)
          case _:Global#TermSymbol => checkFieldHide(enclosedElements, elt1)
          case _ => ()
        }
      }
      superClass = superClass.superClass
    }
    enclosedElements
  }

   def visitExecutable(executableElement: Global#MethodSymbol, o: Any): Any = {
    var methodAnnotationMetadata: AnnotationMetadata = new AnnotationMetadataHierarchy(
      Globals.metadataBuilder(global).getOrCreate(executableElement.owner),
      Globals.metadataBuilder(global).build(executableElement))
    if (executableElement.nameString == "<init>") {
      for (visitor <- visitors) {
        val resultingElement = visitor.visit(executableElement, methodAnnotationMetadata)
        if (resultingElement != null) methodAnnotationMetadata = resultingElement.getAnnotationMetadata
      }
    }
    else {
      for (visitor <- visitors) {
        if (visitor.matches(methodAnnotationMetadata)) {
          val resultingElement = visitor.visit(executableElement, methodAnnotationMetadata)
          if (resultingElement != null) methodAnnotationMetadata = resultingElement.getAnnotationMetadata
        }
      }
    }
    null
  }

  private def checkFieldHide(enclosedElements: JavaList[Global#Symbol], elt1: Global#Symbol): Unit = {
    var hides = false
//    for (elt2 <- enclosedElements) {
//      if (elt1 == elt2 || !elt2.isInstanceOf[VariableElement]) continue //todo: continue is not supported
//      if (elementUtils.hides(elt2, elt1)) {
//        hides = true
//        break //todo: break is not supported
//
//      }
//    }
    if (!hides) enclosedElements.add(elt1)
  }

  private def checkMethodOverride(enclosedElements: JavaList[Global#Symbol], elt1: Global#Symbol): Unit = {
    var overrides = false
//    for (elt2 <- enclosedElements) {
//      if (elt1 == elt2 || !elt2.isInstanceOf[ExecutableElement]) continue //todo: continue is not supported
//      if (elementUtils.overrides(elt2.asInstanceOf[ExecutableElement], elt1.asInstanceOf[ExecutableElement], modelUtils.classElementFor(elt2))) {
//        overrides = true
//        break //todo: break is not supported
//
//      }
//    }
    if (!overrides) enclosedElements.add(elt1)
  }

  def classElementFor(element: Global#Symbol): Global#ClassSymbol = {
    var currentElement = element
    while (
      currentElement != null && !(currentElement.isInterface || currentElement.isJavaEnum)
    ) {
      currentElement = currentElement.owner
    }
    if (currentElement.isInstanceOf[Global#ClassSymbol]) {
      element.asInstanceOf[Global#ClassSymbol]
    } else {
      null
    }
  }
}
