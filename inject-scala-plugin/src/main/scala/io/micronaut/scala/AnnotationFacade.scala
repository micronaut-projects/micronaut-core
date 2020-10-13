package io.micronaut.scala

import scala.tools.nsc.Global

abstract sealed class AnnotationFacade

case class ScalaAnnotationFacade(ai:Global#AnnotationInfo) extends AnnotationFacade
case class JavaAnnotationFacade(cls:Class[_], elementValues:Map[String, Any]) extends AnnotationFacade
