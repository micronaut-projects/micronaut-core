/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.annotation.processing;

import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.ARRAY;
import static javax.lang.model.type.TypeKind.ERROR;
import static javax.lang.model.type.TypeKind.NONE;
import static javax.lang.model.type.TypeKind.VOID;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.inject.processing.JavaModelUtils;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides utility method for working with the annotation processor AST.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class ModelUtils {

    private final Elements elementUtils;
    private final Types typeUtils;

    /**
     * @param elementUtils The {@link Elements}
     * @param typeUtils    The {@link Types}
     */
    ModelUtils(Elements elementUtils, Types typeUtils) {
        this.elementUtils = elementUtils;
        this.typeUtils = typeUtils;
    }

    /**
     * Obtains the {@link TypeElement} for an given element.
     *
     * @param element The element
     * @return The {@link TypeElement}
     */
    final TypeElement classElementFor(Element element) {
        while (!(element.getKind().isClass() || element.getKind().isInterface())) {
            element = element.getEnclosingElement();
        }
        return (TypeElement) element;
    }

    /**
     * The binary name of the type as a String.
     *
     * @param typeElement The type element
     * @return The class name
     */
    String simpleBinaryNameFor(TypeElement typeElement) {
        Name elementBinaryName = elementUtils.getBinaryName(typeElement);
        PackageElement packageElement = elementUtils.getPackageOf(typeElement);

        String packageName = packageElement.getQualifiedName().toString();
        return elementBinaryName.toString().replaceFirst(packageName + "\\.", "");
    }

    /**
     * Resolves a setter method for a field.
     *
     * @param field The field
     * @return An optional setter method
     */
    Optional<ExecutableElement> findSetterMethodFor(Element field) {
        String name = field.getSimpleName().toString();
        name = name.replaceFirst("^(is).+", "");
        String setterName = setterNameFor(name);
        // FIXME refine this to discover one of possible overloaded methods with correct signature (i.e. single arg of field type)
        TypeElement typeElement = classElementFor(field);
        List<? extends Element> elements = typeElement.getEnclosedElements();
        List<ExecutableElement> methods = ElementFilter.methodsIn(elements);
        return methods.stream()
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
    }

    /**
     * The name of a setter for the given field name.
     *
     * @param fieldName The field name
     * @return The setter name
     */
    String setterNameFor(String fieldName) {
        return "set" + NameUtils.capitalize(fieldName);
    }

    /**
     * The constructor inject for the given class element.
     *
     * @param classElement The class element
     * @return The constructor
     */
    @Nullable
    ExecutableElement concreteConstructorFor(TypeElement classElement) {
        List<ExecutableElement> constructors = findNonPrivateConstructors(classElement);
        if (constructors.isEmpty()) {
            return null;
        }
        if (constructors.size() == 1) {
            return constructors.get(0);
        }
        Optional<ExecutableElement> element = constructors.stream().filter(ctor ->
            Objects.nonNull(ctor.getAnnotation(Inject.class))
        ).findFirst();
        if (!element.isPresent()) {
            element = constructors.stream().filter(ctor ->
                ctor.getModifiers().contains(PUBLIC)
            ).findFirst();
        }
        return element.orElse(null);
    }

    /**
     * @param classElement The {@link TypeElement}
     * @return A list of {@link ExecutableElement}
     */
    List<ExecutableElement> findNonPrivateConstructors(TypeElement classElement) {
        List<ExecutableElement> ctors =
            ElementFilter.constructorsIn(classElement.getEnclosedElements());
        return ctors.stream()
            .filter(ctor -> !ctor.getModifiers().contains(PRIVATE))
            .collect(Collectors.toList());
    }

    /**
     * Obtains the class for a given primitive type name.
     *
     * @param primitiveType The primitive type name
     * @return The primtitive type class
     */
    Class<?> classOfPrimitiveFor(String primitiveType) {
        return ClassUtils.getPrimitiveType(primitiveType).orElseThrow(() -> new IllegalArgumentException("Unknown primitive type: " + primitiveType));
    }

    /**
     * Obtains the class for the given primitive type array.
     *
     * @param primitiveType The primitive type
     * @return The class
     */
    Class<?> classOfPrimitiveArrayFor(String primitiveType) {
        try {
            switch (primitiveType) {
                case "byte":
                    return Class.forName("[B");
                case "int":
                    return Class.forName("[I");
                case "short":
                    return Class.forName("[S");
                case "long":
                    return Class.forName("[J");
                case "float":
                    return Class.forName("[F");
                case "double":
                    return Class.forName("[D");
                case "char":
                    return Class.forName("[C");
                case "boolean":
                    return Class.forName("[Z");
                default:
                    // this can never occur
                    throw new IllegalArgumentException("Unsupported primitive type " + primitiveType);
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Obtains the super type element for a given type element.
     *
     * @param element The type element
     * @return The super type element or null if none exists
     */
    TypeElement superClassFor(TypeElement element) {
        TypeMirror superclass = element.getSuperclass();
        if (superclass.getKind() == TypeKind.NONE) {
            return null;
        }
        DeclaredType kind = (DeclaredType) superclass;
        return (TypeElement) kind.asElement();
    }

    /**
     * @param typeElement The {@link TypeElement}
     * @return The resolved type reference or the qualified name for the type element
     */
    Object resolveTypeReference(TypeElement typeElement) {
        TypeMirror type = typeElement.asType();
        if (type != null) {
            return resolveTypeReference(type);
        } else {
            return typeElement.getQualifiedName().toString();
        }
    }

    /**
     * Return whether the given element is the java.lang.Object class.
     *
     * @param element The element
     * @return True if it is java.lang.Object
     */
    boolean isObjectClass(TypeElement element) {
        return element.getSuperclass().getKind() == NONE;
    }

    /**
     * Resolves a type reference for the given element. A type reference is either a reference to the concrete
     * {@link Class} or a String representing the type name.
     *
     * @param element The element
     * @return The type reference
     */
    @Nullable
    Object resolveTypeReference(Element element) {
        if (element instanceof TypeElement) {
            TypeElement typeElement = (TypeElement) element;
            return resolveTypeReferenceForTypeElement(typeElement);
        }
        return null;
    }

    /**
     * Resolves a type reference for the given type element. A type reference is either a reference to the concrete
     * {@link Class} or a String representing the type name.
     *
     * @param typeElement The type
     * @return The type reference
     */

    Object resolveTypeReferenceForTypeElement(TypeElement typeElement) {
        return JavaModelUtils.getClassName(typeElement);
    }

    /**
     * Resolves a type name for the given name.
     *
     * @param type The type
     * @return The type reference
     */
    String resolveTypeName(TypeMirror type) {
        Object reference = resolveTypeReference(type);
        if (reference instanceof Class) {
            return ((Class) reference).getName();
        }
        return reference.toString();
    }

    /**
     * Resolves a type reference for the given type mirror. A type reference is either a reference to the concrete
     * {@link Class} or a String representing the type name.
     *
     * @param type The type
     * @return The type reference
     */
    Object resolveTypeReference(TypeMirror type) {
        Object result = Void.TYPE;
        if (type.getKind().isPrimitive()) {
            result = resolvePrimitiveTypeReference(type);
        } else if (type.getKind() == ARRAY) {
            ArrayType arrayType = (ArrayType) type;
            TypeMirror componentType = arrayType.getComponentType();
            if (componentType.getKind().isPrimitive()) {
                result = classOfPrimitiveArrayFor(resolvePrimitiveTypeReference(componentType).getName());
            } else {
                result = typeUtils.erasure(type).toString();
            }
        } else if (type.getKind() != VOID && type.getKind() != ERROR) {
            TypeElement typeElement = elementUtils.getTypeElement(typeUtils.erasure(type).toString());
            if (typeElement != null) {
                result = resolveTypeReferenceForTypeElement(typeElement);
            } else if (type instanceof DeclaredType) {
                result = resolveTypeReferenceForTypeElement((TypeElement) ((DeclaredType) type).asElement());
            }
        }
        return result;
    }

    /**
     * Returns whether an element is package private.
     *
     * @param element The element
     * @return True if it is package provide
     */
    boolean isPackagePrivate(Element element) {
        Set<Modifier> modifiers = element.getModifiers();
        return !(modifiers.contains(PUBLIC)
            || modifiers.contains(PROTECTED)
            || modifiers.contains(PRIVATE));
    }

    /**
     * Return whether the given method or field is inherited but not public.
     *
     * @param concreteClass  The concrete class
     * @param declaringClass The declaring class of the field
     * @param methodOrField  The method or field
     * @return True if it is inherited and not public
     */
    boolean isInheritedAndNotPublic(TypeElement concreteClass, TypeElement declaringClass, Element methodOrField) {
        PackageElement packageOfDeclaringClass = elementUtils.getPackageOf(declaringClass);
        PackageElement packageOfConcreteClass = elementUtils.getPackageOf(concreteClass);

        return declaringClass != concreteClass &&
            !packageOfDeclaringClass.getQualifiedName().equals(packageOfConcreteClass.getQualifiedName())
            && (isProtected(methodOrField) || !isPublic(methodOrField));
    }

    /**
     * Tests if candidate method is overridden from a given class or subclass.
     *
     * @param overridden   the candidate overridden method
     * @param classElement the type element that may contain the overriding method, either directly or in a subclass
     * @return the overriding method
     */
    Optional<ExecutableElement> overridingOrHidingMethod(ExecutableElement overridden, TypeElement classElement) {
        List<ExecutableElement> methods = ElementFilter.methodsIn(elementUtils.getAllMembers(classElement));
        for (ExecutableElement method : methods) {
            if (!method.equals(overridden) && method.getSimpleName().equals(overridden.getSimpleName())) {
                return Optional.of(method);
            }
        }
        // might be looking for a package private & packages differ method in a superclass
        // that is not visible to the most concrete subclass, really!
        // e.g. see injectPackagePrivateMethod4() for SpareTire -> Tire -> RoundThing in Inject tck
        // check the superclass until we reach Object, then bail out with empty if necessary.
        TypeElement superClass = superClassFor(classElement);
        if (superClass != null && !isObjectClass(superClass)) {
            return overridingOrHidingMethod(overridden, superClass);
        }
        return Optional.empty();
    }

    /**
     * Return whether the element is private.
     *
     * @param element The element
     * @return True if it is private
     */
    boolean isPrivate(Element element) {
        return element.getModifiers().contains(PRIVATE);
    }

    /**
     * Return whether the element is protected.
     *
     * @param element The element
     * @return True if it is protected
     */
    boolean isProtected(Element element) {
        return element.getModifiers().contains(PROTECTED);
    }

    /**
     * Return whether the element is public.
     *
     * @param element The element
     * @return True if it is public
     */
    boolean isPublic(Element element) {
        return element.getModifiers().contains(PUBLIC);
    }

    /**
     * Return whether the element is abstract.
     *
     * @param element The element
     * @return True if it is abstract
     */
    boolean isAbstract(Element element) {
        return element.getModifiers().contains(ABSTRACT);
    }

    /**
     * Return whether the element is static.
     *
     * @param element The element
     * @return True if it is static
     */
    boolean isStatic(Element element) {
        return element.getModifiers().contains(STATIC);
    }


    /**
     * Return whether the element is final.
     *
     * @param element The element
     * @return True if it is final
     */
    boolean isFinal(Element element) {
        return element.getModifiers().contains(FINAL);
    }

    /**
     * Is the given type mirror an optional.
     *
     * @param mirror The mirror
     * @return True if it is
     */
    boolean isOptional(TypeMirror mirror) {
        return typeUtils.erasure(mirror).toString().equals(Optional.class.getName());
    }

    private Class resolvePrimitiveTypeReference(TypeMirror type) {
        Class result;
        if (type instanceof DeclaredType) {
            DeclaredType dt = (DeclaredType) type;
            result = classOfPrimitiveFor(dt.asElement().getSimpleName().toString());
        } else {
            result = classOfPrimitiveFor(type.toString());
        }
        return result;
    }
}
