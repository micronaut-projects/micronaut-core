package io.micronaut.scala

import java.util.function.Function

import io.micronaut.context.annotation.{ConfigurationReader, EachProperty}
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.util.StringUtils
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder

import scala.tools.nsc.Global

class ScalaConfigurationMetadataBuilder(global: Global) extends ConfigurationMetadataBuilder[Global#Symbol] {
  /**
   * <p>Build a property path for the given declaring type and property name.</p>
   * <p>
   * <p>For {@link io.micronaut.context.annotation.ConfigurationProperties} that path is a property is
   * established by looking at the value of the {@link io.micronaut.context.annotation.ConfigurationProperties} and
   * then calculating the path based on the io.micronaut.inject.inheritance tree.</p>
   * <p>
   * <p>For example consider the following classes:</p>
   * <p>
   * <pre><code>
   * {@literal @}ConfigurationProperties("parent")
   * public class ParentProperties {
   * String foo;
   * }
   *
   * {@literal @}ConfigurationProperties("child")
   * public class ChildProperties extends ParentProperties {
   * String bar;
   * }
   * </code></pre>
   * <p>
   * <p>The path of the property {@code foo} will be "parent.foo" whilst the path of the property {@code bar} will
   * be "parent.child.bar" factoring in the class hierarchy</p>
   * <p>
   * <p>Inner classes hierarchies are also taken into account</p>
   *
   * @param owningType    The owning type
   * @param declaringType The declaring type
   * @param propertyName  The property name
   * @return The property path
   */
  override protected def buildPropertyPath(owningType: Global#Symbol, declaringType: Global#Symbol, propertyName: String): String = {
     buildTypePath(owningType, declaringType) + '.' + propertyName
  }

  /**
   * Variation of {@link #buildPropertyPath ( Object, Object, String)} for types.
   *
   * @param owningType    The owning type
   * @param declaringType The type
   * @return The type path
   */
  override protected def buildTypePath(owningType: Global#Symbol, declaringType: Global#Symbol): String =
    buildTypePath(owningType, declaringType, getAnnotationMetadata(declaringType))


  /**
   * Convert the given type to a string.
   *
   * @param tpe The type
   * @return The string
   */
  override protected def getTypeString(tpe: Global#Symbol) = tpe.nameString

  private def prependSuperclasses(declaringType: Global#Symbol, path: StringBuilder): Unit = {
    if (declaringType.isInterface) {
      var superInterface = resolveSuperInterface(declaringType)
      var break = false
      while (!break && superInterface.isInstanceOf[Global#ClassSymbol]) {
        val annotationMetadata = Globals.metadataBuilder(global).getOrCreate(superInterface)
        val parentConfig = annotationMetadata.getValue(classOf[ConfigurationReader], classOf[String])
        if (parentConfig.isPresent) {
          val parentPath = pathEvaluationFunctionForMetadata(annotationMetadata).apply(parentConfig.get)
          path.insert(0, parentPath + '.')
          superInterface = resolveSuperInterface(superInterface)
        }
        else break = true;
      }
    }
    else {
      var superclass = declaringType.superClass
      var break = false
      while (!break && superclass.isInstanceOf[Global#ClassSymbol]) {
        val annotationMetadata = Globals.metadataBuilder(global).getOrCreate(superclass)
        val parentConfig = annotationMetadata.getValue(classOf[ConfigurationReader], classOf[String])
        if (parentConfig.isPresent) {
          val parentPath = pathEvaluationFunctionForMetadata(annotationMetadata).apply(parentConfig.get)
          path.insert(0, parentPath + '.')
          superclass = superclass.superClass
        }
        else break = true
      }
    }
  }

  private def resolveSuperInterface(declaringType: Global#Symbol) =
    declaringType.owner.baseClasses
      .filter((tm: Global#Symbol) => tm.isInterface && tm.isInstanceOf[Global#TypeSymbol] && Globals.metadataBuilder(global).getOrCreate(tm).hasStereotype(classOf[ConfigurationReader]))
      .map((dt: Global#Symbol) => dt.asInstanceOf[Global#ClassSymbol])
      .headOption
      .orNull

  override protected def buildTypePath(owningType: Global#Symbol, declaringType: Global#Symbol, annotationMetadata: AnnotationMetadata): String = {
    val initialPath = calculateInitialPath(owningType, annotationMetadata)
    val path = new StringBuilder(initialPath)
    prependSuperclasses(declaringType, path)
//    if (owningType.isConcreteClass getNestingKind eq NestingKind.MEMBER) { // we have an inner class, so prepend inner class
//      val enclosingElement = owningType.getEnclosingElement
//      if (enclosingElement.isInstanceOf[TypeElement]) {
//        var enclosingType = enclosingElement.asInstanceOf[TypeElement]
//        while ( {
//          true
//        }) {
//          val enclosingTypeMetadata = getAnnotationMetadata(enclosingType)
//          val parentConfig = enclosingTypeMetadata.getValue(classOf[ConfigurationReader], classOf[String])
//          if (parentConfig.isPresent) {
//            val parentPath = pathEvaluationFunctionForMetadata(enclosingTypeMetadata).apply(parentConfig.get)
//            path.insert(0, parentPath + '.')
//            prependSuperclasses(enclosingType, path)
//            if (enclosingType.getNestingKind eq NestingKind.MEMBER) {
//              val el = enclosingType.getEnclosingElement
//              if (el.isInstanceOf[TypeElement]) enclosingType = el.asInstanceOf[TypeElement]
//              else break //todo: break is not supported
//            }
//            else break //todo: break is not supported
//          }
//          else break //todo: break is not supported
//        }
//      }
//    }
    path.toString
  }

  /**
   * @param tpe The type
   * @return The annotation metadata for the type
   */
  override protected def getAnnotationMetadata(tpe: Global#Symbol): AnnotationMetadata = Globals.metadataBuilder(global).getOrCreate(tpe)

  private def calculateInitialPath(owningType: Global#Symbol, annotationMetadata: AnnotationMetadata): String = {
    val forType = annotationMetadata.getValue(
      classOf[ConfigurationReader],
      classOf[String])
      // got weird compiler errors mixing java/scala lambdas
      if (forType.isPresent) {
        pathEvaluationFunctionForMetadata(annotationMetadata).apply(forType.get())
      } else {
        val ownerMetadata = getAnnotationMetadata(owningType)
        val forOwner = ownerMetadata.getValue(
            classOf[ConfigurationReader],
            classOf[String])
        if (forOwner.isPresent) {
          pathEvaluationFunctionForMetadata(ownerMetadata).apply(forOwner.get())
        } else {
          pathEvaluationFunctionForMetadata(annotationMetadata).apply("")
        }
      }
  }

  private def pathEvaluationFunctionForMetadata(annotationMetadata: AnnotationMetadata): Function[String, String] = (path: String) => {
    if (annotationMetadata.hasDeclaredAnnotation(classOf[EachProperty])) {
      if (annotationMetadata.booleanValue(classOf[EachProperty], "list").orElse(false)) {
        path + "[*]"
      } else {
        path + ".*"
      }
    } else {
      val prefix = annotationMetadata.getValue(classOf[ConfigurationReader], "prefix", classOf[String]).orElse(null)
      if (StringUtils.isNotEmpty(prefix)) {
        if (StringUtils.isEmpty(path)) {
          prefix
        } else {
          prefix + "." + path
        }
      } else {
        path
      }
    }
  }
}
