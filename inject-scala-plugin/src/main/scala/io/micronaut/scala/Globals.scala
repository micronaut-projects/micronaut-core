package io.micronaut.scala

import java.util.Collections

import io.micronaut.core.convert.value.MutableConvertibleValuesMap
import io.micronaut.core.reflect.ClassUtils
import io.micronaut.inject.processing.ProcessedTypes

import scala.collection.mutable
import scala.tools.nsc.Global

object Globals {
  val AROUND_TYPE = "io.micronaut.aop.Around"
  val INTRODUCTION_TYPE = "io.micronaut.aop.Introduction"
  val ANNOTATION_STEREOTYPES = Array[String](
    ProcessedTypes.POST_CONSTRUCT,
    ProcessedTypes.PRE_DESTROY,
    "javax.inject.Inject",
    "javax.inject.Qualifier",
    "javax.inject.Singleton",
    "io.micronaut.context.annotation.Bean",
    "io.micronaut.context.annotation.Replaces",
    "io.micronaut.context.annotation.Value",
    "io.micronaut.context.annotation.Property",
    "io.micronaut.context.annotation.Executable",
    AROUND_TYPE,
    INTRODUCTION_TYPE)

  val metadataBuilder = new ScalaAnnotationMetadataBuilder()
  val loadedVisitors = new mutable.LinkedHashMap[String, LoadedVisitor]
  val visitorAttributes = new MutableConvertibleValuesMap[AnyRef]
  val beanableSymbols = new mutable.HashSet[Global#Symbol]
}