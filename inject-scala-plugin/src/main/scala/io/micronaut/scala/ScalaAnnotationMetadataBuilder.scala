package io.micronaut.scala

import java.lang.annotation.{Annotation, Repeatable, RetentionPolicy}
import java.util
import java.util.{Collections, Optional}

import io.micronaut.core.annotation.{AnnotationClassValue, AnnotationMetadata}
import io.micronaut.core.reflect.ClassUtils
import io.micronaut.core.value.OptionalValues
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder
import io.micronaut.inject.visitor.VisitorContext

import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.tools.nsc.Global

object ScalaAnnotationMetadataBuilder {
  val ANNOTATION_DEFAULTS = new util.HashMap[String, util.Map[ElementFacade, Any]]
  val CACHE = new mutable.HashMap[ElementFacade, AnnotationMetadata]()
}

class ScalaAnnotationMetadataBuilder extends AbstractAnnotationMetadataBuilder[ElementFacade, AnnotationFacade] {

  def getOrCreate(element: ElementFacade): AnnotationMetadata =
    ScalaAnnotationMetadataBuilder.CACHE.getOrElseUpdate(element, buildOverridden(element))

  /**
   * Whether the element is a field, method, class or constructor.
   *
   * @param element The element
   * @return True if it is
   */
  override protected def isMethodOrClassElement(element: ElementFacade): Boolean = element match {
    case SymbolFacade(symbol) => symbol match {
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
  override protected def getDeclaringType(element: ElementFacade): String =
    element match {
      case SymbolFacade(symbol) => symbol.owner.nameString
      case NameFacade(cls, _) => cls.getCanonicalName
    }

  /**
   * Get the type of the given annotation.
   *
   * @param annotationMirror The annotation
   * @return The type
   */
  override protected def getTypeForAnnotation(annotationMirror: AnnotationFacade): ElementFacade = {
    annotationMirror match {
      case ScalaAnnotationFacade(annotationInfo) => SymbolFacade(annotationInfo.atp.typeSymbol)
      case JavaAnnotationFacade(cls, _) => NameFacade(cls, "")
    }
  }

  /**
   * Checks whether an annotation is present.
   *
   * @param element    The element
   * @param annotation The annotation type
   * @return True if the annotation is present
   */
  override protected def hasAnnotation(element: ElementFacade, annotation: Class[_ <: java.lang.annotation.Annotation]): Boolean =
    element match {
      case SymbolFacade(symbol) => symbol.annotations.exists{ anno => anno.tree.tpe.toString.equals(annotation.getCanonicalName) }
      case NameFacade(cls, _) => annotation == cls
    }

  /**
   * Get the given type of the annotation.
   *
   * @param annotationMirror The annotation
   * @return The type
   */
  override protected def getAnnotationTypeName(annotationMirror: AnnotationFacade): String = annotationMirror match {
    case ScalaAnnotationFacade(annotationInfo) => annotationInfo.atp.toString
    case JavaAnnotationFacade(cls, _) => cls.getCanonicalName
  }

  /**
   * Get the name for the given element.
   *
   * @param element The element
   * @return The name
   */
  override protected def getElementName(element: ElementFacade): String = element match {
    case SymbolFacade(symbol) => symbol.nameString
    case NameFacade(_, name) => name
  }

  /**
   * Obtain the annotations for the given type.
   *
   * @param element The type element
   * @return The annotations
   */
  override protected def getAnnotationsForType(element: ElementFacade): util.List[_ <: AnnotationFacade] =
    element match {
      case SymbolFacade(symbol) => symbol.annotations.map { ScalaAnnotationFacade(_) }.asJava
      case NameFacade(cls, _) => cls.getAnnotations.map { anno =>
        JavaAnnotationFacade(anno.annotationType, Map())
      }.toList.asJava
    }

  /**
   * Build the type hierarchy for the given element.
   *
   * @param element                The element
   * @param inheritTypeAnnotations Whether to inherit type annotations
   * @param declaredOnly           Whether to only include declared annotations
   * @return The type hierarchy
   */
  override protected def buildHierarchy(element: ElementFacade, inheritTypeAnnotations: Boolean, declaredOnly: Boolean): util.List[ElementFacade] = {
    element match {
      case SymbolFacade(symbol) => {
        if (declaredOnly) Collections.singletonList(SymbolFacade(symbol))
        else {
          symbol match {
            case tpe: Global#TypeSymbol => {
              val hierarchy = new util.ArrayList[ElementFacade]
              populateTypeHierarchy(SymbolFacade(tpe), hierarchy)
              Collections.reverse(hierarchy)
              hierarchy
            }
            case deff: Global#MethodSymbol => { // we have a method
              // for methods we merge the data from any overridden interface or abstract methods
              // with type level data
              // the starting hierarchy is the type and super types of this method
              val hierarchy = if (inheritTypeAnnotations)
                buildHierarchy(SymbolFacade(deff.owner), false, declaredOnly)
              else new util.ArrayList[ElementFacade]
              val symbolFacade = SymbolFacade(deff)
               if (deff.isOverride) hierarchy.addAll(findOverriddenMethods(symbolFacade))
              hierarchy.add(symbolFacade)
              hierarchy
            }
            case varSym: Global#TermSymbol => {
              val hierarchy = new util.ArrayList[ElementFacade]
              val enclosingElement = varSym.owner
              enclosingElement match {
                case executableElement: Global#MethodSymbol =>
                  if (hasAnnotation(SymbolFacade(executableElement), classOf[Override])) {
                    //            val variableIdx = executableElement.getParameters.indexOf(variable)
                    //            for (overridden <- findOverriddenMethods(executableElement)) {
                    //              hierarchy.add(overridden.getParameters.get(variableIdx))
                    //            }
                  }
                case _ =>
              }
              hierarchy.add(SymbolFacade(varSym))
              hierarchy
            }
            case _ => {
              val single = new util.ArrayList[ElementFacade]
              single.add(SymbolFacade(symbol))
              single
            }
          }
        }
      }
      case NameFacade(_,_) => new util.ArrayList[ElementFacade]
    }
  }

  private def populateTypeHierarchy(element:ElementFacade, hierarchy: util.List[ElementFacade]): Unit = {
    element match {
      case SymbolFacade(element) => {
          val baseClasses = element.baseClasses
          for (anInterface <- baseClasses) {
            val interfaceElement = SymbolFacade(anInterface)
            hierarchy.add(interfaceElement)
          }
        }
      case NameFacade(_,_) => ()
    }
  }

  private def findOverriddenMethods(executableElement: ElementFacade):util.List[_ <: SymbolFacade] = {
    executableElement match {
      case SymbolFacade(symbol) => symbol match {
        //case supertype:Global#TypeSymbol =>
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
        case methodSymbol:Global#MethodSymbol => {
          (methodSymbol.overrides ++
            Globals.methodsToBridgeOverrides.getOrElse(methodSymbol, List[Global#Symbol]()))
              .map(SymbolFacade(_:Global#Symbol)).asJava
        }
      }
      case _ => Collections.emptyList()
    }
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
  override protected def readAnnotationRawValues(originatingElement: ElementFacade, annotationName: String, member: ElementFacade, memberName: String, annotationValue: Any, annotationValues: util.Map[CharSequence, AnyRef]): Unit = {
//    if (memberName != null && annotationValue.isInstanceOf[AnnotationValue] && !annotationValues.containsKey(memberName)) {
//      val resolver = new JavaAnnotationMetadataBuilder#MetadataAnnotationValueVisitor(originatingElement)
//      annotationValue.asInstanceOf[AnnotationValue].accept(resolver, this)
      val resolvedValue = annotationValue.toString
      if (resolvedValue != null) {
        val resolvedAnnotationValue = new AnnotationClassValue(resolvedValue)
        validateAnnotationValue(originatingElement, annotationName, member, memberName, resolvedAnnotationValue)
        annotationValues.put(memberName, resolvedAnnotationValue)
      }
//    }
  }

  /**
   * Adds an error.
   *
   * @param originatingElement The originating element
   * @param error              The error
   */
  override protected def addError(originatingElement: ElementFacade, error: String): Unit = ???

  /**
   * Read the given member and value, applying conversions if necessary, and place the data in the given map.
   *
   * @param originatingElement The originating element
   * @param member             The member
   * @param memberName         The member name
   * @param annotationValue    The value
   * @return The object
   */
  override protected def readAnnotationValue(originatingElement: ElementFacade, member: ElementFacade, memberName: String, annotationValue: AnyRef): AnyRef = {
//    if (memberName != null && annotationValue.isInstanceOf[AnnotationValue]) {
//      val visitor = new JavaAnnotationMetadataBuilder#MetadataAnnotationValueVisitor(originatingElement)
//      annotationValue.asInstanceOf[AnnotationValue].accept(visitor, this)
//      return visitor.resolvedValue
//    }
//    else
    if (memberName != null && annotationValue != null && ClassUtils.isJavaLangType(annotationValue.getClass)) { // only allow basic types
      annotationValue
    } else {
      null
    }
  }

  /**
   * Read the raw default annotation values from the given annotation.
   *
   * @param annotationMirror The annotation
   * @return The values
   */
  override protected def readAnnotationDefaultValues(annotationMirror: AnnotationFacade): util.Map[_ <: ElementFacade, _] = {
    val annotationTypeName = getAnnotationTypeName(annotationMirror)
    annotationMirror match {
      case ScalaAnnotationFacade(annotationInfo) => readAnnotationDefaultValues(annotationTypeName, SymbolFacade(annotationInfo.symbol))
      case JavaAnnotationFacade(cls, values) => readAnnotationDefaultValues(annotationTypeName, NameFacade(cls, ""))  // TODO need something with values
    }
  }

  /**
   * Read the raw default annotation values from the given annotation.
   *
   * @param annotationName annotation name
   * @param element the type
   * @return The values
   */
  override protected def readAnnotationDefaultValues(annotationName: String, element: ElementFacade): util.Map[_ <: ElementFacade, _] = {
    val defaults = ScalaAnnotationMetadataBuilder.ANNOTATION_DEFAULTS
    element match {
      case SymbolFacade(symbol) => symbol match {
        case annotationElement: Global#TypeSymbol =>
          val annotationName = annotationElement.fullName
          if (!defaults.containsKey(annotationName)) {
            val defaultValues = new util.HashMap[ElementFacade, Any]
            val allMembers = annotationElement.children
            //allMembers.filter((member: Global#Symbol) => member.getEnclosingElement == annotationElement).filter(classOf[ExecutableElement].isInstance).map(classOf[ExecutableElement].cast).filter(this.isValidDefaultValue).forEach((executableElement: ExecutableElement) => {
            //          def foo(executableElement: ExecutableElement) = {
            //            val defaultValue = executableElement.getDefaultValue
            //            defaultValues.put(executableElement, defaultValue)
            //          }
            //
            //          foo(executableElement)
            //        })
            //defaults.put(annotationName, defaultValues)
          }
          ScalaAnnotationMetadataBuilder.ANNOTATION_DEFAULTS.get(element)
      }
      case NameFacade(_,_) =>  Collections.emptyMap()
    }
    Collections.emptyMap()
  }

  /**
   * Read the raw annotation values from the given annotation.
   *
   * @param annotationMirror The annotation
   * @return The values
   */
  override protected def readAnnotationRawValues(annotationMirror: AnnotationFacade): util.Map[_ <: NameFacade, _] = {
    val result = new util.HashMap[NameFacade, Any]()
    annotationMirror match {
      case ScalaAnnotationFacade(annotationInfo) => {
        annotationInfo.assocs.foreach {
          // Scala 2.12 annotationInfo.tree.foreach {
          //      case arg: Global#AssignOrNamedArg => (arg.lhs, arg.rhs) match {
          //        case (ident:Global#Ident, literal:Global#Literal) => result.put(ScalaNameElement(annotationMirror.symbol, ident.name), literal.value.value.toString)
          //        case _ => ()
          //      }
          case (name, arg) if (arg.isInstanceOf[Global#LiteralAnnotArg]) => {
            result.put(NameFacade(Class.forName(annotationInfo.symbol.fullName), name.toString), arg.asInstanceOf[Global#LiteralAnnotArg].const.value)
          }
        }
      }
      case JavaAnnotationFacade(els, values) => {

      }
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
  override protected def getAnnotationValues(originatingElement: ElementFacade, member: ElementFacade, annotationType: Class[_]): OptionalValues[_] = {
    val converted = new util.LinkedHashMap[CharSequence, AnyRef]

    member match {
      case SymbolFacade(symbol) => {
        val annotationName = annotationType.getName
        for (annotationMirror <-  symbol.annotations) {
          if (annotationMirror.symbol.fullName.endsWith(annotationName)) {
            val values = readAnnotationRawValues(ScalaAnnotationFacade(annotationMirror))
            values.entrySet.forEach { entry =>
              val key = entry.getKey
              val value = entry.getValue
              readAnnotationRawValues(originatingElement, annotationName, member, key.toString, value, converted)
            }
          }
        }
      }
      case NameFacade(annOwner, name) => {
        if (annOwner.isAnnotation) {
          Optional.ofNullable(annOwner
            .getMethod(name)
            .getAnnotation(annotationType.asInstanceOf[Class[Annotation]]))
            .ifPresent { it =>
              // HACK
              val itString = it.toString
              for (method <- it.getClass.getMethods) {
                if (itString.contains(method.getName + "=")) {
                  converted.put(method.getName, wrapAnnotationAttribute(method.invoke(it)))
                }
              }
            }
        }
      }
    }
    OptionalValues.of(classOf[Any], converted)
  }

  private def wrapAnnotationAttribute[T](attrValue: AnyRef) = attrValue match {
    case clazz:Class[T] => new AnnotationClassValue[T](clazz)
    case _ => attrValue
  }

  /**
   * Read the name of an annotation member.
   *
   * @param member The member
   * @return The name
   */
  override protected def getAnnotationMemberName(member: ElementFacade): String = getElementName(member)

  /**
   * Obtain the name of the repeatable annotation if the annotation is is one.
   *
   * @param annotationMirror The annotation mirror
   * @return Return the name or null
   */
  override protected def getRepeatableName(annotationMirror: AnnotationFacade): String = {
    annotationMirror match {
      case ScalaAnnotationFacade(annotationInfo) => getRepeatableNameForType(SymbolFacade(annotationInfo.symbol))
      case JavaAnnotationFacade(els, values) => null
    }
  }

/**
   * Obtain the name of the repeatable annotation if the annotation is is one.
   *
   * @param annotationType The annotation mirror
   * @return Return the name or null
   */
  override protected def getRepeatableNameForType(annotationType: ElementFacade): String = {
    annotationType match {
      case SymbolFacade(symbol) =>
        val mirrors = symbol.annotations
        for (mirror <- mirrors) {
          val name = mirror.symbol.fullName
          if (classOf[Repeatable].getName == name) {
            mirror.assocs.foreach { entry =>
              if ("value" == entry._1.toString && entry._2.isInstanceOf[Global#LiteralAnnotArg]) {
//Scala 2.12                return entry._2.asInstanceOf[Global#LiteralAnnotArg].value.value.toString
                return entry._2.asInstanceOf[Global#LiteralAnnotArg].const.value.toString
              }
            }
          }
        }
        null // TODO
      case NameFacade(_,_) => null
    }
  }

  /**
   * Return a mirror for the given annotation.
   *
   * @param annotationName The annotation name
   * @return An optional mirror
   */
  override protected def getAnnotationMirror(annotationName: String): Optional[ElementFacade] = {
    val clazz = Class.forName(annotationName)
    if (clazz.isAnnotation) {
      Optional.of(NameFacade(clazz, ""))
    } else {
      Optional.empty()
    }
  }

  /**
   * Get the annotation member.
   *
   * @param originatingElement The originatig element
   * @param member             The member
   * @return The annotation member
   */
  override protected def getAnnotationMember(originatingElement:ElementFacade, member: CharSequence): ElementFacade = ???

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
  override protected def getRetentionPolicy(annotation: ElementFacade): RetentionPolicy = {
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
