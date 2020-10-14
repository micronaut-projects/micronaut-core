
import java.io.FileInputStream

import io.micronaut.core.naming.NameUtils

import scala.reflect.internal.util.BatchSourceFile
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.{Global, Settings}

package io.micronaut.inject {

  import scala.reflect.io.VirtualDirectory

  abstract class AbstractCompilerTest {

    def buildBeanDefinition(className: String, cls: String): BeanDefinition[_] = {
      val beanDefName = NameUtils.getSimpleName(className) + "Definition"
      val packageName = NameUtils.getPackageName(className)
      val beanFullName = packageName + "." + beanDefName

      val classLoader = buildClassLoader(className, cls)
      classLoader.loadClass(beanFullName).getDeclaredConstructors() foreach { ctor => {
        if (ctor.getParameterCount == 0) {
          ctor.setAccessible(true)
          return ctor.newInstance().asInstanceOf[BeanDefinition[_]]
        }
      }
      }
      null
    }

    def buildClassLoader(className: String, code: String): ClassLoader = {
      val sources = List(new BatchSourceFile("<test>", code))
      println("sources " + sources)
      val settings = new Settings
      settings.usejavacp.value = true

      val outputDir = AbstractFile.getDirectory("build/generated")
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
          val generated = new FileInputStream(outputDir.file + "/" + name.replace('.', '/') + ".class")
          if (generated != null) {
            val bytes = generated.readAllBytes()
            return defineClass(name, bytes, 0, bytes.length)
          }
          super.findClass(name)
        }
      }
    }
  }
}
