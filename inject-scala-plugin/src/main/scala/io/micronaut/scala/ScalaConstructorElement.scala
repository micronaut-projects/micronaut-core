package io.micronaut.scala

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.{ClassElement, ConstructorElement, ParameterElement}
import io.micronaut.inject.visitor.VisitorContext

import scala.tools.nsc.Global

class ScalaConstructorElement(
  rootClassElement: ClassElement,
  executableElement: Global#MethodSymbol,
  annotationMetadata: AnnotationMetadata,
  visitorContext: VisitorContext
) extends ConstructorElement {
  override def getName: String = executableElement.nameString

  /**
   * @return The return type of the method
   */
  override def getReturnType: ClassElement = rootClassElement

  /**
   * @return The method parameters
   */
  override def getParameters: Array[ParameterElement] = ???

  /**
   * @return The declaring type of the element.
   */
  override def getDeclaringType: ClassElement = ???

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
