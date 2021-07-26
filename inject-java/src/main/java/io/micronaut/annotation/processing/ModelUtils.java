/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.annotation.processing;

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.JavaModelUtils;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static javax.lang.model.element.Modifier.*;
import static javax.lang.model.type.TypeKind.NONE;

/**
 * Provides utility method for working with the annotation processor AST.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class ModelUtils {

    private final Elements elementUtils;
    private final Types typeUtils;

    /**
     * @param elementUtils The {@link Elements}
     * @param typeUtils    The {@link Types}
     */
    protected ModelUtils(Elements elementUtils, Types typeUtils) {
        this.elementUtils = elementUtils;
        this.typeUtils = typeUtils;
    }

    /**
     * @return The type utilities
     */
    public Types getTypeUtils() {
        return typeUtils;
    }

    /**
     * Obtains the {@link TypeElement} for an given element.
     *
     * @param element The element
     * @return The {@link TypeElement}
     */
    @Nullable public final TypeElement classElementFor(Element element) {
        while (element != null && !(JavaModelUtils.isClassOrInterface(element) || JavaModelUtils.isRecord(element) || JavaModelUtils.isEnum(element))) {
            element = element.getEnclosingElement();
        }
        if (element instanceof  TypeElement) {
            return (TypeElement) element;
        }
        return null;
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
    Optional<ExecutableElement> findGetterMethodFor(Element field) {
        // FIXME refine this to discover one of possible overloaded methods with correct signature (i.e. single arg of field type)
        TypeElement typeElement = classElementFor(field);
        if (typeElement == null) {
            return Optional.empty();
        }

        String getterName = getterNameFor(field);
        List<? extends Element> elements = typeElement.getEnclosedElements();
        List<ExecutableElement> methods = ElementFilter.methodsIn(elements);
        return methods.stream()
                .filter(method -> {
                    String methodName = method.getSimpleName().toString();
                    if (getterName.equals(methodName)) {
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
     * Resolves a setter method for a field.
     *
     * @param field The field
     * @return An optional setter method
     */
    Optional<ExecutableElement> findSetterMethodFor(Element field) {
        String name = field.getSimpleName().toString();
        if (field.asType().getKind() == TypeKind.BOOLEAN && name.length() > 2 && Character.isUpperCase(name.charAt(2))) {
            name = name.replaceFirst("^(is)(.+)", "$2");
        }
        // FIXME refine this to discover one of possible overloaded methods with correct signature (i.e. single arg of field type)
        TypeElement typeElement = classElementFor(field);
        if (typeElement == null) {
            return Optional.empty();
        }

        String setterName = setterNameFor(name);
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
     * The name of a getter for the given field.
     *
     * @param field The field in question
     * @return The getter name
     */
    String getterNameFor(Element field) {
        String methodNamePrefix = "get";
        if (field.asType().getKind() == TypeKind.BOOLEAN) {
            methodNamePrefix = "is";
        }
        return methodNamePrefix + NameUtils.capitalize(field.getSimpleName().toString());
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
     * @param annotationUtils The annotation utilities
     * @return The constructor
     */
    @Nullable
    public ExecutableElement concreteConstructorFor(TypeElement classElement, AnnotationUtils annotationUtils) {
        if (JavaModelUtils.isRecord(classElement)) {
            final List<ExecutableElement> constructors = ElementFilter
                    .constructorsIn(classElement.getEnclosedElements());
            Optional<ExecutableElement> element = findAnnotatedConstructor(annotationUtils, constructors);
            if (element.isPresent()) {
                 return element.get();
            } else {

                // with records the record constructor is always the last constructor
                return constructors.get(constructors.size() - 1);
            }
        } else {
            List<ExecutableElement> constructors = findNonPrivateConstructors(classElement);
            if (constructors.isEmpty()) {
                return null;
            }
            if (constructors.size() == 1) {
                return constructors.get(0);
            }

            Optional<ExecutableElement> element = findAnnotatedConstructor(annotationUtils, constructors);
            if (!element.isPresent()) {
                final Comparator<ExecutableElement> comparator = Comparator.comparingInt(e -> e.getParameters().size());
                element = constructors.stream()
                        .sorted(comparator.reversed())
                        .filter(ctor ->
                        ctor.getModifiers().contains(PUBLIC)
                ).findFirst();
            }
            return element.orElse(null);
        }
    }

    private Optional<ExecutableElement> findAnnotatedConstructor(AnnotationUtils annotationUtils, List<ExecutableElement> constructors) {
        return constructors.stream().filter(ctor -> {
                    final AnnotationMetadata annotationMetadata = annotationUtils.getAnnotationMetadata(ctor);
                    return annotationMetadata.hasStereotype(AnnotationUtil.INJECT) || annotationMetadata.hasStereotype(Creator.class);
                }
        ).findFirst();
    }

    /**
     * The static method or Kotlin companion method to execute to
     * construct the given class element.
     *
     * @param classElement The class element
     * @param annotationUtils The annotation utilities
     * @return The creator method
     */
    @Nullable
    public ExecutableElement staticCreatorFor(TypeElement classElement, AnnotationUtils annotationUtils) {
         List<ExecutableElement> creators = findNonPrivateStaticCreators(classElement, annotationUtils);

        if (creators.isEmpty()) {
            return null;
        }
        if (creators.size() == 1) {
            return creators.get(0);
        }

        //Can be multiple static @Creator methods. Prefer one with args here. The no arg method (if present) will
        //be picked up by staticDefaultCreatorFor
        List<ExecutableElement> withArgs = creators.stream().filter(method -> !method.getParameters().isEmpty()).collect(Collectors.toList());

        if (withArgs.size() == 1) {
            return withArgs.get(0);
        } else {
            creators = withArgs;
        }

        return creators.stream().filter(method -> method.getModifiers().contains(PUBLIC)).findFirst().orElse(null);
    }

    /**
     * @param classElement The class element
     * @return True if the element has a non private 0 arg constructor
     */
    public ExecutableElement defaultConstructorFor(TypeElement classElement) {
        List<ExecutableElement> constructors = findNonPrivateConstructors(classElement)
                .stream().filter(ctor -> ctor.getParameters().isEmpty()).collect(Collectors.toList());

        if (constructors.isEmpty()) {
            return null;
        }

        if (constructors.size() == 1) {
            return constructors.get(0);
        }

        return constructors.stream().filter(method -> method.getModifiers().contains(PUBLIC)).findFirst().orElse(null);
    }

    /**
     * @param classElement The class element
     * @param annotationUtils The annotation utils
     * @return A static creator with no args, or null
     */
    public ExecutableElement defaultStaticCreatorFor(TypeElement classElement, AnnotationUtils annotationUtils) {
        List<ExecutableElement> creators = findNonPrivateStaticCreators(classElement, annotationUtils)
                .stream().filter(ctor -> ctor.getParameters().isEmpty()).collect(Collectors.toList());

        if (creators.isEmpty()) {
            return null;
        }

        if (creators.size() == 1) {
            return creators.get(0);
        }

        return creators.stream().filter(method -> method.getModifiers().contains(PUBLIC)).findFirst().orElse(null);
    }

    /**
     * Return whether the given element is the java.lang.Object class.
     *
     * @param element The element
     * @return True if it is java.lang.Object
     */
    public boolean isObjectClass(TypeElement element) {
        return element.getSuperclass().getKind() == NONE;
    }

    /**
     * @param classElement The {@link TypeElement}
     * @return A list of {@link ExecutableElement}
     */
    private List<ExecutableElement> findNonPrivateConstructors(TypeElement classElement) {
        List<ExecutableElement> ctors =
            ElementFilter.constructorsIn(classElement.getEnclosedElements());
        return ctors.stream()
            .filter(ctor -> !ctor.getModifiers().contains(PRIVATE))
            .collect(Collectors.toList());
    }

    private List<ExecutableElement> findNonPrivateStaticCreators(TypeElement classElement, AnnotationUtils annotationUtils) {
        List<? extends Element> enclosedElements = classElement.getEnclosedElements();
        List<ExecutableElement> staticCreators = ElementFilter.methodsIn(enclosedElements)
                .stream()
                .filter(method -> method.getModifiers().contains(STATIC))
                .filter(method -> !method.getModifiers().contains(PRIVATE))
                .filter(method -> typeUtils.isAssignable(typeUtils.erasure(method.getReturnType()), classElement.asType()))
                .filter(method -> {
                    final AnnotationMetadata annotationMetadata = annotationUtils.getAnnotationMetadata(method);
                    return annotationMetadata.hasStereotype(Creator.class);
                })
                .collect(Collectors.toList());

        if (staticCreators.isEmpty()) {
            TypeElement companionClass = ElementFilter.typesIn(enclosedElements)
                    .stream()
                    .filter(type -> type.getSimpleName().toString().equals("Companion"))
                    .filter(type -> type.getModifiers().contains(STATIC))
                    .findFirst().orElse(null);

            if (companionClass != null) {
                staticCreators = ElementFilter.methodsIn(companionClass.getEnclosedElements())
                        .stream()
                        .filter(method -> !method.getModifiers().contains(PRIVATE))
                        .filter(method -> method.getReturnType().equals(classElement.asType()))
                        .filter(method -> {
                            final AnnotationMetadata annotationMetadata = annotationUtils.getAnnotationMetadata(method);
                            return annotationMetadata.hasStereotype(Creator.class);
                        })
                        .collect(Collectors.toList());
            } else if (classElement.getKind() == ElementKind.ENUM) {
                staticCreators = ElementFilter.methodsIn(classElement.getEnclosedElements())
                        .stream()
                        .filter(method -> method.getModifiers().contains(STATIC))
                        .filter(method -> !method.getModifiers().contains(PRIVATE))
                        .filter(method -> method.getReturnType().equals(classElement.asType()))
                        .filter(method -> method.getSimpleName().toString().equals("valueOf"))
                        .collect(Collectors.toList());
            }
        }

        return staticCreators;
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
     * Resolves a type name for the given name.
     *
     * @param type The type
     * @return The type reference
     */
    String resolveTypeName(TypeMirror type) {
        TypeMirror typeMirror = resolveTypeReference(type);
        if (typeMirror.getKind().isPrimitive()) {
            return typeMirror.toString();
        }

        TypeElement typeElement = (TypeElement) typeUtils.asElement(typeMirror);
        if (typeElement != null) {
            return elementUtils.getBinaryName(typeElement).toString();
        } else {
            return typeUtils.erasure(typeMirror).toString();
        }
    }

    /**
     * Resolves a type reference for the given type mirror. A type reference is either a reference to the concrete
     * {@link Class} or a String representing the type name.
     *
     * @param type The type
     * @return The type reference
     */
    TypeMirror resolveTypeReference(TypeMirror type) {
        TypeKind typeKind = type.getKind();
        if (typeKind.isPrimitive()) {
            return type;
        } else if (typeKind == TypeKind.DECLARED) {
            DeclaredType dt = (DeclaredType) type;
            if (dt.getTypeArguments().isEmpty()) {
                return dt;
            }
            return typeUtils.erasure(type);
        } else {
            return typeUtils.erasure(type);
        }
    }

    /**
     * Returns whether an element is package private.
     *
     * @param element The element
     * @return True if it is package provide
     */
    public boolean isPackagePrivate(Element element) {
        Set<Modifier> modifiers = element.getModifiers();
        return !(modifiers.contains(PUBLIC)
            || modifiers.contains(PROTECTED)
            || modifiers.contains(PRIVATE));
    }

    /**
     * @param aClass A class
     * @return All the interfaces
     */
    public Set<TypeElement> getAllInterfaces(TypeElement aClass) {
        SortedSet<TypeElement> interfaces = new TreeSet<>((o1, o2) -> {
            if (typeUtils.isSubtype(o1.asType(), o2.asType())) {
                return -1;
            } else if (o1.equals(o2)) {
                return 0;
            } else {
                return 1;
            }
        });
        return populateInterfaces(aClass, interfaces);
    }

    /**
     * @param aClass     A class
     * @param interfaces The interfaces
     * @return A set with the interfaces
     */
    @SuppressWarnings("Duplicates")
    private Set<TypeElement> populateInterfaces(TypeElement aClass, Set<TypeElement> interfaces) {
        List<? extends TypeMirror> theInterfaces = aClass.getInterfaces();
        interfaces.addAll(theInterfaces.stream().flatMap(tm -> {
            TypeMirror erased = typeUtils.erasure(tm);
            if (erased instanceof DeclaredType) {
                Element e = ((DeclaredType) erased).asElement();
                if (e instanceof TypeElement) {
                    return Stream.of((TypeElement) e);
                }
            }
            return Stream.empty();
        }).collect(Collectors.toSet()));
        for (TypeMirror theInterface : theInterfaces) {
            TypeMirror erased = typeUtils.erasure(theInterface);
            if (erased instanceof DeclaredType) {
                Element e = ((DeclaredType) erased).asElement();
                if (e instanceof TypeElement) {
                    populateInterfaces(((TypeElement) e), interfaces);
                }
            }
        }
        if (aClass.getKind() != ElementKind.INTERFACE) {
            TypeMirror superclass = aClass.getSuperclass();
            while (superclass != null) {
                TypeMirror erased = typeUtils.erasure(superclass);
                if (erased instanceof DeclaredType) {
                    Element e = ((DeclaredType) erased).asElement();
                    if (e instanceof TypeElement) {
                        TypeElement superTypeElement = (TypeElement) e;
                        populateInterfaces(superTypeElement, interfaces);
                        superclass = superTypeElement.getSuperclass();
                    }
                } else {
                    break;
                }
            }
        }
        return interfaces;
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
     * Return whether the given method or field is inherited but not public.
     *
     * @param concreteClass  The concrete class
     * @param declaringClass The declaring class of the field
     * @param methodOrField  The method or field
     * @return True if it is inherited and not public
     */
    boolean isInheritedAndNotPublic(ClassElement concreteClass, ClassElement declaringClass, io.micronaut.inject.ast.Element methodOrField) {
        String packageOfDeclaringClass = declaringClass.getPackageName();
        String packageOfConcreteClass = concreteClass.getPackageName();

        return declaringClass != concreteClass &&
                !packageOfDeclaringClass.equals(packageOfConcreteClass)
                && (methodOrField.isProtected() || !methodOrField.isPublic());
    }

    /**
     * Tests if candidate method is overridden from a given class or subclass.
     *
     * @param overridden   the candidate overridden method
     * @param classElement the type element that may contain the overriding method, either directly or in a subclass
     * @param strict       Whether to use strict checks for overriding and not include logic to handle method overloading
     * @return the overriding method
     */
    public Optional<ExecutableElement> overridingOrHidingMethod(ExecutableElement overridden, TypeElement classElement, boolean strict) {
        List<ExecutableElement> methods = ElementFilter.methodsIn(elementUtils.getAllMembers(classElement));
        for (ExecutableElement method : methods) {
            if (strict) {
                if (elementUtils.overrides(method, overridden, classElement)) {
                    return Optional.of(method);
                }
            } else {
                if (!method.equals(overridden) &&
                        method.getSimpleName().equals(overridden.getSimpleName())) {
                    return Optional.of(method);
                }
            }

        }
        // might be looking for a package private & packages differ method in a superclass
        // that is not visible to the most concrete subclass, really!
        // e.g. see injectPackagePrivateMethod4() for SpareTire -> Tire -> RoundThing in Inject tck
        // check the superclass until we reach Object, then bail out with empty if necessary.
        TypeElement superClass = superClassFor(classElement);
        if (superClass != null && !isObjectClass(superClass)) {
            return overridingOrHidingMethod(overridden, superClass, strict);
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

    /**
     * The Java APT throws an internal exception {code com.sun.tools.javac.code.Symbol$CompletionFailure} if a class is missing from the classpath and {@link Element#getKind()} is called. This method
     * handles exceptions when calling the getKind() method to avoid this scenario and should be used instead of {@link Element#getKind()}.
     *
     * @param element The element
     * @param expected The expected kind
     * @return The kind if it is resolvable and matches the expected kind
     */
    public Optional<ElementKind> resolveKind(Element element, ElementKind expected) {
        final Optional<ElementKind> elementKind = resolveKind(element);
        if (elementKind.isPresent() && elementKind.get() == expected) {
            return elementKind;
        }
        return Optional.empty();
    }

    /**
     * The Java APT throws an internal exception {code com.sun.tools.javac.code.Symbol$CompletionFailure} if a class is missing from the classpath and {@link Element#getKind()} is called. This method
     * handles exceptions when calling the getKind() method to avoid this scenario and should be used instead of {@link Element#getKind()}.
     *
     * @param element The element
     * @return The kind if it is resolvable
     */
    public Optional<ElementKind> resolveKind(Element element) {
        if (element != null) {

            try {
                final ElementKind kind = element.getKind();
                return Optional.of(kind);
            } catch (Exception e) {
                // ignore and fall through to empty
            }
        }
        return Optional.empty();
    }
}
