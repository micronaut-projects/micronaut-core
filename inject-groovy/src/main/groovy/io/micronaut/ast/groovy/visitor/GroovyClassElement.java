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

import io.micronaut.ast.groovy.utils.AstClassUtils;
import io.micronaut.ast.groovy.utils.AstGenericUtils;
import io.micronaut.ast.groovy.utils.PublicMethodVisitor;
import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ArrayableClassElement;
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
import io.micronaut.inject.ast.PropertyElement;
import org.apache.groovy.util.concurrent.LazyInitializable;
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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static groovyjarjarasm.asm.Opcodes.ACC_PRIVATE;
import static groovyjarjarasm.asm.Opcodes.ACC_PROTECTED;
import static groovyjarjarasm.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;

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
    public boolean isTypeVariable() {
        return isTypeVar;
    }

    @Override
    public <T extends Element> List<T> getEnclosedElements(@NonNull ElementQuery<T> query) {
        Objects.requireNonNull(query, "Query cannot be null");
        ElementQuery.Result<T> result = query.result();
        boolean onlyDeclared = result.isOnlyDeclared();
        boolean onlyAccessible = result.isOnlyAccessible();
        boolean onlyAbstract = result.isOnlyAbstract();
        boolean onlyConcrete = result.isOnlyConcrete();
        boolean onlyInstance = result.isOnlyInstance();
        boolean onlyStatic = result.isOnlyStatic();
        boolean excludePropertyElements = result.isExcludePropertyElements();
        Set<AnnotatedNode> excludeMethodElements;
        Set<AnnotatedNode> excludeFieldElements;
        if (excludePropertyElements) {
            excludeMethodElements = new HashSet<>();
            excludeFieldElements = new HashSet<>();
            for (PropertyElement excludePropertyElement : getBeanProperties()) {
                excludePropertyElement.getReadMethod()
                    .filter(m -> m instanceof AbstractGroovyElement)
                    .ifPresent(methodElement -> excludeMethodElements.add((AnnotatedNode) methodElement.getNativeType()));
                excludePropertyElement.getWriteMethod()
                    .filter(m -> m instanceof AbstractGroovyElement)
                    .ifPresent(methodElement -> excludeMethodElements.add((AnnotatedNode) methodElement.getNativeType()));
                excludePropertyElement.getField().ifPresent(fieldElement -> excludeFieldElements.add((AnnotatedNode) fieldElement.getNativeType()));
            }
        } else {
            excludeMethodElements = Collections.emptySet();
            excludeFieldElements = Collections.emptySet();
        }

        List<Predicate<String>> namePredicates = result.getNamePredicates();
        List<Predicate<ClassElement>> typePredicates = result.getTypePredicates();
        List<Predicate<AnnotationMetadata>> annotationPredicates = result.getAnnotationPredicates();
        List<Predicate<T>> elementPredicates = result.getElementPredicates();
        List<Predicate<Set<ElementModifier>>> modifierPredicates = result.getModifierPredicates();
        List<T> elements;
        Class<T> elementType = result.getElementType();
        if (elementType == MethodElement.class) {

            List<MethodNode> methods;

            if (onlyDeclared) {
                methods = new ArrayList<>(classNode.getMethods());
            } else {
                methods = new ArrayList<>(getDeclaredMethods());
            }

            Iterator<MethodNode> i = methods.iterator();
            while (i.hasNext()) {
                MethodNode methodNode = i.next();
                if (JUNK_METHOD_FILTER.test(methodNode)) {
                    i.remove();
                    continue;
                }
                if (onlyAbstract && !methodNode.isAbstract()) {
                    i.remove();
                    continue;
                }
                if (onlyConcrete && methodNode.isAbstract()) {
                    i.remove();
                    continue;
                }
                if (onlyInstance && methodNode.isStatic()) {
                    i.remove();
                    continue;
                }
                if (onlyStatic && !methodNode.isStatic()) {
                    i.remove();
                    continue;
                }
                if (onlyAccessible) {
                    final ClassElement accessibleFromType = result.getOnlyAccessibleFromType().orElse(this);
                    if (methodNode.isPrivate()) {
                        i.remove();
                        continue;
                    } else if (!methodNode.getDeclaringClass().getName().equals(accessibleFromType.getName())) {
                        // inaccessible through package scope
                        if (methodNode.isPackageScope() && !methodNode.getDeclaringClass().getPackageName().equals(accessibleFromType.getPackageName())) {
                            i.remove();
                            continue;
                        }
                    }
                }
                if (!modifierPredicates.isEmpty()) {
                    Set<ElementModifier> elementModifiers = resolveModifiers(methodNode);
                    if (!modifierPredicates.stream().allMatch(p -> p.test(elementModifiers))) {
                        i.remove();
                        continue;
                    }
                }

                if (!namePredicates.isEmpty()) {
                    if (!namePredicates.stream().allMatch(p -> p.test(methodNode.getName()))) {
                        i.remove();
                    }
                }
                if (excludeMethodElements.contains(methodNode)) {
                    i.remove();
                }
            }

            //noinspection unchecked
            elements = methods.stream()
                .map(methodNode -> (T) visitorContext.getElementFactory().newMethodElement(this, methodNode, elementAnnotationMetadataFactory))
                .collect(Collectors.toList());
            if (!typePredicates.isEmpty()) {
                elements.removeIf(e -> !typePredicates.stream().allMatch(p -> p.test(((MethodElement) e).getGenericReturnType())));
            }
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
            Stream<FieldNode> fieldStream = fields.stream().filter(f -> !excludeFieldElements.contains(f));
            if (onlyInstance) {
                fieldStream = fieldStream.filter((fn) -> !fn.isStatic());
            } else if (onlyStatic) {
                fieldStream = fieldStream.filter(FieldNode::isStatic);
            }
            elements = fieldStream.map(fieldNode -> (T) visitorContext.getElementFactory().newFieldElement(this, fieldNode, elementAnnotationMetadataFactory)).collect(Collectors.toList());
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

    private Collection<MethodNode> getDeclaredMethods() {
        Map<String, MethodNode> declaredMethodsMap = new LinkedHashMap<>();
        collectDeclaredMethodsMap(classNode, declaredMethodsMap);
        return declaredMethodsMap.values();
    }

    private void collectDeclaredMethodsMap(ClassNode classNode, Map<String, MethodNode> methods) {
        // Partial copy from ClassNode#declaredMethodsMap to have linked map
        ClassNode parent = classNode.getSuperClass();
        if (parent != null) {
            collectDeclaredMethodsMap(parent, methods);
        }
        for (ClassNode iface : classNode.getInterfaces()) {
            Map<String, MethodNode> declaredMethods = new LinkedHashMap<>();
            collectDeclaredMethodsMap(iface, declaredMethods);
            for (Map.Entry<String, MethodNode> entry : declaredMethods.entrySet()) {
                if (entry.getValue().getDeclaringClass().isInterface() && (entry.getValue().getModifiers() & ACC_SYNTHETIC) == 0) {
                    methods.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
        }
        // add in the methods implemented in this class
        for (MethodNode method : classNode.getMethods()) {
            methods.put(method.getTypeDescriptor(), method);
        }
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
            generics.forEach((variable, type) -> {
                resolved.put(variable, new GroovyClassElement(visitorContext, type, resolveElementAnnotationMetadataFactory(classNode)));
            });
            results.put(name, resolved);
        });
        results.put(getName(), getTypeArguments());
        return results;
    }

    @Override
    public @NonNull
    Map<String, ClassElement> getTypeArguments() {
        Map<String, Map<String, ClassNode>> genericInfo = getGenericTypeInfo();
        Map<String, ClassNode> info = genericInfo.get(classNode.getName());
        return resolveGenericMap(info);
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
                map.put(entry.getKey(), new GroovyClassElement(visitorContext, cn, resolveElementAnnotationMetadataFactory(cn)));
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
    public List<PropertyElement> getBeanProperties() {
        if (properties == null) {
            List<PropertyElement> allProperties = new ArrayList<>();
            List<GroovyPropertyElement> propertyElements = getPropertyNodes();
            allProperties.addAll(propertyElements);
            allProperties.addAll(getPropertiesFromGettersAndSetters(propertyElements));
            properties = Collections.unmodifiableList(allProperties);
        }
        return properties;
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
        return classNode.isStaticClass();
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
    public ClassElement withBoundGenericTypes(@NonNull List<? extends ClassElement> typeArguments) {
        // we can't create a new ClassNode, so we have to go this route.
        GroovyClassElement copy = new GroovyClassElement(visitorContext, classNode, elementAnnotationMetadataFactory);
        copy.overrideBoundGenericTypes = typeArguments;
        return copy;
    }

    private List<GroovyPropertyElement> getPropertyNodes() {
        List<PropertyNode> propertyNodes = new ArrayList<>();
        ClassNode classNode = this.classNode;
        while (classNode != null && !classNode.equals(ClassHelper.OBJECT_TYPE) && !classNode.equals(ClassHelper.Enum_Type)) {
            propertyNodes.addAll(classNode.getProperties());
            classNode = classNode.getSuperClass();
        }
        List<GroovyPropertyElement> propertyElements = new ArrayList<>();
        for (PropertyNode propertyNode : propertyNodes) {
            if (propertyNode.isPublic() && !propertyNode.isStatic()) {
                GroovyPropertyElement groovyPropertyElement = new GroovyPropertyElement(
                    visitorContext,
                    this,
                    propertyNode,
                    elementAnnotationMetadataFactory
                );
                propertyElements.add(groovyPropertyElement);
            }
        }
        return propertyElements;
    }

    private List<GroovyPropertyElement> getPropertiesFromGettersAndSetters(List<GroovyPropertyElement> propertyNodes) {
        Set<String> groovyProps = propertyNodes.stream().map(PropertyElement::getName).collect(Collectors.toSet());
        Map<String, GetterAndSetter> props = new LinkedHashMap<>();
        ClassNode classNode = this.classNode;
        while (classNode != null && !classNode.equals(ClassHelper.OBJECT_TYPE) && !classNode.equals(ClassHelper.Enum_Type)) {

            classNode.visitContents(
                new PublicMethodVisitor(null) {

                    final String[] readPrefixes = getValue(AccessorsStyle.class, "readPrefixes", String[].class)
                        .orElse(new String[]{AccessorsStyle.DEFAULT_READ_PREFIX});
                    final String[] writePrefixes = getValue(AccessorsStyle.class, "writePrefixes", String[].class)
                        .orElse(new String[]{AccessorsStyle.DEFAULT_WRITE_PREFIX});

                    @Override
                    protected boolean isAcceptable(MethodNode node) {
                        boolean validModifiers = node.isPublic() && !node.isStatic() && !node.isSynthetic() && !node.isAbstract();
                        if (validModifiers) {
                            String methodName = node.getName();
                            if (methodName.contains("$") || methodName.equals("getMetaClass")) {
                                return false;
                            }

                            if (NameUtils.isReaderName(methodName, readPrefixes) && node.getParameters().length == 0) {
                                return true;
                            } else {
                                return NameUtils.isWriterName(methodName, writePrefixes) && node.getParameters().length == 1;
                            }
                        }
                        return validModifiers;
                    }

                    @Override
                    public void accept(ClassNode classNode, MethodNode node) {
                        String methodName = node.getName();
                        if (NameUtils.isReaderName(methodName, readPrefixes) && node.getParameters().length == 0) {
                            String propertyName = NameUtils.getPropertyNameForGetter(methodName, readPrefixes);
                            if (groovyProps.contains(propertyName)) {
                                return;
                            }
                            ClassNode returnTypeNode = node.getReturnType();
                            ClassElement getterReturnType = null;
                            if (returnTypeNode.isGenericsPlaceHolder()) {
                                final String placeHolderName = returnTypeNode.getUnresolvedName();
                                final ClassElement classElement = getTypeArguments().get(placeHolderName);
                                if (classElement != null) {
                                    getterReturnType = classElement;
                                }
                            }
                            if (getterReturnType == null) {
                                getterReturnType = visitorContext.getElementFactory().newClassElement(returnTypeNode, elementAnnotationMetadataFactory);
                            }

                            GetterAndSetter getterAndSetter = props.computeIfAbsent(propertyName, GetterAndSetter::new);
                            getterAndSetter.type = getterReturnType;
                            getterAndSetter.getter = node;
                            if (getterAndSetter.setter != null) {
                                ClassNode typeMirror = getterAndSetter.setter.getParameters()[0].getType();
                                ClassElement setterParameterType = visitorContext.getElementFactory().newClassElement(typeMirror, emptyAnnotationsForNativeType(classNode));
                                if (!setterParameterType.getName().equals(getterReturnType.getName())) {
                                    getterAndSetter.setter = null; // not a compatible setter
                                }
                            }
                        } else if (NameUtils.isWriterName(methodName, writePrefixes) && node.getParameters().length == 1) {
                            String propertyName = NameUtils.getPropertyNameForSetter(methodName, writePrefixes);
                            if (groovyProps.contains(propertyName)) {
                                return;
                            }
                            ClassNode typeMirror = node.getParameters()[0].getType();
                            ClassElement setterParameterType = visitorContext.getElementFactory().newClassElement(typeMirror, emptyAnnotationsForNativeType(classNode));

                            GetterAndSetter getterAndSetter = props.computeIfAbsent(propertyName, GetterAndSetter::new);
                            ClassElement propertyType = getterAndSetter.type;
                            if (propertyType != null) {
                                if (propertyType.getName().equals(setterParameterType.getName())) {
                                    getterAndSetter.setter = node;
                                }
                            } else {
                                getterAndSetter.setter = node;
                            }
                        }
                    }

                });
            classNode = classNode.getSuperClass();
        }
        List<GroovyPropertyElement> propertyElements = new ArrayList<>(props.size());
        if (!props.isEmpty()) {
            for (Map.Entry<String, GetterAndSetter> entry : props.entrySet()) {
                String propertyName = entry.getKey();
                GetterAndSetter value = entry.getValue();
                if (value.getter != null) {
                    FieldNode field = GroovyClassElement.this.classNode.getField(propertyName);
                    if (field instanceof LazyInitializable) {
                        //this nonsense is to work around https://issues.apache.org/jira/browse/GROOVY-10398
                        ((LazyInitializable) field).lazyInit();
                        try {
                            Field delegate = field.getClass().getDeclaredField("delegate");
                            delegate.setAccessible(true);
                            field = (FieldNode) delegate.get(field);
                        } catch (NoSuchFieldException | IllegalAccessException e) {
                            // no op
                        }
                    }

                    GroovyPropertyElement propertyElement = new GroovyPropertyElement(
                        visitorContext,
                        this,
                        value.type,
                        propertyName,
                        value.setter == null,
                        value.getter,
                        field,
                        value.getter,
                        value.setter,
                        elementAnnotationMetadataFactory);

                    propertyElements.add(propertyElement);
                }
            }
        }
        return propertyElements;
    }

    private MethodElement asConstructor(ConstructorNode cn) {
        return visitorContext.getElementFactory().newConstructorElement(this, cn, elementAnnotationMetadataFactory);
    }

    /**
     * Internal holder class for getters and setters.
     */
    private class GetterAndSetter {
        ClassElement type;
        MethodNode getter;
        MethodNode setter;
        final String propertyName;

        GetterAndSetter(String propertyName) {
            this.propertyName = propertyName;
        }
    }
}
