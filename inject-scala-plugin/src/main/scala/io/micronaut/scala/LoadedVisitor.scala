package io.micronaut.scala

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.Element
import io.micronaut.inject.visitor.TypeElementVisitor

import scala.tools.nsc.Global

class LoadedVisitor(classSymbol:Global#ClassDef, visitor: TypeElementVisitor[_, _], visitorContext:ScalaVisitorContext) {
  var rootClassElement : ScalaClassElement = _
  var classAnnotation = "java.lang.Object" // TODO
  var elementAnnotation = "java.lang.Object" // TODO

  def start() = visitor.start(visitorContext)

  def finish() = visitor.finish(visitorContext)

  def matches( classSymbol:Global#ClassDef ): Boolean = {
    if (classSymbol.name.toString() == "java.lang.Object") {
      true
    } else {
      val annotationMetadata = Globals.metadataBuilder.build(classSymbol.symbol)
      annotationMetadata.hasStereotype(classAnnotation)
    }
  }

  def matches(annotationMetadata: AnnotationMetadata): Boolean = {
    if (elementAnnotation == "java.lang.Object") {
      true
    } else {
      annotationMetadata.hasStereotype(elementAnnotation)
    }
  }

  def visit(element: Global#Symbol, annotationMetadata: AnnotationMetadata): Element = element match {
    case variable: Global#TermSymbol if !variable.isMethod => {
      val e = new ScalaFieldElement(variable, annotationMetadata, visitorContext)
      visitor.visitField(e, visitorContext)
      e
    }
    case executableElement: Global#MethodSymbol => {
      if (rootClassElement != null) {
        if (executableElement.nameString == "<init>") {
          val e = new ScalaConstructorElement(rootClassElement, executableElement, annotationMetadata, visitorContext)
          visitor.visitConstructor(e, visitorContext)
          e
        }
        else {
          val e = new ScalaMethodElement(rootClassElement, executableElement, annotationMetadata, visitorContext)
          visitor.visitMethod(e, visitorContext)
          e
        }
      } else {
        null
      }
    }
    case typeElement: Global#TypeSymbol => {
      val isEnum = typeElement.isJavaEnum
      if (isEnum) {
        this.rootClassElement = new ScalaEnumElement(typeElement, annotationMetadata, visitorContext)
        visitor.visitClass(rootClassElement, visitorContext)
        rootClassElement
      }
      else {
        this.rootClassElement = new ScalaClassElement(typeElement, annotationMetadata, visitorContext)
        visitor.visitClass(rootClassElement, visitorContext)
        rootClassElement
      }
    }
    case _ => null
  }
}

