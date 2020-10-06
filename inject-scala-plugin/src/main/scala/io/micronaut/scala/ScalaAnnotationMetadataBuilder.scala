package io.micronaut.scala

import java.lang.annotation.RetentionPolicy
import java.util
import java.util.{Collections, Optional}

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.value.OptionalValues
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder
import io.micronaut.inject.visitor.VisitorContext

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.tools.nsc.Global

object ScalaAnnotationMetadataBuilder {
  val ANNOTATION_DEFAULTS = new util.HashMap[String, util.Map[ScalaElement, Any]]
  val CACHE = new mutable.HashMap[ScalaElement, AnnotationMetadata]()
}

class ScalaAnnotationMetadataBuilder extends AbstractAnnotationMetadataBuilder[ScalaElement, Global#AnnotationInfo] {

  def getOrCreate(element: ScalaElement): AnnotationMetadata =
    ScalaAnnotationMetadataBuilder.CACHE.getOrElseUpdate(element, buildOverridden(element))

  /**
   * Whether the element is a field, method, class or constructor.
   *
   * @param element The element
   * @return True if it is
   */
  override protected def isMethodOrClassElement(element: ScalaElement): Boolean = element match {
    case ScalaSymbolElement(symbol) => symbol match {
      case _: Global#ClassSymbol | _: Global#MethodSymbol => true
      case _ => false
    }
    case _ => false
  }

  /**
   * Obtains the declaring type for an element.
   *
   * @param element The element
   * @return The declaring type
   */
  override protected def getDeclaringType(element: ScalaElement): String =
    element match {
      case ScalaSymbolElement(symbol) => symbol.owner.nameString
      case ScalaNameElement(name) => name.toString
    }

  /**
   * Get the type of the given annotation.
   *
   * @param annotationMirror The annotation
   * @return The type
   */
  override protected def getTypeForAnnotation(annotationMirror: Global#AnnotationInfo): ScalaElement =
    ScalaSymbolElement(annotationMirror.atp.typeSymbol)

  /**
   * Checks whether an annotation is present.
   *
   * @param element    The element
   * @param annotation The annotation type
   * @return True if the annotation is present
   */
  override protected def hasAnnotation(element: ScalaElement, annotation: Class[_ <: java.lang.annotation.Annotation]): Boolean =
    element match {
      case ScalaSymbolElement(symbol) => symbol.annotations.exists{ anno => anno.tree.tpe.toString.equals(annotation.getCanonicalName) }
      case ScalaNameElement(name) => false
    }

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
  override protected def getElementName(element: ScalaElement): String = element match {
    case ScalaSymbolElement(symbol) => symbol.nameString
    case ScalaNameElement(name) => name.toString()
  }

  /**
   * Obtain the annotations for the given type.
   *
   * @param element The type element
   * @return The annotations
   */
  override protected def getAnnotationsForType(element: ScalaElement): util.List[Global#AnnotationInfo] =
    element match {
      case ScalaSymbolElement(symbol) => symbol.annotations.asJava
      case ScalaNameElement(_) => Collections.emptyList()
    }

  /**
   * Build the type hierarchy for the given element.
   *
   * @param element                The element
   * @param inheritTypeAnnotations Whether to inherit type annotations
   * @param declaredOnly           Whether to only include declared annotations
   * @return The type hierarchy
   */
  override protected def buildHierarchy(element: ScalaElement, inheritTypeAnnotations: Boolean, declaredOnly: Boolean): util.List[ScalaElement] = {
    element match {
      case ScalaSymbolElement(symbol) => {
        if (declaredOnly) Collections.singletonList(ScalaSymbolElement(symbol))
        else {
          symbol match {
            case tpe: Global#TypeSymbol => {
              val hierarchy = new util.ArrayList[ScalaElement]
              hierarchy.add(ScalaSymbolElement(symbol))
              populateTypeHierarchy(ScalaSymbolElement(tpe), hierarchy)
              Collections.reverse(hierarchy)
              hierarchy
            }
            case deff: Global#MethodSymbol => { // we have a method
              // for methods we merge the data from any overridden interface or abstract methods
              // with type level data
              // the starting hierarchy is the type and super types of this method
              val hierarchy = if (inheritTypeAnnotations)
                buildHierarchy(ScalaSymbolElement(deff.owner), false, declaredOnly)
              else new util.ArrayList[ScalaElement]
              if (hasAnnotation(ScalaSymbolElement(deff), classOf[Override])) hierarchy.addAll(findOverriddenMethods(ScalaSymbolElement(deff)))
              hierarchy.add(ScalaSymbolElement(symbol))
              hierarchy
            }
            case varSym: Global#TermSymbol => {
              val hierarchy = new util.ArrayList[ScalaElement]
              val enclosingElement = varSym.owner
              enclosingElement match {
                case executableElement: Global#MethodSymbol =>
                  if (hasAnnotation(ScalaSymbolElement(executableElement), classOf[Override])) {
                    //            val variableIdx = executableElement.getParameters.indexOf(variable)
                    //            for (overridden <- findOverriddenMethods(executableElement)) {
                    //              hierarchy.add(overridden.getParameters.get(variableIdx))
                    //            }
                  }
                case _ =>
              }
              hierarchy.add(ScalaSymbolElement(varSym))
              hierarchy
            }
            case _ => {
              val single = new util.ArrayList[ScalaElement]
              single.add(ScalaSymbolElement(symbol))
              single
            }
          }
        }
      }
      case ScalaNameElement(_) => new util.ArrayList[ScalaElement]
    }
  }

  private def populateTypeHierarchy(element:ScalaElement, hierarchy: util.List[ScalaElement]): Unit = {
    element match {
      case ScalaSymbolElement(element) => {
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
      case ScalaNameElement(_) => ()
    }
  }

  private def findOverriddenMethods(executableElement: ScalaElement) = {
    val overridden = new util.ArrayList[ScalaElement]
    executableElement match {
      case ScalaSymbolElement(symbol) => symbol.owner match {
        case supertype:Global#TypeSymbol =>
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
      case _ => ()
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
  override protected def readAnnotationRawValues(originatingElement: ScalaElement, annotationName: String, member: ScalaElement, memberName: String, annotationValue: Any, annotationValues: util.Map[CharSequence, AnyRef]): Unit = {
//    if (memberName != null && annotationValue.isInstanceOf[AnnotationValue] && !annotationValues.containsKey(memberName)) {
//      val resolver = new JavaAnnotationMetadataBuilder#MetadataAnnotationValueVisitor(originatingElement)
//      annotationValue.asInstanceOf[AnnotationValue].accept(resolver, this)
      val resolvedValue = annotationValue.toString
      if (resolvedValue != null) {
        validateAnnotationValue(originatingElement, annotationName, member, memberName, resolvedValue)
        annotationValues.put(memberName, resolvedValue)
      }
//    }
  }

  /**
   * Adds an error.
   *
   * @param originatingElement The originating element
   * @param error              The error
   */
  override protected def addError(originatingElement: ScalaElement, error: String): Unit = ???

  /**
   * Read the given member and value, applying conversions if necessary, and place the data in the given map.
   *
   * @param originatingElement The originating element
   * @param member             The member
   * @param memberName         The member name
   * @param annotationValue    The value
   * @return The object
   */
  override protected def readAnnotationValue(originatingElement: ScalaElement, member: ScalaElement, memberName: String, annotationValue: Any): AnyRef = ???

  /**
   * Read the raw default annotation values from the given annotation.
   *
   * @param annotationMirror The annotation
   * @return The values
   */
  override protected def readAnnotationDefaultValues(annotationMirror: Global#AnnotationInfo): util.Map[_ <: ScalaElement, _] = {
    val annotationTypeName = getAnnotationTypeName(annotationMirror)
    val element = ScalaSymbolElement(annotationMirror.symbol)
    readAnnotationDefaultValues(annotationTypeName, element)
  }

  /**
   * Read the raw default annotation values from the given annotation.
   *
   * @param annotationName annotation name
   * @param element the type
   * @return The values
   */
  override protected def readAnnotationDefaultValues(annotationName: String, element: ScalaElement): util.Map[_ <: ScalaElement, _] = {
    val defaults = ScalaAnnotationMetadataBuilder.ANNOTATION_DEFAULTS
    element match {
      case ScalaSymbolElement(symbol) => symbol match {
        case annotationElement: Global#TypeSymbol =>
          val annotationName = annotationElement.fullName
          if (!defaults.containsKey(annotationName)) {
            val defaultValues = new util.HashMap[ScalaElement, Any]
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
          ScalaAnnotationMetadataBuilder.ANNOTATION_DEFAULTS.get(element)
      }
      case ScalaNameElement(_) =>  Collections.emptyMap()
    }
    Collections.emptyMap()
  }

  /**
   * Read the raw annotation values from the given annotation.
   *
   * @param annotationMirror The annotation
   * @return The values
   */
  override protected def readAnnotationRawValues(annotationMirror: Global#AnnotationInfo): util.Map[_ <: ScalaElement, _] = {
    val result = new util.HashMap[ScalaElement, Any]()
    annotationMirror.tree.foreach {
      case arg: Global#AssignOrNamedArg => (arg.lhs, arg.rhs) match {
        case (ident:Global#Ident, literal:Global#Literal) => result.put(ScalaNameElement(ident.name), literal.value.value.toString)
        case _ => ()
      }
      case _ => ()
    }
    result
  }

  /**
   * Resolve the annotations values from the given member for the given type.
   *
   * @param originatingElement The originating element
   * @param member             The member
   * @param annotationType     The type
   * @return The values
   */
  override protected def getAnnotationValues(originatingElement: ScalaElement, member: ScalaElement, annotationType: Class[_]): OptionalValues[_] = {
    OptionalValues.empty[Any]
  }

  /**
   * Read the name of an annotation member.
   *
   * @param member The member
   * @return The name
   */
  override protected def getAnnotationMemberName(member: ScalaElement): String = getElementName(member)

  /**
   * Obtain the name of the repeatable annotation if the annotation is is one.
   *
   * @param annotationMirror The annotation mirror
   * @return Return the name or null
   */
  override protected def getRepeatableName(annotationMirror: Global#AnnotationInfo): String = {
    val typeElement = annotationMirror.symbol
    getRepeatableNameForType(ScalaSymbolElement(typeElement))
  }

/**
   * Obtain the name of the repeatable annotation if the annotation is is one.
   *
   * @param annotationType The annotation mirror
   * @return Return the name or null
   */
  override protected def getRepeatableNameForType(annotationType: ScalaElement): String = {
    annotationType match {
      case ScalaSymbolElement(symbol) =>
        val mirrors = symbol.annotations
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
        null // TODO
      case ScalaNameElement(name) => null
    }
  }

  /**
   * Return a mirror for the given annotation.
   *
   * @param annotationName The annotation name
   * @return An optional mirror
   */
  override protected def getAnnotationMirror(annotationName: String): Optional[ScalaElement] = ???

  /**
   * Get the annotation member.
   *
   * @param originatingElement The originatig element
   * @param member             The member
   * @return The annotation member
   */
  override protected def getAnnotationMember(originatingElement:ScalaElement, member: CharSequence): ScalaElement = ???

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
  override protected def getRetentionPolicy(annotation: ScalaElement): RetentionPolicy = {
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
