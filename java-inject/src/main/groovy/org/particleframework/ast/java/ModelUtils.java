package org.particleframework.ast.java;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.Modifier.*;

class ModelUtils {
    private final Elements elementUtils;
    private final Types typeUtils;

    ModelUtils(Elements elementUtils,Types typeUtils) {
        this.elementUtils = elementUtils;
        this.typeUtils = typeUtils;
    }

    TypeElement classElementFor(Element element) {
        while (CLASS != element.getKind() ) {
            element = element.getEnclosingElement();
        }
        return (TypeElement) element;
    }

    Optional<ExecutableElement> findSetterMethodFor(Element field) {
        String name = field.getSimpleName().toString();
        name = name.replaceFirst("^(is).+", "");
        String setterName = setterNameFor(name);
        // FIXME refine this to disocver one of possible overlaoded methods with correct signature (i.e. single arg of field type)
        TypeElement typeElement = classElementFor(field);
        List<? extends Element> elements = typeElement.getEnclosedElements();
        List<ExecutableElement> methods = ElementFilter.methodsIn(elements);
        Optional<ExecutableElement> element = methods.stream()
            .filter(method -> {
                String methodName = method.getSimpleName().toString();
                if (setterName.equals(methodName)) {
                    Set<Modifier> modifiers = method.getModifiers();
                    return
                        // it's not static
                        !modifiers.contains(STATIC)
                        // it's either public or package visibility
                        && modifiers.contains(PUBLIC)
                        || !(modifiers.contains(PRIVATE) || modifiers.contains(PROTECTED));
                }
                return false;
            })
            .findFirst();
        return element;
    }

    String setterNameFor(String fieldName) {
        final String rest = fieldName.substring(1);

        String name;
        // Funky rule so that names like 'pNAME' will still work.
        if (Character.isLowerCase(fieldName.charAt(0)) && (rest.length() > 0) && Character.isUpperCase(rest.charAt(0))) {
            name = fieldName;
        } else {
            name = fieldName.substring(0, 1).toUpperCase() + rest;
        }
        return "set" + name;
    }

    List<? extends Element> findPublicConstructors(String className) {
        TypeElement typeElement = elementUtils.getTypeElement(className);
        return elementUtils.getAllMembers(typeElement).stream()
            .filter(element ->
                element.getKind() == CONSTRUCTOR && element.getModifiers().contains(PUBLIC))
            .collect(Collectors.toList());
    }

    // FIXME test for requires reflection (private qualifier is just one aspect)
    // e.g. see InjectTransform requiresReflection = isPrivate || isPackagePrivateAndPackagesDiffer
    boolean requiresReflection(Element element) {
        Set<Modifier> modifiers = element.getModifiers();
        boolean requiresReflection = modifiers.contains(Modifier.PRIVATE);
        return requiresReflection;
    }

    TypeMirror wrapperForPrimitiveType(TypeMirror primitive) {
        return primitive.getKind().isPrimitive()
            ? typeUtils.boxedClass((PrimitiveType)primitive).asType()
            : primitive;
    }

    // for cases where Element.getKind() == FIELD
    // and field.asType().toString() is something like
    // "int" return Integer.TYPE
    // FIXME is there an API way to do this? I didn't find one so far
    Class<?> classOfPrimitiveFor(String primitiveType) {
        switch (primitiveType) {
            case "byte":
                return Byte.TYPE;
            case "int":
                return Integer.TYPE;
            case "short":
                return Short.TYPE;
            case "long":
                return Long.TYPE;
            case "float":
                return Float.TYPE;
            case "double":
                return Double.TYPE;
            case "char":
                return Character.TYPE;
            case "boolean":
                return Boolean.TYPE;
            default:
                return Void.TYPE;
        }
    }
}
