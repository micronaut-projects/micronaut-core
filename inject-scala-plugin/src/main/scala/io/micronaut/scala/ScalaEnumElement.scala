package io.micronaut.scala

import java.util

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.EnumElement
import io.micronaut.inject.visitor.VisitorContext

import scala.tools.nsc.Global

class ScalaEnumElement (
  typeElement: Global#ClassSymbol,
  annotationMetadata: AnnotationMetadata,
  visitorContext: VisitorContext
 ) extends ScalaClassElement(typeElement, annotationMetadata, visitorContext) with EnumElement {
  /**
   * The values that make up this enum.
   *
   * @return The values
   */
  override def values(): util.List[String] = ???

}
