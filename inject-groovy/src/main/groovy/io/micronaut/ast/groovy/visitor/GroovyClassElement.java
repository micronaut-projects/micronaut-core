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
package io.micronaut.ast.groovy.visitor;

import groovy.lang.GroovyObject;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.Script;
import io.micronaut.ast.groovy.utils.AstClassUtils;
import io.micronaut.ast.groovy.utils.AstGenericUtils;
import io.micronaut.context.annotation.BeanProperties;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ArrayableClassElement;
import io.micronaut.inject.ast.BeanPropertiesConfiguration;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PackageElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.utils.AstBeanPropertiesUtils;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.InnerClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.PackageNode;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static groovyjarjarasm.asm.Opcodes.ACC_PRIVATE;
import static groovyjarjarasm.asm.Opcodes.ACC_PROTECTED;
import static groovyjarjarasm.asm.Opcodes.ACC_PUBLIC;

/**
 * A class element returning data from a {@link ClassNode}.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Internal
public class GroovyClassElement extends AbstractGroovyElement implements ArrayableClassElement {

    private static final Predicate<MethodNode> JUNK_METHOD_FILTER = m -> {
        String methodName = m.getName();

        return m.isStaticConstructor() ||
            methodName.startsWith("$") ||
            methodName.contains("trait$") ||
            methodName.startsWith("super$") ||
            methodName.equals("setMetaClass") ||
            m.getReturnType().getNameWithoutPackage().equals("MetaClass") ||
            m.getDeclaringClass().equals(ClassHelper.GROOVY_OBJECT_TYPE) ||
            m.getDeclaringClass().equals(ClassHelper.OBJECT_TYPE);
    };
    private static final Predicate<FieldNode> JUNK_FIELD_FILTER = m -> {
        String fieldName = m.getName();

        return fieldName.startsWith("$") ||
            fieldName.startsWith("__$") ||
            fieldName.contains("trait$") ||
            fieldName.equals("metaClass") ||
            m.getDeclaringClass().equals(ClassHelper.GROOVY_OBJECT_TYPE) ||
            m.getDeclaringClass().equals(ClassHelper.OBJECT_TYPE);
    };
    protected final ClassNode classNode;
    private final int arrayDimensions;
    private final boolean isTypeVar;
    private List<? extends ClassElement> overrideBoundGenericTypes;
    private Map<String, Map<String, ClassNode>> genericInfo;
    private List<PropertyElement> properties;
    private List<PropertyElement> nativeProperties;
    private Map<AnnotatedNode, Element> elementsMap = new HashMap<>();
    private Map<String, ClassElement> resolvedTypeArguments;

    /**
     * @param visitorContext            The visitor context
     * @param classNode                 The {@link ClassNode}
     * @param annotationMetadataFactory The annotation metadata
     */
    public GroovyClassElement(GroovyVisitorContext visitorContext,
                              ClassNode classNode,
                              ElementAnnotationMetadataFactory annotationMetadataFactory) {
        this(visitorContext, classNode, annotationMetadataFactory, null, 0);
    }

    /**
     * @param visitorContext            The visitor context
     * @param classNode                 The {@link ClassNode}
     * @param annotationMetadataFactory The annotation metadata factory
     * @param genericInfo               The generic info
     * @param arrayDimensions           The number of array dimensions
     */
    GroovyClassElement(
        GroovyVisitorContext visitorContext,
        ClassNode classNode,
        ElementAnnotationMetadataFactory annotationMetadataFactory,
        Map<String, Map<String, ClassNode>> genericInfo,
        int arrayDimensions) {
        this(visitorContext, classNode, annotationMetadataFactory, genericInfo, arrayDimensions, false);
    }

    /**
     * @param visitorContext            The visitor context
     * @param classNode                 The {@link ClassNode}
     * @param annotationMetadataFactory The annotation metadata factory
     * @param genericInfo               The generic info
     * @param arrayDimensions           The number of array dimensions
     * @param isTypeVar                 Is the element a type variable
     */
    GroovyClassElement(
        GroovyVisitorContext visitorContext,
        ClassNode classNode,
        ElementAnnotationMetadataFactory annotationMetadataFactory,
        Map<String, Map<String, ClassNode>> genericInfo,
        int arrayDimensions,
        boolean isTypeVar) {
        super(visitorContext, classNode, annotationMetadataFactory);
        this.classNode = classNode;
        this.genericInfo = genericInfo;
        this.arrayDimensions = arrayDimensions;
        if (classNode.isArray()) {
            classNode.setName(classNode.getComponentType().getName());
        }
        this.isTypeVar = isTypeVar;
    }

    @Override
    protected GroovyClassElement copyThis() {
        return new GroovyClassElement(visitorContext, classNode, elementAnnotationMetadataFactory, genericInfo, arrayDimensions, isTypeVar);
    }

    @Override
    protected void copyValues(AbstractGroovyElement element) {
        super.copyValues(element);
        ((GroovyClassElement) element).resolvedTypeArguments = resolvedTypeArguments;
    }

    @Override
    public ClassElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (ClassElement) super.withAnnotationMetadata(annotationMetadata);
    }

    @Override
    public ClassElement withTypeArguments(Map<String, ClassElement> typeArguments) {
        GroovyClassElement groovyClassElement = new GroovyClassElement(visitorContext, classNode, elementAnnotationMetadataFactory, genericInfo, arrayDimensions);
        groovyClassElement.resolvedTypeArguments = typeArguments;
        return groovyClassElement;
    }

    @Override
    public boolean isTypeVariable() {
        return isTypeVar;
    }

    @Override
    public <T extends Element> List<T> getEnclosedElements(@NonNull ElementQuery<T> query) {
        return getEnclosedElements(query, false);
    }

    public final <T extends Element> List<T> getEnclosedElements(@NonNull ElementQuery<T> query, boolean isSource) {
        Objects.requireNonNull(query, "Query cannot be null");
        ElementQuery.Result<T> result = query.result();
        boolean onlyDeclared = result.isOnlyDeclared();
        boolean onlyAccessible = result.isOnlyAccessible();
        boolean onlyAbstract = result.isOnlyAbstract();
        boolean onlyConcrete = result.isOnlyConcrete();
        boolean onlyInstance = result.isOnlyInstance();
        boolean onlyStatic = result.isOnlyStatic();
        boolean excludePropertyElements = result.isExcludePropertyElements();
        Set<AnnotatedNode> excludeMethodNodes;
        Set<AnnotatedNode> excludeFieldNodes;
        if (excludePropertyElements) {
            excludeMethodNodes = new HashSet<>();
            excludeFieldNodes = new HashSet<>();
            for (PropertyElement excludePropertyElement : getBeanProperties()) {
                excludePropertyElement.getReadMethod()
                    .filter(m -> !m.isSynthetic())
                    .ifPresent(methodElement -> excludeMethodNodes.add((AnnotatedNode) methodElement.getNativeType()));
                excludePropertyElement.getWriteMethod()
                    .filter(m -> !m.isSynthetic())
                    .ifPresent(methodElement -> excludeMethodNodes.add((AnnotatedNode) methodElement.getNativeType()));
                excludePropertyElement.getField().ifPresent(fieldElement -> excludeFieldNodes.add((AnnotatedNode) fieldElement.getNativeType()));
            }
        } else {
            excludeMethodNodes = Collections.emptySet();
            excludeFieldNodes = Collections.emptySet();
        }

        List<Predicate<String>> namePredicates = result.getNamePredicates();
        List<Predicate<ClassElement>> typePredicates = result.getTypePredicates();
        List<Predicate<AnnotationMetadata>> annotationPredicates = result.getAnnotationPredicates();
        List<Predicate<T>> elementPredicates = result.getElementPredicates();
        List<Predicate<Set<ElementModifier>>> modifierPredicates = result.getModifierPredicates();
        List<T> elements;
        Class<T> elementType = result.getElementType();
        if (elementType == MethodElement.class) {
            Predicate<MethodNode> methodNodePredicate = methodNode -> {
                for (Predicate<String> predicate : namePredicates) {
                    if (!predicate.test(methodNode.getName())) {
                        return false;
                    }
                }
                return !JUNK_METHOD_FILTER.test(methodNode);
            };
            List<MethodElement> methods;
            if (onlyDeclared) {
                methods = classNode.getMethods().stream().filter(methodNodePredicate).map(mn -> toMethodElement(mn, isSource)).collect(Collectors.toList());
            } else {
                methods = new ArrayList<>(getAllMethods(classNode, methodNodePredicate, result.isIncludeOverriddenMethods(), isSource));
            }

            Iterator<MethodElement> i = methods.iterator();
            while (i.hasNext()) {
                MethodElement method = i.next();
                if (onlyAbstract && !method.isAbstract()) {
                    i.remove();
                    continue;
                }
                if (onlyConcrete && method.isAbstract()) {
                    i.remove();
                    continue;
                }
                if (onlyInstance && method.isStatic()) {
                    i.remove();
                    continue;
                }
                if (onlyStatic && !method.isStatic()) {
                    i.remove();
                    continue;
                }
                if (onlyAccessible) {
                    final ClassElement accessibleFromType = result.getOnlyAccessibleFromType().orElse(this);
                    if (!method.isAccessible(accessibleFromType)) {
                        i.remove();
                        continue;
                    }
                }
                if (!modifierPredicates.isEmpty()) {
                    Set<ElementModifier> elementModifiers = method.getModifiers();
                    if (!modifierPredicates.stream().allMatch(p -> p.test(elementModifiers))) {
                        i.remove();
                        continue;
                    }
                }
                if (excludeMethodNodes.contains(method.getNativeType())) {
                    i.remove();
                }
            }
            if (!typePredicates.isEmpty()) {
                methods.removeIf(e -> !typePredicates.stream().allMatch(p -> p.test(e.getGenericReturnType())));
            }
            elements = (List<T>) methods;
        } else if (elementType == ConstructorElement.class) {
            List<ConstructorNode> constructors = new ArrayList<>(classNode.getDeclaredConstructors());
            if (!onlyDeclared) {
                ClassNode superClass = classNode.getSuperClass();
                while (superClass != null) {
                    // don't include constructors on enum, record... – matches behavior of JavaClassElement
                    if (superClass.getPackageName().equals("java.lang")) {
                        break;
                    }
                    constructors.addAll(superClass.getDeclaredConstructors());
                    superClass = superClass.getSuperClass();
                }
            }
            for (Iterator<ConstructorNode> i = constructors.iterator(); i.hasNext(); ) {
                ConstructorNode constructor = i.next();
                // we don't listen to the user here, we never return static initializers. This matches behavior of JavaClassElement
                if (constructor.isStatic()) {
                    i.remove();
                    continue;
                }
                if (onlyAccessible) {
                    final ClassElement accessibleFromType = result.getOnlyAccessibleFromType().orElse(this);
                    if (constructor.isPrivate()) {
                        i.remove();
                        continue;
                    } else if (!constructor.getDeclaringClass().getName().equals(accessibleFromType.getName())) {
                        // inaccessible through package scope
                        if (constructor.isPackageScope() && !constructor.getDeclaringClass().getPackageName().equals(accessibleFromType.getPackageName())) {
                            i.remove();
                            continue;
                        }
                    }
                }
                if (!modifierPredicates.isEmpty()) {
                    Set<ElementModifier> elementModifiers = resolveModifiers(constructor);
                    if (!modifierPredicates.stream().allMatch(p -> p.test(elementModifiers))) {
                        i.remove();
                    }
                }
            }

            //noinspection unchecked
            elements = constructors.stream()
                .map(constructorNode -> (T) asConstructor(constructorNode))
                .collect(Collectors.toList());
        } else if (elementType == FieldElement.class) {
            List<FieldNode> fields;
            if (onlyDeclared) {
                List<FieldNode> initialFields = classNode.getFields();
                fields = findRelevantFields(onlyAccessible, result.getOnlyAccessibleFromType().orElse(this), initialFields, namePredicates, modifierPredicates);
            } else {
                fields = new ArrayList<>(classNode.getFields());
                ClassNode superClass = classNode.getSuperClass();
                while (superClass != null && !superClass.equals(ClassHelper.OBJECT_TYPE)) {
                    fields.addAll(superClass.getFields());
                    superClass = superClass.getSuperClass();
                }
                fields = findRelevantFields(onlyAccessible, result.getOnlyAccessibleFromType().orElse(this), fields, namePredicates, modifierPredicates);
            }
            Stream<FieldNode> fieldStream = fields.stream().filter(f -> !excludeFieldNodes.contains(f));
            if (onlyInstance) {
                fieldStream = fieldStream.filter((fn) -> !fn.isStatic());
            } else if (onlyStatic) {
                fieldStream = fieldStream.filter(FieldNode::isStatic);
            }
            elements = fieldStream
                .map(fieldNode -> (T) elementsMap.computeIfAbsent(fieldNode, annotatedNode -> visitorContext.getElementFactory().newFieldElement(this, fieldNode, elementAnnotationMetadataFactory)))
                .collect(Collectors.toList());
            if (!typePredicates.isEmpty()) {
                elements.removeIf(e -> !typePredicates.stream().allMatch(p -> p.test(((FieldElement) e).getGenericField())));
            }
        } else if (elementType == ClassElement.class) {
            Iterator<InnerClassNode> i = classNode.getInnerClasses();
            List<T> innerClasses = new ArrayList<>();
            while (i.hasNext()) {
                InnerClassNode innerClassNode = i.next();
                if (onlyAbstract && !innerClassNode.isAbstract()) {
                    continue;
                }
                if (onlyConcrete && innerClassNode.isAbstract()) {
                    continue;
                }
                if (onlyAccessible) {
                    if (Modifier.isPrivate(innerClassNode.getModifiers())) {
                        continue;
                    }
                }
                if (!modifierPredicates.isEmpty()) {
                    Set<ElementModifier> elementModifiers = resolveModifiers(innerClassNode);
                    if (!modifierPredicates.stream().allMatch(p -> p.test(elementModifiers))) {
                        continue;
                    }
                }
                if (!namePredicates.isEmpty()) {
                    if (!namePredicates.stream().allMatch(p -> p.test(innerClassNode.getName()))) {
                        continue;
                    }
                }
                ClassElement classElement = visitorContext.getElementFactory().newClassElement(innerClassNode, elementAnnotationMetadataFactory);
                if (!typePredicates.isEmpty()) {
                    if (!typePredicates.stream().allMatch(p -> p.test(classElement))) {
                        continue;
                    }
                }

                innerClasses.add((T) classElement);
            }
            elements = innerClasses;
        } else {
            elements = Collections.emptyList();
        }
        if (!elements.isEmpty()) {
            if (!annotationPredicates.isEmpty()) {
                elements.removeIf(e -> !annotationPredicates.stream().allMatch(p -> p.test(e.getAnnotationMetadata())));
            }
            if (!elements.isEmpty() && !elementPredicates.isEmpty()) {
                elements.removeIf(e -> !elementPredicates.stream().allMatch(p -> p.test(e)));
            }
        }
        return elements;
    }

    private List<FieldNode> findRelevantFields(
        boolean onlyAccessible,
        ClassElement onlyAccessibleType,
        List<FieldNode> initialFields,
        List<Predicate<String>> namePredicates,
        List<Predicate<Set<ElementModifier>>> modifierPredicates) {
        List<FieldNode> filteredFields = new ArrayList<>(initialFields.size());

        elementLoop:
        for (FieldNode fn : initialFields) {
            if (JUNK_FIELD_FILTER.test(fn)) {
                continue;
            }
            if (onlyAccessible && fn.isPrivate()) {
                continue;
            } else if (onlyAccessible && isPackageScope(fn)) {
                if (!fn.getDeclaringClass().getPackageName().equals(onlyAccessibleType.getPackageName())) {
                    continue;
                }
            }
            if (!modifierPredicates.isEmpty()) {
                final Set<ElementModifier> elementModifiers = resolveModifiers(fn);
                for (Predicate<Set<ElementModifier>> modifierPredicate : modifierPredicates) {
                    if (!modifierPredicate.test(elementModifiers)) {
                        continue elementLoop;
                    }
                }
            }
            if (!namePredicates.isEmpty()) {
                String name = fn.getName();
                for (Predicate<String> namePredicate : namePredicates) {
                    if (!namePredicate.test(name)) {
                        continue elementLoop;
                    }
                }
            }
            filteredFields.add(fn);
        }
        return filteredFields;
    }

    private Collection<MethodElement> getAllMethods(ClassNode classNode,
                                                    Predicate<MethodNode> methodNodePredicate,
                                                    boolean includeOverriddenMethods,
                                                    boolean isSource) {
        // This method will return private/package private methods that
        // cannot be overridden by defining a method with the same signature
        Set<MethodElement> methods = new LinkedHashSet<>();
        Map<MethodNode, MethodElement> methodElements = new HashMap<>();
        List<List<MethodNode>> hierarchy = new ArrayList<>();
        collectHierarchyMethods(classNode, methodNodePredicate, hierarchy);
        for (List<MethodNode> classMethods : hierarchy) {
            Set<MethodElement> addedFromClassMethods = new LinkedHashSet<>();
            for (MethodNode methodNode : classMethods) {
                MethodElement newMethod = methodElements.computeIfAbsent(methodNode, mn -> toMethodElement(mn, isSource));
                for (Iterator<MethodElement> iterator = methods.iterator(); iterator.hasNext(); ) {
                    MethodElement existingMethod = iterator.next();
                    if (!includeOverriddenMethods && newMethod.overrides(existingMethod)) {
                        iterator.remove();
                        addedFromClassMethods.add(newMethod);
                    }
                }
                addedFromClassMethods.add(newMethod);
            }
            methods.addAll(addedFromClassMethods);
        }
        return methods;
    }

    private static void collectHierarchyMethods(ClassNode classNode,
                                                Predicate<MethodNode> methodNodePredicate,
                                                List<List<MethodNode>> hierarchy) {
        if (Object.class.getName().equals(classNode.getName())
            || Enum.class.getName().equals(classNode.getName())
            || GroovyObjectSupport.class.getName().equals(classNode.getName())
            || Script.class.getName().equals(classNode.getName())) {
            return;
        }
        ClassNode parent = classNode.getSuperClass();
        if (parent != null) {
            collectHierarchyMethods(parent, methodNodePredicate, hierarchy);
        }
        for (ClassNode iface : classNode.getInterfaces()) {
            if (iface.getName().equals(GroovyObject.class.getName())) {
                continue;
            }
            List<List<MethodNode>> interfaceMethods = new ArrayList<>();
            collectHierarchyMethods(iface, methodNodePredicate, interfaceMethods);
            interfaceMethods.forEach(methodNodes -> methodNodes.removeIf(methodNode -> (methodNode.getModifiers() & Opcodes.ACC_SYNTHETIC) != 0));
            hierarchy.addAll(interfaceMethods);
        }
        hierarchy.add(classNode.getMethods().stream().filter(methodNodePredicate).collect(Collectors.toList()));
    }

    private GroovyMethodElement toMethodElement(MethodNode methodNode, boolean isSource) {
        return (GroovyMethodElement) elementsMap.computeIfAbsent(methodNode, annotatedNode -> {
            if (isSource) {
                return visitorContext.getElementFactory().newSourceMethodElement(this, methodNode, elementAnnotationMetadataFactory);
            }
            return visitorContext.getElementFactory().newMethodElement(this, methodNode, elementAnnotationMetadataFactory);
        });
    }

    private boolean isPackageScope(FieldNode fn) {
        return (fn.getModifiers() & (ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED)) == 0;
    }

    @Override
    public Set<ElementModifier> getModifiers() {
        return resolveModifiers(this.classNode);
    }

    @Override
    public boolean isInner() {
        return classNode instanceof InnerClassNode;
    }

    @Override
    public Optional<ClassElement> getEnclosingType() {
        if (isInner()) {
            return Optional.ofNullable(classNode.getOuterClass()).map(this::toGroovyClassElement);
        }
        return Optional.empty();
    }

    @Override
    public boolean isInterface() {
        return classNode.isInterface();
    }

    @Override
    public boolean isPrimitive() {
        return classNode.isArray() && ClassUtils.getPrimitiveType(classNode.getComponentType().getName()).isPresent();
    }

    @Override
    public Collection<ClassElement> getInterfaces() {
        final ClassNode[] interfaces = classNode.getInterfaces();
        if (ArrayUtils.isNotEmpty(interfaces)) {
            return Arrays.stream(interfaces).map(this::toGroovyClassElement).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public Optional<ClassElement> getSuperType() {
        final ClassNode superClass = classNode.getUnresolvedSuperClass(false);
        if (superClass != null && !superClass.equals(ClassHelper.OBJECT_TYPE)) {
            return Optional.of(
                toGroovyClassElement(superClass)
            );
        }
        return Optional.empty();
    }

    private ClassElement toGroovyClassElement(ClassNode superClass) {
        return visitorContext.getElementFactory().newClassElement(superClass, elementAnnotationMetadataFactory);
    }

    @NonNull
    @Override
    public Optional<MethodElement> getPrimaryConstructor() {
        Optional<MethodElement> primaryConstructor = ArrayableClassElement.super.getPrimaryConstructor();
        if (primaryConstructor.isPresent()) {
            return primaryConstructor;
        }
        return possibleDefaultEmptyConstructor();
    }

    @Override
    public Optional<MethodElement> getDefaultConstructor() {
        Optional<MethodElement> defaultConstructor = ArrayableClassElement.super.getDefaultConstructor();
        if (defaultConstructor.isPresent()) {
            return defaultConstructor;
        }
        return possibleDefaultEmptyConstructor();
    }

    private Optional<MethodElement> possibleDefaultEmptyConstructor() {
        List<ConstructorNode> constructors = classNode.getDeclaredConstructors();
        if (CollectionUtils.isEmpty(constructors) && !classNode.isAbstract() && !classNode.isEnum()) {
            // empty default constructor
            return createMethodElement(new ConstructorNode(Modifier.PUBLIC, new BlockStatement()));
        }
        return Optional.empty();
    }

    private Optional<MethodElement> createMethodElement(MethodNode method) {
        return Optional.ofNullable(method).map(executableElement -> {
            if (executableElement instanceof ConstructorNode) {
                return visitorContext.getElementFactory().newConstructorElement(this, executableElement, elementAnnotationMetadataFactory);
            } else {
                return visitorContext.getElementFactory().newMethodElement(this, executableElement, elementAnnotationMetadataFactory);
            }
        });
    }

    /**
     * Builds and returns the generic type information.
     *
     * @return The generic type info
     */
    public Map<String, Map<String, ClassNode>> getGenericTypeInfo() {
        if (genericInfo == null) {
            genericInfo = AstGenericUtils.buildAllGenericElementInfo(classNode, new GroovyVisitorContext(sourceUnit, compilationUnit));
        }
        return genericInfo;
    }

    @NonNull
    @Override
    public Map<String, ClassElement> getTypeArguments(@NonNull String type) {
        Map<String, Map<String, ClassNode>> allData = getGenericTypeInfo();
        Map<String, ClassNode> thisSpec = allData.get(getName());
        Map<String, ClassNode> forType = allData.get(type);
        if (forType != null) {
            Map<String, ClassElement> typeArgs = new LinkedHashMap<>(forType.size());
            for (Map.Entry<String, ClassNode> entry : forType.entrySet()) {
                ClassNode classNode = entry.getValue();

                ClassElement rawElement = new GroovyClassElement(visitorContext, classNode, resolveElementAnnotationMetadataFactory(classNode));
                if (thisSpec != null) {
                    rawElement = getGenericElement(sourceUnit, classNode, rawElement, thisSpec);
                }
                typeArgs.put(entry.getKey(), rawElement);
            }
            return Collections.unmodifiableMap(typeArgs);
        }
        return Collections.emptyMap();
    }

    @NonNull
    @Override
    public Map<String, Map<String, ClassElement>> getAllTypeArguments() {
        Map<String, Map<String, ClassNode>> genericInfo =
            AstGenericUtils.buildAllGenericElementInfo(classNode, new GroovyVisitorContext(sourceUnit, compilationUnit));
        Map<String, Map<String, ClassElement>> results = new LinkedHashMap<>(genericInfo.size());

        genericInfo.forEach((name, generics) -> {
            Map<String, ClassElement> resolved = new LinkedHashMap<>(generics.size());
            generics.forEach((variable, type) -> resolved.put(variable, new GroovyClassElement(visitorContext, type, resolveElementAnnotationMetadataFactory(classNode))));
            results.put(name, resolved);
        });
        results.put(getName(), getTypeArguments());
        return results;
    }

    @Override
    @NonNull
    public Map<String, ClassElement> getTypeArguments() {
        if (resolvedTypeArguments == null) {
            Map<String, Map<String, ClassNode>> genericInfo = getGenericTypeInfo();
            Map<String, ClassNode> info = genericInfo.get(classNode.getName());
            resolvedTypeArguments = resolveGenericMap(info);
        }
        return resolvedTypeArguments;
    }

    @NonNull
    private Map<String, ClassElement> resolveGenericMap(Map<String, ClassNode> info) {
        if (info != null) {
            Map<String, ClassElement> typeArgumentMap = new LinkedHashMap<>(info.size());
            GenericsType[] genericsTypes = classNode.getGenericsTypes();
            GenericsType[] redirectTypes = classNode.redirect().getGenericsTypes();
            if (genericsTypes != null && redirectTypes != null && genericsTypes.length == redirectTypes.length) {
                for (int i = 0; i < genericsTypes.length; i++) {
                    GenericsType gt = genericsTypes[i];
                    GenericsType redirectType = redirectTypes[i];
                    if (gt.isPlaceholder()) {
                        ClassNode cn = resolveTypeArgument(info, redirectType.getName());
                        if (cn != null) {
                            Map<String, ClassNode> newInfo = alignNewGenericsInfo(genericsTypes, redirectTypes, info);
                            typeArgumentMap.put(redirectType.getName(), new GroovyClassElement(
                                visitorContext,
                                cn,
                                resolveElementAnnotationMetadataFactory(cn),
                                Collections.singletonMap(cn.getName(), newInfo),
                                cn.isArray() ? computeDimensions(cn) : 0,
                                true
                            ));
                        }
                    } else {
                        ClassNode type;
                        String unresolvedName = redirectType.getType().getUnresolvedName();
                        ClassNode cn = info.get(unresolvedName);
                        if (cn != null) {
                            type = cn;
                        } else {
                            type = gt.getType();
                        }
                        typeArgumentMap.put(redirectType.getName(), new GroovyClassElement(
                            visitorContext,
                            type,
                            resolveElementAnnotationMetadataFactory(type),
                            Collections.emptyMap(),
                            type.isArray() ? computeDimensions(type) : 0
                        ));
                    }
                }
            } else if (redirectTypes != null) {
                for (GenericsType gt : redirectTypes) {
                    String name = gt.getName();
                    ClassNode cn = resolveTypeArgument(info, name);
                    if (cn != null) {
                        Map<String, ClassNode> newInfo = Collections.emptyMap();
                        if (genericsTypes != null) {
                            newInfo = alignNewGenericsInfo(genericsTypes, redirectTypes, info);
                        }
                        typeArgumentMap.put(gt.getName(), new GroovyClassElement(
                            visitorContext,
                            cn,
                            resolveElementAnnotationMetadataFactory(cn),
                            Collections.singletonMap(cn.getName(), newInfo),
                            cn.isArray() ? computeDimensions(cn) : 0
                        ));
                    }
                }
            }
            if (CollectionUtils.isNotEmpty(typeArgumentMap)) {
                return typeArgumentMap;
            }
        }
        Map<String, ClassNode> spec = AstGenericUtils.createGenericsSpec(classNode);
        if (!spec.isEmpty()) {
            Map<String, ClassElement> map = new LinkedHashMap<>(spec.size());
            for (Map.Entry<String, ClassNode> entry : spec.entrySet()) {
                ClassNode cn = entry.getValue();
                map.put(entry.getKey(), visitorContext.getElementFactory().newClassElement(cn, resolveElementAnnotationMetadataFactory(cn)));
            }
            return Collections.unmodifiableMap(map);
        }
        return Collections.emptyMap();
    }

    @Nullable
    private ClassNode resolveTypeArgument(Map<String, ClassNode> info, String name) {
        ClassNode cn = info.get(name);
        while (cn != null && cn.isGenericsPlaceHolder()) {
            name = cn.getUnresolvedName();
            ClassNode next = info.get(name);
            if (next == cn) {
                break;
            }
            cn = next;
        }
        return cn;
    }

    private int computeDimensions(ClassNode cn) {
        ClassNode componentType = cn.getComponentType();
        int i = 1;
        while (componentType != null && componentType.isArray()) {
            i++;
            componentType = componentType.getComponentType();
        }
        return i;
    }

    @Override
    public List<PropertyElement> getNativeBeanProperties() {
        // Native properties should be composed of field + synthetic getter/setter
        if (nativeProperties == null) {
            BeanPropertiesConfiguration configuration = new BeanPropertiesConfiguration();
            configuration.setAllowStaticProperties(true);
            Set<String> nativeProps = getPropertyNodes().stream().map(PropertyNode::getName).collect(Collectors.toCollection(LinkedHashSet::new));
            nativeProperties = AstBeanPropertiesUtils.resolveBeanProperties(configuration,
                this,
                () -> AstBeanPropertiesUtils.getSubtypeFirstMethods(this),
                () -> AstBeanPropertiesUtils.getSubtypeFirstFields(this),
                true,
                nativeProps,
                methodElement -> Optional.empty(),
                methodElement -> Optional.empty(),
                value -> mapPropertyElement(nativeProps, value, configuration, true));
        }
        return nativeProperties;
    }

    @Override
    public List<PropertyElement> getBeanProperties(BeanPropertiesConfiguration configuration) {
        Set<String> nativeProps = getPropertyNodes().stream().map(PropertyNode::getName).collect(Collectors.toCollection(LinkedHashSet::new));
        return AstBeanPropertiesUtils.resolveBeanProperties(configuration,
            this,
            () -> AstBeanPropertiesUtils.getSubtypeFirstMethods(this),
            () -> AstBeanPropertiesUtils.getSubtypeFirstFields(this),
            true,
            nativeProps,
            methodElement -> Optional.empty(),
            methodElement -> Optional.empty(),
            value -> mapPropertyElement(nativeProps, value, configuration, false));
    }

    @Override
    public List<PropertyElement> getBeanProperties() {
        if (properties == null) {
            properties = getBeanProperties(BeanPropertiesConfiguration.of(this));
        }
        return properties;
    }

    private GroovyPropertyElement mapPropertyElement(Set<String> nativeProps,
                                                     AstBeanPropertiesUtils.BeanPropertyData value,
                                                     BeanPropertiesConfiguration conf,
                                                     boolean nativePropertiesOnly) {
        if (value.type == null) {
            // withSomething() builder setter
            value.type = PrimitiveElement.VOID;
        }
        AtomicReference<AnnotationMetadataProvider> ref = new AtomicReference<>();
        if (conf.getAccessKinds().contains(BeanProperties.AccessKind.METHOD) && nativeProps.remove(value.propertyName)) {
            AnnotationMetadataProvider annotationMetadataProvider = new AnnotationMetadataProvider() {
                @Override
                public AnnotationMetadata getAnnotationMetadata() {
                    return new AnnotationMetadataHierarchy(GroovyClassElement.this, ref.get().getAnnotationMetadata());
                }
            };
            if (value.readAccessKind != BeanProperties.AccessKind.METHOD) {
                String getterName = NameUtils.getterNameFor(
                    value.propertyName,
                    value.type.equals(PrimitiveElement.BOOLEAN) || value.type.getName().equals(Boolean.class.getName())
                );
                value.getter = MethodElement.of(
                    this,
                    value.field.getDeclaringType(),
                    annotationMetadataProvider,
                    visitorContext.getAnnotationMetadataBuilder(),
                    value.field.getGenericType(),
                    value.field.getGenericType(),
                    getterName,
                    value.field.isStatic(),
                    value.field.isFinal()
                );
                value.readAccessKind = BeanProperties.AccessKind.METHOD;
            } else if (nativePropertiesOnly) {
                value.getter = null;
                value.readAccessKind = null;
            }
            if (!value.field.isFinal() && value.writeAccessKind != BeanProperties.AccessKind.METHOD) {
                value.setter = MethodElement.of(
                    this,
                    value.field.getDeclaringType(),
                    annotationMetadataProvider,
                    visitorContext.getAnnotationMetadataBuilder(),
                    PrimitiveElement.VOID,
                    PrimitiveElement.VOID,
                    NameUtils.setterNameFor(value.propertyName),
                    value.field.isStatic(),
                    value.field.isFinal(),
                    ParameterElement.of(value.field.getGenericType(), value.propertyName, annotationMetadataProvider, visitorContext.getAnnotationMetadataBuilder())
                );
                value.writeAccessKind = BeanProperties.AccessKind.METHOD;
            } else if (nativePropertiesOnly) {
                value.setter = null;
                value.writeAccessKind = null;
            }
        } else if (nativePropertiesOnly) {
            return null;
        }
        // Skip not accessible setters / getters
        if (value.writeAccessKind != BeanProperties.AccessKind.METHOD) {
            value.setter = null;
        }
        if (value.readAccessKind != BeanProperties.AccessKind.METHOD) {
            value.getter = null;
        }
        GroovyPropertyElement propertyElement = new GroovyPropertyElement(
            visitorContext,
            this,
            value.type,
            value.getter,
            value.setter,
            value.field,
            elementAnnotationMetadataFactory,
            value.propertyName,
            value.readAccessKind == null ? PropertyElement.AccessKind.METHOD : PropertyElement.AccessKind.valueOf(value.readAccessKind.name()),
            value.writeAccessKind == null ? PropertyElement.AccessKind.METHOD : PropertyElement.AccessKind.valueOf(value.writeAccessKind.name()),
            value.isExcluded);
        ref.set(propertyElement);
        return propertyElement;
    }

    @Override
    public boolean isArray() {
        return arrayDimensions > 0;
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        return new GroovyClassElement(visitorContext, classNode, elementAnnotationMetadataFactory, getGenericTypeInfo(), arrayDimensions);
    }

    @Override
    public int getArrayDimensions() {
        return arrayDimensions;
    }

    @Override
    public String toString() {
        return classNode.getName();
    }

    @Override
    public String getName() {
        return classNode.getName();
    }

    @Override
    public String getSimpleName() {
        return classNode.getNameWithoutPackage();
    }

    @Override
    public String getPackageName() {
        return classNode.getPackageName();
    }

    @Override
    public PackageElement getPackage() {
        final PackageNode aPackage = classNode.getPackage();
        if (aPackage != null) {
            return new GroovyPackageElement(
                visitorContext,
                aPackage,
                elementAnnotationMetadataFactory
            );
        } else {
            return PackageElement.DEFAULT_PACKAGE;
        }
    }

    @Override
    public boolean isAbstract() {
        return classNode.isAbstract();
    }

    @Override
    public boolean isStatic() {
        // I assume Groovy can decide not to make the class static internally
        // and isStaticClass will be false even if the class has static modifier
        return classNode.isStaticClass() || Modifier.isStatic(classNode.getModifiers());
    }

    @Override
    public boolean isPublic() {
        return (classNode.isSyntheticPublic() || Modifier.isPublic(classNode.getModifiers())) && !isPackagePrivate();
    }

    @Override
    public boolean isPrivate() {
        return Modifier.isPrivate(classNode.getModifiers());
    }

    @Override
    public boolean isFinal() {
        return Modifier.isFinal(classNode.getModifiers());
    }

    @Override
    public boolean isProtected() {
        return Modifier.isProtected(classNode.getModifiers());
    }

    @Override
    public ClassNode getNativeType() {
        return classNode;
    }

    @Override
    public boolean isAssignable(String type) {
        return AstClassUtils.isSubclassOfOrImplementsInterface(classNode, type);
    }

    @Override
    public boolean isAssignable(ClassElement type) {
        return AstClassUtils.isSubclassOfOrImplementsInterface(classNode, type.getName());
    }

    @NonNull
    @Override
    public List<? extends ClassElement> getBoundGenericTypes() {
        if (overrideBoundGenericTypes == null) {
            overrideBoundGenericTypes = getBoundGenericTypes(classNode);
        }
        return overrideBoundGenericTypes;
    }

    @NonNull
    private List<? extends ClassElement> getBoundGenericTypes(ClassNode classNode) {
        GenericsType[] genericsTypes = classNode.getGenericsTypes();
        if (genericsTypes == null) {
            return Collections.emptyList();
        } else {
            return Arrays.stream(genericsTypes)
                .map(cn -> {
                    if (cn.isWildcard()) {
                        List<GroovyClassElement> upperBounds;
                        if (cn.getUpperBounds() != null && cn.getUpperBounds().length > 0) {
                            upperBounds = Arrays.stream(cn.getUpperBounds())
                                .map(bound -> (GroovyClassElement) toClassElement(bound))
                                .collect(Collectors.toList());
                        } else {
                            upperBounds = Collections.singletonList((GroovyClassElement) visitorContext.getClassElement(Object.class).get());
                        }
                        List<GroovyClassElement> lowerBounds;
                        if (cn.getLowerBound() == null) {
                            lowerBounds = Collections.emptyList();
                        } else {
                            lowerBounds = Collections.singletonList((GroovyClassElement) toClassElement(cn.getLowerBound()));
                        }
                        return new GroovyWildcardElement(
                            upperBounds,
                            lowerBounds,
                            elementAnnotationMetadataFactory
                        );
                    } else {
                        return toClassElement(cn.getType());
                    }
                })
                .collect(Collectors.toList());
        }
    }

    @NonNull
    @Override
    public List<? extends GenericPlaceholderElement> getDeclaredGenericPlaceholders() {
        //noinspection unchecked
        return (List<? extends GenericPlaceholderElement>) getBoundGenericTypes(classNode.redirect());
    }

    protected final ClassElement toClassElement(ClassNode classNode) {
        return visitorContext.getElementFactory().newClassElement(classNode, emptyAnnotationsForNativeType(classNode));
    }

    @NonNull
    @Override
    public ClassElement withBoundGenericTypes(@NonNull List<? extends
        ClassElement> typeArguments) {
        // we can't create a new ClassNode, so we have to go this route.
        GroovyClassElement copy = new GroovyClassElement(visitorContext, classNode, elementAnnotationMetadataFactory);
        copy.overrideBoundGenericTypes = typeArguments;
        return copy;
    }

    private List<PropertyNode> getPropertyNodes() {
        List<PropertyNode> propertyNodes = new ArrayList<>();
        ClassNode classNode = this.classNode;
        while (classNode != null && !classNode.equals(ClassHelper.OBJECT_TYPE) && !classNode.equals(ClassHelper.Enum_Type)) {
            propertyNodes.addAll(classNode.getProperties());
            classNode = classNode.getSuperClass();
        }
        List<PropertyNode> propertyElements = new ArrayList<>();
        for (PropertyNode propertyNode : propertyNodes) {
            if (propertyNode.isPublic()) {
                propertyElements.add(propertyNode);
            }
        }
        return propertyElements;
    }

    private MethodElement asConstructor(ConstructorNode cn) {
        return visitorContext.getElementFactory().newConstructorElement(this, cn, elementAnnotationMetadataFactory);
    }

}
