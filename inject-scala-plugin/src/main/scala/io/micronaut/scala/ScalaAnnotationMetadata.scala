package io.micronaut.scala

import java.util.List

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.annotation.DefaultAnnotationMetadata

import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.collection.mutable
import scala.tools.nsc.Global

class ScalaAnnotationMetadata(val annotations: List[Global#AnnotationInfo] ) extends DefaultAnnotationMetadata {
  override def hasAnnotation(annotation: String): Boolean = annotations.exists(x => annotation.equals(x.atp.toLongString))

  override def hasDeclaredAnnotation(annotation: String): Boolean = annotations.exists(x => annotation.equals(x.atp.toLongString))

}

object ScalaAnnotationMetadata {
  val cache:mutable.Map[String, AnnotationMetadata] = new mutable.HashMap[String, AnnotationMetadata]()

  def get(name: String): AnnotationMetadata = {
    cache.getOrElseUpdate(name, AnnotationMetadata.EMPTY_METADATA)
  }
}
