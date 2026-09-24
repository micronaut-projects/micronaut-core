/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.python.processing;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.util.PythonAnnotationTypes;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.TypeDef;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Generates the {@code PythonFunctionalInterfaceProvider} of a compilation: the functional
 * interfaces found in the Java types the Python sources reference, which the Python host access
 * converts a callable to by arity so that a Java method overloaded on functional interfaces, or on
 * a functional interface and {@code Iterable}, is called with a plain lambda.
 * <p>
 * A referenced type is an imported Java type, a Java base of a Python class, or a Java type a
 * Python class or script names in a type hint (a parameter, a return type, an attribute). The
 * generator collects the referenced types that are functional interfaces, the functional interfaces
 * the accessible methods of a referenced type (inherited ones included) accept as parameters, and,
 * one level deep, the functional interfaces the methods of the types those methods return accept:
 * a factory returning a {@code KStream} makes the mappers of the stream known without an import of
 * them. JDK types are not walked, their functional interfaces are the standard ones the host access
 * knows. A functional interface is an interface annotated with {@link FunctionalInterface} or,
 * outside the JDK, an interface with a single abstract method: the JDK's unannotated single-method
 * interfaces ({@code Iterable}, {@code Comparable}, {@code AutoCloseable}) are collection or
 * resource types, not functional ones.
 * <p>
 * The generated class is a singleton bean and a service, like the generated target type mappings:
 * the host access of an application context takes the provider beans, a context bootstrapped from
 * a class loader loads the services. It is named by the hash of its entries, so the main and the
 * test sources of a project, compiled into the same package, register distinct providers when they
 * differ and the same one when they do not; the host access merges the providers it finds.
 *
 * @since 5.2.4
 */
@Internal
final class PythonFunctionalInterfaceProviderGenerator {

    static final String PROVIDER = "io.micronaut.context.python.PythonFunctionalInterfaceProvider";
    static final String CLASS_NAME_PREFIX = "$PythonFunctionalInterfaces$";

    private static final String ENTRY = PROVIDER + ".Entry";
    private static final String SINGLETON = "jakarta.inject.Singleton";
    private static final Set<String> OBJECT_METHODS = Set.of("equals", "hashCode", "toString");

    private final VisitorContext context;
    /** The entries by the name of the interface. */
    private final Map<String, Entry> entries = new TreeMap<>();
    /** The depth each type was walked at, so a type referenced twice is inspected once per depth needed. */
    private final Map<String, Integer> walked = new HashMap<>();
    /** The single abstract method of the interfaces inspected, {@code null} entries for other types. */
    private final Map<String, @Nullable MethodElement> functionalMethods = new HashMap<>();

    PythonFunctionalInterfaceProviderGenerator(VisitorContext context) {
        this.context = context;
    }

    /**
     * Records the functional interfaces of an imported Java type.
     *
     * @param className The binary name of the type
     */
    void referenceImport(String className) {
        try {
            context.getClassElement(className).ifPresent(type -> reference(type, 0));
        } catch (RuntimeException e) {
            // a type missing from the compile class path: see reference()
        }
    }

    /**
     * Records the functional interfaces of the Java types a Python class or script references:
     * its Java bases and the types of its methods and attributes.
     *
     * @param element The Python class or script element
     */
    void referencePythonType(ClassElement element) {
        element.getSuperType().ifPresent(superType -> reference(superType, 0));
        for (ClassElement anInterface : element.getInterfaces()) {
            reference(anInterface, 0);
        }
        try {
            for (MethodElement method : element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())) {
                referenceSignature(method, 0);
            }
            for (MethodElement constructor : element.getEnclosedElements(ElementQuery.CONSTRUCTORS)) {
                referenceSignature(constructor, 0);
            }
            for (FieldElement field : element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyDeclared())) {
                reference(field.getType(), 0);
            }
            for (PropertyElement property : element.getBeanProperties()) {
                reference(property.getType(), 0);
            }
        } catch (RuntimeException e) {
            // a member whose type names a class missing from the compile class path: see reference()
        }
    }

    /**
     * Writes the provider of the entries recorded so far and registers it as a service.
     *
     * @param originatingElement The element the compilation originates from
     * @return The name of the generated class, or {@code null} when no functional interface was found
     */
    @Nullable String generate(ClassElement originatingElement) {
        if (entries.isEmpty()) {
            return null;
        }
        ClassTypeDef entryType = ClassTypeDef.of(ENTRY);
        TypeDef listType = TypeDef.parameterized(ClassTypeDef.of(List.class), entryType);
        // the interfaces are named, not referenced: one of a compile-time only dependency is absent
        // at run time, and the host access skips what it cannot load
        List<ExpressionDef> values = entries.values().stream()
            .map(entry -> entryType.instantiate(
                ExpressionDef.constant(entry.name()),
                ExpressionDef.constant(entry.arity()),
                ExpressionDef.constant(entry.returnsValue())
            ))
            .map(ExpressionDef.class::cast)
            .toList();
        String className = originatingElement.getPackageName() + '.' + CLASS_NAME_PREFIX + contentHash();
        // a bean, like the generated target type mappings, for the host access of the application
        // context, and a service for a context bootstrapped from a class loader (not @Generated: the
        // bean definition processor skips generated classes)
        ClassDef classDef = ClassDef.builder(className)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Internal.class)
            .addAnnotation(ClassTypeDef.of(SINGLETON))
            .addSuperinterface(ClassTypeDef.of(PROVIDER))
            .addMethod(MethodDef.builder("entries")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(listType)
                .build((aThis, params) -> ClassTypeDef.of(List.class).invokeStatic("of", listType, values).returning()))
            .build();
        SourceGenerators.findByLanguage(VisitorContext.Language.JAVA)
            .ifPresent(generator -> generator.write(classDef, context, originatingElement));
        context.visitServiceDescriptor(PROVIDER, className, originatingElement);
        return className;
    }

    /**
     * The functional interfaces recorded so far, by binary name.
     *
     * @return The entries
     */
    Map<String, Entry> entries() {
        return entries;
    }

    private void referenceSignature(MethodElement method, int depth) {
        for (ParameterElement parameter : method.getParameters()) {
            reference(parameter.getType(), depth);
        }
        reference(method.getReturnType(), depth);
    }

    /**
     * Records a referenced type: the type itself when it is a functional interface, the functional
     * interfaces its methods accept, and at depth 0 the ones the methods of its return types accept.
     */
    private void reference(@Nullable ClassElement referenced, int depth) {
        try {
            ClassElement type = rawType(referenced);
            if (type == null) {
                return;
            }
            Integer walkedDepth = walked.get(type.getName());
            if (walkedDepth != null && walkedDepth <= depth) {
                return;
            }
            walked.put(type.getName(), depth);
            if (isFunctionalInterface(type)) {
                recordEntry(type);
            }
            if (isJdkType(type.getName())) {
                return;
            }
            for (MethodElement method : type.getEnclosedElements(ElementQuery.ALL_METHODS.onlyAccessible())) {
                for (ParameterElement parameter : method.getParameters()) {
                    ClassElement parameterType = rawType(parameter.getType());
                    if (parameterType != null && isFunctionalInterface(parameterType)) {
                        recordEntry(parameterType);
                    }
                }
                if (depth == 0) {
                    reference(method.getReturnType(), 1);
                }
            }
        } catch (RuntimeException e) {
            // a type whose signatures name a class missing from the compile class path cannot be
            // inspected (javac reports the completion failure as a runtime exception): its functional
            // interfaces stay unknown, like those of a type the sources do not reference
        }
    }

    private void recordEntry(ClassElement type) {
        if (!isAccessible(type)) {
            return;
        }
        MethodElement method = functionalMethod(type);
        if (method == null) {
            return;
        }
        entries.putIfAbsent(type.getName(), new Entry(
            type.getName(),
            method.getParameters().length,
            !method.getReturnType().isVoid()
        ));
    }

    /**
     * Whether the type is a functional interface: an interface annotated with {@link FunctionalInterface}
     * or, outside the JDK, an interface with a single abstract method.
     */
    boolean isFunctionalInterface(ClassElement type) {
        return functionalMethod(type) != null
            && (type.hasAnnotation(FunctionalInterface.class) || !isJdkType(type.getName()));
    }

    /**
     * The single abstract method of an interface, computed once per type.
     */
    private @Nullable MethodElement functionalMethod(ClassElement type) {
        return functionalMethods.computeIfAbsent(type.getName(), ignored -> findFunctionalMethod(type));
    }

    private @Nullable MethodElement findFunctionalMethod(ClassElement type) {
        if (!type.isInterface() || PythonAnnotationTypes.isAnnotationType(type)) {
            return null;
        }
        MethodElement found = null;
        for (MethodElement method : type.getEnclosedElements(ElementQuery.ALL_METHODS.onlyAbstract())) {
            if (isObjectMethod(method)) {
                continue;
            }
            if (found != null && !(found.getName().equals(method.getName())
                && found.getParameters().length == method.getParameters().length)) {
                return null;
            }
            if (found == null) {
                found = method;
            }
        }
        return found;
    }

    private static boolean isObjectMethod(MethodElement method) {
        String name = method.getName();
        int parameters = method.getParameters().length;
        return OBJECT_METHODS.contains(name) && (name.equals("equals") ? parameters == 1 : parameters == 0);
    }

    /**
     * The class of a type reference: the raw class of a parameterized type, the bound of a type
     * variable; {@code null} for a primitive, an array or {@code void}.
     */
    private static @Nullable ClassElement rawType(@Nullable ClassElement type) {
        if (type == null || type.isPrimitive() || type.isArray() || type.isVoid()) {
            return null;
        }
        ClassElement raw = type.isGenericPlaceholder() || type.isWildcard() || !type.getTypeArguments().isEmpty()
            ? type.getRawClassElement()
            : type;
        return raw.isPrimitive() || raw.isArray() ? null : raw;
    }

    /**
     * Whether the host access can use the type: a public type whose enclosing types are public.
     */
    private static boolean isAccessible(ClassElement type) {
        ClassElement current = type;
        while (current != null) {
            if (!current.isPublic()) {
                return false;
            }
            current = current.getEnclosingType().orElse(null);
        }
        return true;
    }

    private static boolean isJdkType(String name) {
        return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.") || name.startsWith("sun.");
    }

    private String contentHash() {
        StringBuilder content = new StringBuilder();
        for (Entry entry : entries.values()) {
            content.append(entry.name()).append(':').append(entry.arity()).append(':').append(entry.returnsValue()).append('\n');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A functional interface.
     *
     * @param name The binary name of the interface
     * @param arity The number of parameters of its single abstract method
     * @param returnsValue Whether the single abstract method returns a value
     */
    record Entry(String name, int arity, boolean returnsValue) {
    }
}
