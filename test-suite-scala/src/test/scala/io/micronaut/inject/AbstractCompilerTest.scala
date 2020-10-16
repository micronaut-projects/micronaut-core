package io.micronaut.inject

import java.io.{File, FileInputStream}

import io.micronaut.context.{ApplicationContext, DefaultApplicationContext}
import io.micronaut.core.io.scan.ClassPathResourceLoader
import io.micronaut.core.naming.NameUtils
import org.assertj.core.api.Assertions.assertThat

import scala.jdk.CollectionConverters._

import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.io.Directory
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.{Global, Settings}

abstract class AbstractCompilerTest {
  private val outputDirPath = "build/generated/spec"

  def deleteGeneratedOutput():Unit = {
    val file = new File(outputDirPath)
    if (file.exists()) {
      assertThat(new Directory(file).deleteRecursively()) //.isTrue
    }
    assertThat(file.mkdir()) //.isTrue
  }

  def buildBeanDefinition(className: String, code: String): BeanDefinition[_] = {
    val beanDefName = NameUtils.getSimpleName(className) + "Definition"
    val packageName = NameUtils.getPackageName(className)
    val beanFullName = packageName + "." + beanDefName

    val classLoader = buildClassLoader(code)
    classLoader.loadClass(beanFullName).getDeclaredConstructors() foreach { ctor => {
      if (ctor.getParameterCount == 0) {
        ctor.setAccessible(true)
        return ctor.newInstance().asInstanceOf[BeanDefinition[_]]
      }
    }
    }
    null
  }

  def buildContext(className: String, code: String):ApplicationContext = {
    val classLoader = buildClassLoader(code)
    new DefaultApplicationContext(ClassPathResourceLoader.defaultLoader(classLoader),"test") {
      override def resolveBeanDefinitionReferences():java.util.List[BeanDefinitionReference[_]] = {
        new Directory(new File(outputDirPath)).deepFiles
          .filter(file => file.name.endsWith("DefinitionClass.class"))
          .map(file =>
            classLoader.loadClass(
              file.toString
                .substring(outputDirPath.length + 1)
                .replaceAll("\\.class$", "")
                .replace('\\', '.')).newInstance())

      }.toList.asJava.asInstanceOf[java.util.List[BeanDefinitionReference[_]]]
    }.start()
  }

  def buildClassLoader(code: String): ClassLoader = {
    val sources = List(new BatchSourceFile("<test>", code))
    println("sources " + sources)
    val settings = new Settings
    settings.usejavacp.value = true

    deleteGeneratedOutput()

    val outputDir = AbstractFile.getDirectory(outputDirPath)
    // TODO - would be nice to make this work, but BeanDefinitionWriter seem to have problems writing to it
    //  val outputDir = new VirtualDirectory("(memory)", None)
    settings.outputDirs.setSingleOutput(outputDir)

    val compiler = new Global(settings, new ConsoleReporter(settings)) {
      override protected def computeInternalPhases(): Unit = {
        super.computeInternalPhases()
        for (phase <- new io.micronaut.scala.CompilerPlugin(this).components)
          phasesSet += phase
      }
    }

    new compiler.Run() compileSources (sources)

    new ClassLoader() {
      override def findClass(name: String): Class[_] = {
        val file = new File(outputDir.file + "/" + name.replace('.', '/') + ".class")
        if (file.exists()) {
          val generated = new FileInputStream(file)
          if (generated != null) {
            val bytes = generated.readAllBytes()
            return defineClass(name, bytes, 0, bytes.length)
          }
        }
        super.findClass(name)
      }
    }
  }
}
