package io.micronaut.scala

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.VisitorContext

import scala.tools.nsc.Global

/*
Class exists to provided ClassElement implements to pass to object in the micronaut-core project
 */

class ScalaClassElement(
  symbol: Global#ClassSymbol,
  annotationMetadata: AnnotationMetadata,
  visitorContext: VisitorContext
) extends io.micronaut.inject.ast.ClassElement {
  /**
   * @return The name of the element.
   */
  override def getName: String = symbol.nameString

  /**
   * @return True if the element is protected.
   */
  override def isProtected: Boolean = symbol.isProtected

  /**
   * @return True if the element is public.
   */
  override def isPublic: Boolean = symbol.isPublic

  /**
   * Returns the native underlying type. This API is extended by all of the inject language implementations.
   * The object returned by this method will be the language native type the information is being retrieved from.
   *
   * @return The native type
   */
  override def getNativeType: AnyRef = symbol

  /**
   * Tests whether one type is assignable to another.
   *
   * @param tpe The type to check
   * @return {@code true} if and only if the this type is assignable to the second
   */
  override def isAssignable(tpe: String): Boolean = ???

  /**
   * Convert the class element to an element for the same type, but representing an array.
   * Do not mutate the existing instance. Create a new instance instead.
   *
   * @return A new class element
   */
  override def toArray: ClassElement = ???
}
