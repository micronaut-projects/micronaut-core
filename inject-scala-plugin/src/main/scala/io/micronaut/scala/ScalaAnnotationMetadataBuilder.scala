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
import io.micronaut.scala

object ScalaAnnotationMetadataBuilder {
  val ANNOTATION_DEFAULTS = new util.HashMap[String, util.Map[Global#Symbol, Any]]
  val CACHE = new mutable.HashMap[Global#Symbol, AnnotationMetadata]()
}

class ScalaAnnotationMetadataBuilder(val global: Global) extends AbstractAnnotationMetadataBuilder[Global#Symbol, Global#AnnotationInfo] {

  def getOrCreate(element: Global#Symbol): AnnotationMetadata =
    ScalaAnnotationMetadataBuilder.CACHE.getOrElseUpdate(element, buildOverridden(element))

  /**
   * Whether the element is a field, method, class or constructor.
   *
   * @param element The element
   * @return True if it is
   */
  override protected def isMethodOrClassElement(element: Global#Symbol): Boolean = element match {
    case _: Global#ClassSymbol | _: Global#MethodSymbol => true
    case _ => false
  }

  /**
   * Obtains the declaring type for an element.
   *
   * @param element The element
   * @return The declaring type
   */
  override protected def getDeclaringType(element: Global#Symbol): String = element.owner.nameString

  /**
   * Get the type of the given annotation.
   *
   * @param annotationMirror The annotation
   * @return The type
   */
  override protected def getTypeForAnnotation(annotationMirror: Global#AnnotationInfo): Global#Symbol = annotationMirror.atp.typeSymbol

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
  override protected def getElementName(element: Global#Symbol): String = element.nameString

  /**
   * Obtain the annotations for the given type.
   *
   * @param element The type element
   * @return The annotations
   */
  override protected def getAnnotationsForType(element: Global#Symbol): util.List[_ <: Global#AnnotationInfo] =
    (element match {
      case methodSymbol:Global#MethodSymbol if !methodSymbol.isPrivate && methodSymbol.isAccessor && methodSymbol.isSetter =>  methodSymbol.accessedOrSelf
      case _ => element
    }).annotations.asJava

  /**
   * Build the type hierarchy for the given element.
   *
   * @param element                The element
   * @param inheritTypeAnnotations Whether to inherit type annotations
   * @param declaredOnly           Whether to only include declared annotations
   * @return The type hierarchy
   */
  override protected def buildHierarchy(element: Global#Symbol, inheritTypeAnnotations: Boolean, declaredOnly: Boolean): util.List[Global#Symbol] = {
    if (declaredOnly) Collections.singletonList(element)
    else {
      element match {
        case tpe: Global#TypeSymbol => {
          val hierarchy = new util.ArrayList[Global#Symbol]
          populateTypeHierarchy(tpe, hierarchy)
          Collections.reverse(hierarchy)
          hierarchy
        }
        case deff: Global#MethodSymbol => { // we have a method
          // for methods we merge the data from any overridden interface or abstract methods
          // with type level data
          // the starting hierarchy is the type and super types of this method
          val hierarchy = if (inheritTypeAnnotations)
            buildHierarchy(deff.owner, false, declaredOnly)
          else new util.ArrayList[Global#Symbol]
          if (deff.isOverride) hierarchy.addAll(findOverriddenMethods(deff))
          hierarchy.add(deff)
          hierarchy
        }
        case varSym: Global#TermSymbol => {
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
  }

  private def populateTypeHierarchy(element:Global#Symbol, hierarchy: util.List[Global#Symbol]): Unit = {
    val baseClasses = element.baseClasses
    for (anInterface <- baseClasses) {
      hierarchy.add(anInterface)
    }
  }

  private def findOverriddenMethods(executableElement: Global#Symbol):util.List[_ <: Global#Symbol] = {
    executableElement match {
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
          Globals.methodsToBridgeOverrides.getOrElse(methodSymbol, List[Global#Symbol]())).asJava
      }
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
  override protected def readAnnotationRawValues(originatingElement: Global#Symbol, annotationName: String, member: Global#Symbol, memberName: String, annotationValue: Any, annotationValues: util.Map[CharSequence, AnyRef]): Unit = {
    //    if (memberName != null && annotationValue.isInstanceOf[AnnotationValue] && !annotationValues.containsKey(memberName)) {
    //      val resolver = new JavaAnnotationMetadataBuilder#MetadataAnnotationValueVisitor(originatingElement)
    //      annotationValue.asInstanceOf[AnnotationValue].accept(resolver, this)
    val resolvedValue = annotationValue match {
      case Array(elements@_*) => elements.map(resolveAnnotationValue).toArray
      case _ => new AnnotationClassValue(annotationValue.toString)
    }
    if (resolvedValue != null) {
      validateAnnotationValue(originatingElement, annotationName, member, memberName, resolvedValue)
      annotationValues.put(memberName, resolvedValue)
    }
  }

  private def resolveAnnotationValue(input:Any):AnnotationClassValue[_] = {
    new AnnotationClassValue(input.toString)
  }

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
  override protected def readAnnotationValue(originatingElement: Global#Symbol, member: Global#Symbol, memberName: String, annotationValue: AnyRef): AnyRef = {
    if (memberName != null /* && annotationValue.isInstanceOf[Global#TypeRef] */) {
      resolveAnnotationValue(annotationValue)
    } else if (memberName != null && annotationValue != null && ClassUtils.isJavaLangType(annotationValue.getClass)) { // only allow basic types
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
  override protected def readAnnotationDefaultValues(annotationMirror: Global#AnnotationInfo): util.Map[_ <: Global#Symbol, _] = {
    val annotationTypeName = getAnnotationTypeName(annotationMirror)
    readAnnotationDefaultValues(annotationTypeName, annotationMirror.symbol)
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
    element match {
      case annotationElement: Global#TypeSymbol =>
        val annotationName = annotationElement.fullName
        if (!defaults.containsKey(annotationName)) {
          val defaultValues = new util.HashMap[Global#Symbol, Any]
          annotationElement.originalInfo.decls.foreach { symbol:Global#Symbol =>
            symbol match {
              case method: Global#MethodSymbol if !method.isConstructor => {
                if (method.hasDefault) {
                  //defaultValues.put(method, method.defa)
                }
              }
              case _ => ()
            }
          }
          //            allMembers
          //              .filter((member: Global#Symbol) => member.enclClass == annotationElement)
          //              .filter(classOf[ExecutableElement].isInstance)
          //              .map(classOf[ExecutableElement].cast)
          //              .filter(this.isValidDefaultValue)
          //              .forEach((executableElement: ExecutableElement) => {
          //                val defaultValue = executableElement.getDefaultValue
          //                defaultValues.put(executableElement, defaultValue)
          //            })
          defaults.put(annotationName, defaultValues)
        }
        ScalaAnnotationMetadataBuilder.ANNOTATION_DEFAULTS.get(element)
    }
  }

  private def processAnnotationRawValue(arg:Global#ClassfileAnnotArg):Any = {
    arg match {
      case literal:Global#LiteralAnnotArg => literal.const.value
      case array:Global#ArrayAnnotArg => array.args.map(processAnnotationRawValue)
      case _ => ???
    }
  }

  /**
   * Read the raw annotation values from the given annotation.
   *
   * @param annotationMirror The annotation
   * @return The values
   */
  override protected def readAnnotationRawValues(annotationMirror: Global#AnnotationInfo): util.Map[_ <: Global#Symbol, _] = {
    val result = new util.HashMap[Global#Symbol, Any]()
    val methodMap:Map[String, Global#Symbol] = annotationMirror.symbol.originalInfo.decls
      .filter((it:Global#Symbol) => it.isMethod && !it.isConstructor)
      .map(it => (it.nameString, it))
      .toMap
    annotationMirror.assocs.foreach {
      case (name, arg) => result.put(methodMap(name.toString()), processAnnotationRawValue(arg))
      case _ => ??? // Is this possible? TODO
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
  override protected def getAnnotationValues(originatingElement: Global#Symbol, member: Global#Symbol, annotationType: Class[_]): OptionalValues[_] = {
    val converted = new util.LinkedHashMap[CharSequence, AnyRef]
    val annotationName = annotationType.getName
    for (annotationMirror <-  member.annotations) {
      if (annotationMirror.symbol.fullName.endsWith(annotationName)) {
        val values = readAnnotationRawValues(annotationMirror)
        values.entrySet.forEach { entry =>
          val key = entry.getKey
          val value = entry.getValue
          readAnnotationRawValues(originatingElement, annotationName, member, key.nameString, value, converted)
        }
      }
    }
    OptionalValues.of(classOf[Any], converted)
  }

  /**
   * Read the name of an annotation member.
   *
   * @param member The member
   * @return The name
   */
  override protected def getAnnotationMemberName(member: Global#Symbol): String = getElementName(member)

  /**
   * Obtain the name of the repeatable annotation if the annotation is is one.
   *
   * @param annotationMirror The annotation mirror
   * @return Return the name or null
   */
  override protected def getRepeatableName(annotationMirror: Global#AnnotationInfo): String = getRepeatableNameForType(annotationMirror.symbol)

  /**
   * Obtain the name of the repeatable annotation if the annotation is is one.
   *
   * @param annotationType The annotation mirror
   * @return Return the name or null
   */
  override protected def getRepeatableNameForType(annotationType: Global#Symbol): String = {
    val mirrors = annotationType.annotations
    for (mirror <- mirrors) {
      val name = mirror.symbol.fullName
      if (classOf[Repeatable].getName == name) {
        mirror.assocs.foreach { entry =>
          if ("value" == entry._1.toString && entry._2.isInstanceOf[Global#LiteralAnnotArg]) {
            return entry._2.asInstanceOf[Global#LiteralAnnotArg].const.value.toString
          }
        }
      }
    }
    null // TODO
  }

  /**
   * Return a mirror for the given annotation.
   *
   * @param annotationName The annotation name
   * @return An optional mirror
   */
  override protected def getAnnotationMirror(annotationName: String): Optional[Global#Symbol] = {
    try {
      Optional.of(global.rootMirror.getClassByName(annotationName))
    } catch {
      case _:Throwable => Optional.empty()
    }
  }

  /**
   * Get the annotation member.
   *
   * @param originatingElement The originatig element
   * @param member             The member
   * @return The annotation member
   */
  override protected def getAnnotationMember(originatingElement:Global#Symbol, member: CharSequence): Global#Symbol = ???

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
