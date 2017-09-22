package org.particleframework.annotation.processing;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;
import java.util.stream.Collectors;

import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.Modifier.*;
import static javax.lang.model.type.TypeKind.ARRAY;
import static javax.lang.model.type.TypeKind.NONE;
import static javax.lang.model.type.TypeKind.VOID;

class ModelUtils {
    private final Elements elementUtils;
    private final Types typeUtils;

    ModelUtils(Elements elementUtils,Types typeUtils) {
        this.elementUtils = elementUtils;
        this.typeUtils = typeUtils;
    }

    TypeElement classElementFor(Element element) {
        while (!(element.getKind().isClass() || element.getKind().isInterface())) {
            element = element.getEnclosingElement();
        }
        return (TypeElement) element;
    }

    String simpleBinaryNameFor(TypeElement typeElement) {
        Name elementBinaryName = elementUtils.getBinaryName(typeElement);
        PackageElement packageElement = elementUtils.getPackageOf(typeElement);

        String packageName = packageElement.getQualifiedName().toString();
        return elementBinaryName.toString().replaceFirst(packageName + "\\.","");
    }

    Optional<ExecutableElement> findSetterMethodFor(Element field) {
        String name = field.getSimpleName().toString();
        name = name.replaceFirst("^(is).+", "");
        String setterName = setterNameFor(name);
        // FIXME refine this to discover one of possible overloaded methods with correct signature (i.e. single arg of field type)
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

    List<ExecutableElement> findPublicConstructors(TypeElement classElement) {
        List<ExecutableElement> ctors =
            ElementFilter.constructorsIn(classElement.getEnclosedElements());
        return ctors.stream()
            .filter(ctor -> ctor.getModifiers().contains(PUBLIC))
            .collect(Collectors.toList());
    }

    // FIXME test for requires reflection (private qualifier is just one aspect)
    // e.g. see InjectTransform requiresReflection = isPrivate || isPackagePrivateAndPackagesDiffer
    boolean requiresReflection(Element element) {
        Set<Modifier> modifiers = element.getModifiers();
        boolean requiresReflection = isPrivate(element);
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
                    throw new IllegalArgumentException("primitiveType cannot be " + primitiveType);
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    TypeElement superClassFor(TypeElement element) {
        TypeMirror superclass = element.getSuperclass();
        if (superclass.getKind() == TypeKind.NONE) {
            return null;
        }
        DeclaredType kind = (DeclaredType) superclass;
        return (TypeElement) kind.asElement();
    }

    List<TypeElement> superClassesFor(TypeElement classElement) {
        List<TypeElement> superClasses = new ArrayList<>();
        TypeElement superclass = superClassFor(classElement);
                while (superclass != null) {
            superClasses.add(superclass);
            superclass = superClassFor(superclass);
        }
                Collections.reverse(superClasses);
        return superClasses;
    }

    Object resolveTypeReference(TypeElement typeElement) {
        TypeMirror type = typeElement.asType();
        return resolveTypeReference(type);
    }

    boolean isObjectClass(TypeElement element) {
        return element.getSuperclass().getKind() == NONE;
    }

    Object resolveTypeReference(TypeMirror type) {
        Object result = Void.TYPE;
        if (type.getKind().isPrimitive()) {
            result = classOfPrimitiveFor(type.toString());
        } else if (type.getKind() == ARRAY) {
            ArrayType arrayType = (ArrayType) type;
            TypeMirror componentType = arrayType.getComponentType();
            if (componentType.getKind().isPrimitive()) {
                result = classOfPrimitiveArrayFor(componentType.toString());
            } else {
                result = arrayType.toString();
            }
        } else if (type.getKind() != VOID) {
            TypeElement typeElement = elementUtils.getTypeElement(typeUtils.erasure(type).toString());
            Name qualifiedName = typeElement.getQualifiedName();
            NestingKind nestingKind = typeElement.getNestingKind();
            if( nestingKind == NestingKind.MEMBER) {
                TypeElement enclosingElement = typeElement;
                StringBuilder builder = new StringBuilder();
                while(nestingKind == NestingKind.MEMBER) {
                    enclosingElement = (TypeElement) typeElement.getEnclosingElement();
                    nestingKind = enclosingElement.getNestingKind();
                    builder.insert(0,'$').insert(1,typeElement.getSimpleName());
                }
                Name enclosingName = enclosingElement.getQualifiedName();
                result = enclosingName.toString() + builder;
            }
            else {
                result = qualifiedName.toString();
            }
        }
        return result;
    }

    boolean isPackagePrivate(Element element) {
        Set<Modifier> modifiers = element.getModifiers();
        return !(modifiers.contains(PUBLIC)
            || modifiers.contains(PROTECTED)
            || modifiers.contains(PRIVATE));
    }


    // FIXME review/test this
    boolean isInheritedAndNotPublic(TypeElement concreteClass, TypeElement declaringClass, ExecutableElement method) {
        PackageElement packageOfDeclaringClass = elementUtils.getPackageOf(declaringClass);
        PackageElement packageOfConcreteClass = elementUtils.getPackageOf(concreteClass);

        return declaringClass != concreteClass &&
            !packageOfDeclaringClass.getQualifiedName().equals(packageOfConcreteClass.getQualifiedName())
            && (isProtected(method) || !isPublic(method));
    }

    /**
     * Tests if candidate method is overriden from a given class or subclass
     *
     * @param overridden the candidate overridden method
     * @param classElement the type element that may contain the overriding method, either directly or in a subclass
     * @return the overriding method
     */
    Optional<ExecutableElement> overridingMethod(ExecutableElement overridden, TypeElement classElement) {
        List<ExecutableElement> methods = ElementFilter.methodsIn(elementUtils.getAllMembers(classElement));
        for (ExecutableElement method: methods) {
            if (!method.equals(overridden)) {
                if (elementUtils.overrides(method, overridden, classElement)) {
                    return Optional.ofNullable(method);
                }
            }
        }
        return Optional.empty();
    }

    boolean isPrivate(Element element) {
        return element.getModifiers().contains(PRIVATE);
    }

    boolean isProtected(Element element) {
        return element.getModifiers().contains(PROTECTED);
    }

    boolean isPublic(Element element) {
        return element.getModifiers().contains(PUBLIC);
    }

    boolean isAbstract(Element element) {
        return element.getModifiers().contains(ABSTRACT);
    }

    boolean isStatic(Element element) {
        return element.getModifiers().contains(STATIC);
    }

    public Object[] resolveTypeReferences(AnnotationMirror[] mirrors) {
        return Arrays.stream(mirrors)
              .map(mirror ->
                resolveTypeReference(mirror.getAnnotationType())
              ).toArray(Object[]::new);
    }
}
