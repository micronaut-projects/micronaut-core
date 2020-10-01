package io.micronaut.scala

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.{ClassElement, FieldElement}
import io.micronaut.inject.visitor.VisitorContext

import scala.tools.nsc.Global

class ScalaFieldElement(
  fieldElement: Global#TermSymbol,
  annotationMetadata: AnnotationMetadata,
  visitorContext: VisitorContext
) extends FieldElement {
  /**
   * @return The type of the element
   */
  override def getType: ClassElement = ???

  /**
   * @return The declaring type of the element.
   */
  override def getDeclaringType: ClassElement = ???

  /**
   * @return The name of the element.
   */
  override def getName: String = ???

  /**
   * @return True if the element is protected.
   */
  override def isProtected: Boolean = ???

  /**
   * @return True if the element is public.
   */
  override def isPublic: Boolean = ???

  /**
   * Returns the native underlying type. This API is extended by all of the inject language implementations.
   * The object returned by this method will be the language native type the information is being retrieved from.
   *
   * @return The native type
   */
  override def getNativeType: AnyRef = ???
}
