package io.micronaut.scala

import java.lang.annotation.{Repeatable, RetentionPolicy}
import java.util
import java.util.{ArrayList, Collections, HashMap, List, Map, Optional}

import io.micronaut.annotation.processing.JavaAnnotationMetadataBuilder
import io.micronaut.core.value.OptionalValues
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder
import io.micronaut.inject.processing.JavaModelUtils
import io.micronaut.inject.visitor.VisitorContext
import javax.lang.model.`type`.{DeclaredType, TypeMirror}
import javax.lang.model.element.{AnnotationValue, Element, ElementKind, ExecutableElement, TypeElement, VariableElement}

import scala.reflect.runtime.universe._
import scala.collection.JavaConverters._
import scala.tools.nsc.Global

object ScalaAnnotationMetadataBuilder {
  val ANNOTATION_DEFAULTS = new util.HashMap[String, util.Map[Global#Symbol, AnnotationValue]]
}

class ScalaAnnotationMetadataBuilder extends AbstractAnnotationMetadataBuilder[Global#Symbol, Global#AnnotationInfo] {
  /**
   * Whether the element is a field, method, class or constructor.
   *
   * @param element The element
   * @return True if it is
   */
  override protected def isMethodOrClassElement(element: Global#Symbol): Boolean = element match {
    case _:Global#ClassSymbol | _:Global#MethodSymbol => true
    case _ => false
  }

  /**
   * Obtains the declaring type for an element.
   *
   * @param element The element
   * @return The declaring type
   */
  override protected def getDeclaringType(element: Global#Symbol): String = element match {
    case classSymbol:Global#ClassSymbol => classSymbol.name.toString
    case _ => element.owner.toString
  }

  /**
   * Get the type of the given annotation.
   *
   * @param annotationMirror The annotation
   * @return The type
   */
  override protected def getTypeForAnnotation(annotationMirror: Global#AnnotationInfo): Global#Symbol =
    annotationMirror.atp.typeSymbol

  /**
   * Checks whether an annotation is present.
   *
   * @param element    The element
   * @param annotation The annotation type
   * @return True if the annotation is present
   */
  override protected def hasAnnotation(element: Global#Symbol, annotation: Class[_ <: java.lang.annotation.Annotation]): Boolean =
    element.annotations.exists{ anno => anno.tree.tpe.toString.equals(annotation.getCanonicalName) }

  /**
   * Get the given type of the annotation.
   *
   * @param annotationMirror The annotation
   * @return The type
   */
  override protected def getAnnotationTypeName(annotationMirror: Global#AnnotationInfo): String = annotationMirror.atp.toString

  /**
   * Get the name for the given element.
   *
   * @param element The element
   * @return The name
   */
  override protected def getElementName(element: Global#Symbol): String = element.name.toString

  /**
   * Obtain the annotations for the given type.
   *
   * @param element The type element
   * @return The annotations
   */
  override protected def getAnnotationsForType(element: Global#Symbol): util.List[Global#AnnotationInfo] =
    element.annotations.asJava

  /**
   * Build the type hierarchy for the given element.
   *
   * @param element                The element
   * @param inheritTypeAnnotations Whether to inherit type annotations
   * @param declaredOnly           Whether to only include declared annotations
   * @return The type hierarchy
   */
  override protected def buildHierarchy(element: Global#Symbol, inheritTypeAnnotations: Boolean, declaredOnly: Boolean): util.List[Global#Symbol] = {
    if (declaredOnly) return Collections.singletonList(element)

    element match {
      case tipe:Global#TypeSymbol => {
        val hierarchy = new util.ArrayList[Global#Symbol]
        hierarchy.add(element)
        populateTypeHierarchy(element, hierarchy)
        Collections.reverse(hierarchy)
        hierarchy
      }
      case deff:Global#MethodSymbol => { // we have a method
        // for methods we merge the data from any overridden interface or abstract methods
        // with type level data
        val executableElement = element.asInstanceOf[Global#Symbol]
        // the starting hierarchy is the type and super types of this method
         val hierarchy = if (inheritTypeAnnotations)
               buildHierarchy(executableElement.owner, false, declaredOnly)
          else new util.ArrayList[Global#Symbol]
        if (hasAnnotation(executableElement, classOf[Override])) hierarchy.addAll(findOverriddenMethods(executableElement))
        hierarchy.add(element)
        hierarchy
      }
    case varSym:Global#TermSymbol => {
      val hierarchy = new util.ArrayList[Global#Symbol]
      val enclosingElement = varSym.owner
      enclosingElement match {
        case executableElement: Global#MethodSymbol =>
          if (hasAnnotation(executableElement, classOf[Override])) {
//            val variableIdx = executableElement.getParameters.indexOf(variable)
//            for (overridden <- findOverriddenMethods(executableElement)) {
//              hierarchy.add(overridden.getParameters.get(variableIdx))
//            }
          }
        case _ =>
      }
      hierarchy.add(varSym)
      hierarchy
    }
    case _ => {
      val single = new util.ArrayList[Global#Symbol]
      single.add(element)
      single
    }
  }
  }

  private def populateTypeHierarchy(element: Global#Symbol, hierarchy: util.List[Global#Symbol]): Unit = {
    val isInterface = element.isInterface
    if (isInterface) {
      val typeElement = element.asInstanceOf[Global#Symbol]
//      val interfaces = typeElement.getInterfaces
//      for (anInterface <- interfaces) {
//        if (anInterface.isInstanceOf[DeclaredType]) {
//          val interfaceElement = anInterface.asInstanceOf[DeclaredType].asElement
//          hierarchy.add(interfaceElement)
//          populateTypeHierarchy(interfaceElement, hierarchy)
//        }
//      }
    }
//    else while ( {
//      JavaModelUtils.resolveKind(element, ElementKind.CLASS).isPresent
//    }) {
//      val typeElement = element.asInstanceOf[TypeElement]
//      val interfaces = typeElement.getInterfaces
//      import scala.collection.JavaConversions._
//      for (anInterface <- interfaces) {
//        if (anInterface.isInstanceOf[DeclaredType]) {
//          val interfaceElement = anInterface.asInstanceOf[DeclaredType].asElement
//          hierarchy.add(interfaceElement)
//          populateTypeHierarchy(interfaceElement, hierarchy)
//        }
//      }
//      val superMirror = typeElement.getSuperclass
//      if (superMirror.isInstanceOf[DeclaredType]) {
//        val `type` = superMirror.asInstanceOf[DeclaredType]
//        if (`type`.toString == classOf[Any].getName) break //todo: break is not supported
//        else {
//          element = `type`.asElement
//          hierarchy.add(element)
//        }
//      }
//      else break //todo: break is not supported
//    }
  }

  private def findOverriddenMethods(executableElement: Global#Symbol) = {
    val overridden = new util.ArrayList[Global#Symbol]
    val enclosingElement = executableElement.owner
    if (enclosingElement.isInstanceOf[Global#TypeSymbol]) {
//      var supertype = enclosingElement.asInstanceOf[TypeElement]
//      while ( {
//        supertype != null && !(supertype.toString == classOf[Any].getName)
//      }) {
//        val result = findOverridden(executableElement, supertype)
//        if (result.isPresent) {
//          val overriddenMethod = result.get
//          overridden.add(overriddenMethod)
//        }
//        findOverriddenInterfaceMethod(executableElement, overridden, supertype)
//        val superclass = supertype.getSuperclass
//        if (superclass.isInstanceOf[DeclaredType]) supertype = superclass.asInstanceOf[DeclaredType].asElement.asInstanceOf[TypeElement]
//        else break //todo: break is not supported
//      }
    }
    overridden
  }

  /**
   * Read the given member and value, applying conversions if necessary, and place the data in the given map.
   *
   * @param originatingElement The originating element
   * @param annotationName     The annotation name
   * @param member             The member being read from
   * @param memberName         The member
   * @param annotationValue    The value
   * @param annotationValues   The values to populate
   */
  override protected def readAnnotationRawValues(originatingElement: Global#Symbol, annotationName: String, member: Global#Symbol, memberName: String, annotationValue: Any, annotationValues: util.Map[CharSequence, AnyRef]): Unit = ???

  /**
   * Adds an error.
   *
   * @param originatingElement The originating element
   * @param error              The error
   */
  override protected def addError(originatingElement: Global#Symbol, error: String): Unit = ???

  /**
   * Read the given member and value, applying conversions if necessary, and place the data in the given map.
   *
   * @param originatingElement The originating element
   * @param member             The member
   * @param memberName         The member name
   * @param annotationValue    The value
   * @return The object
   */
  override protected def readAnnotationValue(originatingElement: Global#Symbol, member: Global#Symbol, memberName: String, annotationValue: Any): AnyRef = ???

  /**
   * Read the raw default annotation values from the given annotation.
   *
   * @param annotationMirror The annotation
   * @return The values
   */
  override protected def readAnnotationDefaultValues(annotationMirror: Global#AnnotationInfo): util.Map[_ <: Global#Symbol, _] = {
    val annotationTypeName = getAnnotationTypeName(annotationMirror)
    val element = annotationMirror.symbol
    readAnnotationDefaultValues(annotationTypeName, element)
  }


  /**
   * Read the raw default annotation values from the given annotation.
   *
   * @param annotationName annotation name
   * @param element the type
   * @return The values
   */
  override protected def readAnnotationDefaultValues(annotationName: String, element: Global#Symbol): util.Map[_ <: Global#Symbol, _] = {
    val defaults = ScalaAnnotationMetadataBuilder.ANNOTATION_DEFAULTS
    if (element.isInstanceOf[Global#TypeSymbol]) {
      val annotationElement = element.asInstanceOf[Global#TypeSymbol]
      val annotationName = annotationElement.fullName
      if (!defaults.containsKey(annotationName)) {
        val defaultValues = new util.HashMap[Global#Symbol, AnnotationValue]
//        val allMembers = elementUtils.getAllMembers(annotationElement)
//        allMembers.stream.filter((member: Element) => member.getEnclosingElement == annotationElement).filter(classOf[ExecutableElement].isInstance).map(classOf[ExecutableElement].cast).filter(this.isValidDefaultValue).forEach((executableElement: ExecutableElement) => {
//          def foo(executableElement: ExecutableElement) = {
//            val defaultValue = executableElement.getDefaultValue
//            defaultValues.put(executableElement, defaultValue)
//          }
//
//          foo(executableElement)
//        })
        defaults.put(annotationName, defaultValues)
      }
    }
    return ScalaAnnotationMetadataBuilder.ANNOTATION_DEFAULTS.get(element)
  }

  /**
   * Read the raw annotation values from the given annotation.
   *
   * @param annotationMirror The annotation
   * @return The values
   */
  override protected def readAnnotationRawValues(annotationMirror: Global#AnnotationInfo): util.Map[_ <: Global#Symbol, _] = {
    // annotationMirror.tree.
    new util.HashMap[Global#Symbol, Any]()
  }

  /**
   * Resolve the annotations values from the given member for the given type.
   *
   * @param originatingElement The originating element
   * @param member             The member
   * @param annotationType     The type
   * @return The values
   */
  override protected def getAnnotationValues(originatingElement: Global#Symbol, member: Global#Symbol, annotationType: Class[_]): OptionalValues[_] = ???

  /**
   * Read the name of an annotation member.
   *
   * @param member The member
   * @return The name
   */
  override protected def getAnnotationMemberName(member: Global#Symbol): String = ???

  /**
   * Obtain the name of the repeatable annotation if the annotation is is one.
   *
   * @param annotationMirror The annotation mirror
   * @return Return the name or null
   */
  override protected def getRepeatableName(annotationMirror: Global#AnnotationInfo): String = {
    val typeElement = annotationMirror.symbol
    getRepeatableNameForType(typeElement)
  }

/**
   * Obtain the name of the repeatable annotation if the annotation is is one.
   *
   * @param annotationType The annotation mirror
   * @return Return the name or null
   */
  override protected def getRepeatableNameForType(annotationType: Global#Symbol): String = {
    val mirrors = annotationType.annotations
    for (mirror <- mirrors) {
      val name = mirror.symbol.nameString
//      if (classOf[Repeatable].getName == name) {
//        val elementValues = mirror.getElementValues
//        import scala.collection.JavaConversions._
//        for (entry <- elementValues.entrySet) {
//          if (entry.getKey.getSimpleName.toString == "value") {
//            val av = entry.getValue
//            val value = av.getValue
//            if (value.isInstanceOf[DeclaredType]) {
//              val element = value.asInstanceOf[DeclaredType].asElement
//              return JavaModelUtils.getClassName(element.asInstanceOf[TypeElement])
//            }
//          }
//        }
//      }
    }
    null
  }

  /**
   * Return a mirror for the given annotation.
   *
   * @param annotationName The annotation name
   * @return An optional mirror
   */
  override protected def getAnnotationMirror(annotationName: String): Optional[Global#Symbol] = ???

  /**
   * Get the annotation member.
   *
   * @param originatingElement The originatig element
   * @param member             The member
   * @return The annotation member
   */
  override protected def getAnnotationMember(originatingElement: Global#Symbol, member: CharSequence): Global#Symbol = ???

  /**
   * Creates the visitor context for this implementation.
   *
   * @return The visitor context
   */
  override protected def createVisitorContext(): VisitorContext = ???

  /**
   * Gets the retention policy for the given annotation.
   *
   * @param annotation The annotation
   * @return The retention policy
   */
  override protected def getRetentionPolicy(annotation: Global#Symbol): RetentionPolicy = {
//    List<AnnotationNode> annotations = annotation.getAnnotations()
//    for(ann in annotations) {
//      if (ann.classNode.name == Retention.name) {
//        def i = ann.members.values().iterator()
//        if (i.hasNext()) {
//          def expr = i.next()
//          if (expr instanceof PropertyExpression) {
//            PropertyExpression pe = (PropertyExpression) expr
//            try {
//              return RetentionPolicy.valueOf(pe.propertyAsString)
//            } catch (e) {
//              // should never happen
//              return RetentionPolicy.RUNTIME
//            }
//          }
//        }
//      }
//    }
    RetentionPolicy.RUNTIME
  }
  
}
