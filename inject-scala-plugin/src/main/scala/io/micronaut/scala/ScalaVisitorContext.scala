package io.micronaut.scala

import java.io.OutputStream
import java.util
import java.util.Optional

import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.convert.value.MutableConvertibleValues
import io.micronaut.inject.ast.Element
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.inject.writer.{DirectoryClassWriterOutputVisitor, GeneratedFile}

import scala.reflect.internal.util.SourceFile
import scala.tools.nsc.{Global, Settings}

class ScalaVisitorContext(val global: Global, val source: SourceFile) extends VisitorContext {

  override def info(message: String, element: Element):Unit = ()

  override def info(message: String):Unit = ()

  override def fail(message: String, element: Element):Unit = ()

  override def warn(message: String, element: Element):Unit = ()

  override def visitMetaInfFile(path: String): Optional[GeneratedFile] = {
    val classesDir = global.settings.outputDirs.outputDirFor(source.file).file
    if (classesDir != null) {
      val outputVisitor = new DirectoryClassWriterOutputVisitor(classesDir)
      outputVisitor.visitMetaInfFile(path)
    } else {
      Optional.empty[GeneratedFile]()
    }
  }

  override def visitGeneratedFile(path: String): Optional[GeneratedFile] = {
    val classesDir = global.settings.outputDirs.outputDirFor(source.file).file
    if (classesDir != null) {
      val outputVisitor = new DirectoryClassWriterOutputVisitor(classesDir)
      outputVisitor.visitGeneratedFile(path)
    } else {
      Optional.empty[GeneratedFile]()
    }
  }

  override def names(): util.Set[String] = Globals.visitorAttributes.names()

  override def values(): util.Collection[AnyRef] = Globals.visitorAttributes.values()

  override def get[T](name: CharSequence, conversionContext: ArgumentConversionContext[T]): Optional[T] = {
    Globals.visitorAttributes.get(name, conversionContext)
  }

  override def visitClass(classname: String, originatingElement: Element): OutputStream = {
    val classesDir = global.settings.outputDirs.outputDirFor(source.file).file
    if (classesDir != null) {
      val outputVisitor = new DirectoryClassWriterOutputVisitor(classesDir)
      outputVisitor.visitClass(classname, originatingElement)
    } else {
      null
    }
  }

  override def visitServiceDescriptor(`type`: String, classname: String): Unit = {
    val classesDir = global.settings.outputDirs.outputDirFor(source.file).file
    if (classesDir != null) {
      val outputVisitor = new DirectoryClassWriterOutputVisitor(classesDir)
      outputVisitor.visitServiceDescriptor(`type`, classname)
      outputVisitor.finish()
    }
  }

  override def finish(): Unit = {

  }

  override def put(key: CharSequence, value: scala.AnyRef): MutableConvertibleValues[AnyRef] = {
    Globals.visitorAttributes.put(key, value)
  }

  override def remove(key: CharSequence): MutableConvertibleValues[AnyRef] = {
    Globals.visitorAttributes.remove(key)
  }

  override def clear(): MutableConvertibleValues[AnyRef] = {
    Globals.visitorAttributes.clear()
  }
}
