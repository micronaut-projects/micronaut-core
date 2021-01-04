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

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.ast.groovy.annotation.GroovyAnnotationMetadataBuilder;
import io.micronaut.ast.groovy.utils.AstAnnotationUtils;
import io.micronaut.ast.groovy.utils.AstClassUtils;
import io.micronaut.ast.groovy.utils.AstGenericUtils;
import io.micronaut.ast.groovy.utils.PublicMethodVisitor;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ArrayableClassElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

import static org.codehaus.groovy.ast.ClassHelper.makeCached;

/**
 * A class element returning data from a {@link ClassNode}.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Internal
public class GroovyClassElement extends AbstractGroovyElement implements ArrayableClassElement {

    protected final ClassNode classNode;
    private final int arrayDimensions;
    private Map<String, Map<String, ClassNode>> genericInfo;

    /**
     * @param visitorContext     The visitor context
     * @param classNode          The {@link ClassNode}
     * @param annotationMetadata The annotation metadata
     */
    public GroovyClassElement(GroovyVisitorContext visitorContext, ClassNode classNode, AnnotationMetadata annotationMetadata) {
        this(visitorContext, classNode, annotationMetadata, null, 0);
    }

    /**
     * @param visitorContext     The visitor context
     * @param classNode          The {@link ClassNode}
     * @param annotationMetadata The annotation metadata
     * @param genericInfo        The generic info
     * @param arrayDimensions    The number of array dimensions
     */
    GroovyClassElement(
            GroovyVisitorContext visitorContext,
            ClassNode classNode,
            AnnotationMetadata annotationMetadata,
            Map<String, Map<String, ClassNode>> genericInfo,
            int arrayDimensions) {
        super(visitorContext, classNode, annotationMetadata);
        this.classNode = classNode;
        this.genericInfo = genericInfo;
        this.arrayDimensions = arrayDimensions;
        if (classNode.isArray()) {
            classNode.setName(classNode.getComponentType().getName());
        }
    }

    @Override
    public boolean isInner() {
        return classNode instanceof InnerClassNode;
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
    public Optional<ClassElement> getSuperType() {
        final ClassNode superClass = classNode.getSuperClass();
        if (superClass != null && !superClass.equals(ClassHelper.OBJECT_TYPE)) {
            return Optional.of(
                    visitorContext.getElementFactory().newClassElement(
                            superClass,
                            AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, superClass)
                    )
            );
        }
        return Optional.empty();
    }

    @NonNull
    @Override
    public Optional<MethodElement> getPrimaryConstructor() {
        MethodNode method = findStaticCreator();
        if (method == null) {
            method = findConcreteConstructor();
        }

        return createMethodElement(method);
    }

    @NonNull
    @Override
    public Optional<MethodElement> getDefaultConstructor() {
        MethodNode method = findDefaultStaticCreator();
        if (method == null) {
            method = findDefaultConstructor();
        }
        return createMethodElement(method);
    }

    private Optional<MethodElement> createMethodElement(MethodNode method) {
        return Optional.ofNullable(method).map(executableElement -> {

            final AnnotationMetadata annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, executableElement);
            if (executableElement instanceof ConstructorNode) {
                return new GroovyConstructorElement(this, visitorContext, (ConstructorNode) executableElement, annotationMetadata);
            } else {
                return new GroovyMethodElement(this, visitorContext, executableElement, annotationMetadata);
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

                AnnotationMetadata annotationMetadata = resolveAnnotationMetadata(classNode);
                ClassElement rawElement = visitorContext.getElementFactory().newClassElement(classNode, annotationMetadata);
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
                AnnotationMetadata annotationMetadata = resolveAnnotationMetadata(type);
                resolved.put(variable, new GroovyClassElement(visitorContext, type, annotationMetadata));
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
                            AnnotationMetadata annotationMetadata = resolveAnnotationMetadata(cn);
                            typeArgumentMap.put(redirectType.getName(), new GroovyClassElement(
                                    visitorContext,
                                    cn,
                                    annotationMetadata,
                                    Collections.singletonMap(cn.getName(), newInfo),
                                    cn.isArray() ? computeDimensions(cn) : 0
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
                        AnnotationMetadata annotationMetadata = resolveAnnotationMetadata(cn);
                        typeArgumentMap.put(redirectType.getName(), new GroovyClassElement(
                                visitorContext,
                                type,
                                annotationMetadata,
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
                        AnnotationMetadata annotationMetadata = resolveAnnotationMetadata(cn);
                        typeArgumentMap.put(gt.getName(), new GroovyClassElement(
                                visitorContext,
                                cn,
                                annotationMetadata,
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
                AnnotationMetadata annotationMetadata = resolveAnnotationMetadata(cn);
                ClassElement classElement = visitorContext.getElementFactory().newClassElement(cn, annotationMetadata);
                map.put(entry.getKey(), classElement);
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
        List<PropertyNode> propertyNodes = classNode.getProperties();
        List<PropertyElement> propertyElements = new ArrayList<>();
        Set<String> groovyProps = new HashSet<>();
        for (PropertyNode propertyNode : propertyNodes) {
            if (propertyNode.isPublic() && !propertyNode.isStatic()) {
                groovyProps.add(propertyNode.getName());
                boolean readOnly = propertyNode.getField().isFinal();
                GroovyPropertyElement groovyPropertyElement = new GroovyPropertyElement(
                        visitorContext,
                        this,
                        propertyNode.getField(),
                        AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, propertyNode.getField()),
                        propertyNode.getName(),
                        readOnly,
                        propertyNode
                ) {
                    @NonNull
                    @Override
                    public ClassElement getType() {
                        ClassNode type = propertyNode.getType();
                        return visitorContext.getElementFactory().newClassElement(type,
                                AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, type));
                    }
                };
                propertyElements.add(groovyPropertyElement);
            }
        }
        Map<String, GetterAndSetter> props = new LinkedHashMap<>();
        ClassNode classNode = this.classNode;
        while (classNode != null && !classNode.equals(ClassHelper.OBJECT_TYPE) && !classNode.equals(ClassHelper.Enum_Type)) {

            classNode.visitContents(
                    new PublicMethodVisitor(null) {

                        @Override
                        protected boolean isAcceptable(MethodNode node) {
                            boolean validModifiers = node.isPublic() && !node.isStatic() && !node.isSynthetic() && !node.isAbstract();
                            if (validModifiers) {
                                String methodName = node.getName();
                                if (methodName.contains("$") || methodName.equals("getMetaClass")) {
                                    return false;
                                }

                                if (NameUtils.isGetterName(methodName) && node.getParameters().length == 0) {
                                    return true;
                                } else {
                                    return NameUtils.isSetterName(methodName) && node.getParameters().length == 1;
                                }
                            }
                            return validModifiers;
                        }

                        @Override
                        public void accept(ClassNode classNode, MethodNode node) {
                            String methodName = node.getName();
                            final ClassNode declaringTypeElement = node.getDeclaringClass();
                            if (NameUtils.isGetterName(methodName) && node.getParameters().length == 0) {
                                String propertyName = NameUtils.getPropertyNameForGetter(methodName);
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
                                    getterReturnType = visitorContext.getElementFactory().newClassElement(returnTypeNode, AnnotationMetadata.EMPTY_METADATA);
                                }

                                GetterAndSetter getterAndSetter = props.computeIfAbsent(propertyName, GetterAndSetter::new);
                                configureDeclaringType(declaringTypeElement, getterAndSetter);
                                getterAndSetter.type = getterReturnType;
                                getterAndSetter.getter = node;
                                if (getterAndSetter.setter != null) {
                                    ClassNode typeMirror = getterAndSetter.setter.getParameters()[0].getType();
                                    ClassElement setterParameterType = visitorContext.getElementFactory().newClassElement(typeMirror, AnnotationMetadata.EMPTY_METADATA);
                                    if (!setterParameterType.getName().equals(getterReturnType.getName())) {
                                        getterAndSetter.setter = null; // not a compatible setter
                                    }
                                }
                            } else if (NameUtils.isSetterName(methodName) && node.getParameters().length == 1) {
                                String propertyName = NameUtils.getPropertyNameForSetter(methodName);
                                if (groovyProps.contains(propertyName)) {
                                    return;
                                }
                                ClassNode typeMirror = node.getParameters()[0].getType();
                                ClassElement setterParameterType = visitorContext.getElementFactory().newClassElement(typeMirror, AnnotationMetadata.EMPTY_METADATA);

                                GetterAndSetter getterAndSetter = props.computeIfAbsent(propertyName, GetterAndSetter::new);
                                configureDeclaringType(declaringTypeElement, getterAndSetter);
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

                        private void configureDeclaringType(ClassNode declaringTypeElement, GetterAndSetter beanPropertyData) {
                            if (beanPropertyData.declaringType == null && !GroovyClassElement.this.classNode.equals(declaringTypeElement)) {
                                beanPropertyData.declaringType = new GroovyClassElement(
                                        visitorContext,
                                        declaringTypeElement,
                                        AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, declaringTypeElement)
                                );
                            }
                        }
                    });
            classNode = classNode.getSuperClass();
        }
        if (!props.isEmpty()) {
            GroovyClassElement thisElement = this;
            for (Map.Entry<String, GetterAndSetter> entry : props.entrySet()) {
                String propertyName = entry.getKey();
                GetterAndSetter value = entry.getValue();
                if (value.getter != null) {

                    final AnnotationMetadata annotationMetadata;
                    final GroovyAnnotationMetadataBuilder groovyAnnotationMetadataBuilder = new GroovyAnnotationMetadataBuilder(sourceUnit, compilationUnit);
                    final FieldNode field = this.classNode.getField(propertyName);
                    if (field != null) {
                        annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, field, value.getter);
                    } else {
                        annotationMetadata = groovyAnnotationMetadataBuilder.buildForMethod(value.getter);
                    }
                    GroovyPropertyElement propertyElement = new GroovyPropertyElement(
                            visitorContext,
                            value.declaringType == null ? this : value.declaringType,
                            value.getter,
                            annotationMetadata,
                            propertyName,
                            value.setter == null,
                            value.getter) {
                        @Override
                        public Optional<MethodElement> getWriteMethod() {
                            if (value.setter != null) {
                                return Optional.of(new GroovyMethodElement(
                                        thisElement,
                                        visitorContext,
                                        value.setter,
                                        groovyAnnotationMetadataBuilder.buildForMethod(value.setter)
                                ));
                            }
                            return Optional.empty();
                        }

                        @NonNull
                        @Override
                        public ClassElement getType() {
                            return value.type;
                        }

                        @Override
                        public Optional<MethodElement> getReadMethod() {
                            return Optional.of(new GroovyMethodElement(thisElement, visitorContext, value.getter, annotationMetadata));
                        }
                    };
                    propertyElements.add(propertyElement);
                }
            }
        }
        return Collections.unmodifiableList(propertyElements);
    }

    @Override
    public boolean isArray() {
        return arrayDimensions > 0;
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        return new GroovyClassElement(visitorContext, classNode, getAnnotationMetadata(), getGenericTypeInfo(), arrayDimensions);
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
    public boolean isAbstract() {
        return classNode.isAbstract();
    }

    @Override
    public boolean isStatic() {
        return classNode.isStaticClass();
    }

    @Override
    public boolean isPublic() {
        return classNode.isSyntheticPublic() || Modifier.isPublic(classNode.getModifiers());
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
    public Object getNativeType() {
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

    private MethodNode findConcreteConstructor() {
        List<ConstructorNode> constructors = classNode.getDeclaredConstructors();
        if (CollectionUtils.isEmpty(constructors) && !classNode.isAbstract() && !classNode.isEnum()) {
            return new ConstructorNode(Modifier.PUBLIC, new BlockStatement()); // empty default constructor
        }

        List<ConstructorNode> nonPrivateConstructors = findNonPrivateMethods(constructors);

        MethodNode methodNode;
        if (nonPrivateConstructors.size() == 1) {
            methodNode = nonPrivateConstructors.get(0);
        } else {
            methodNode = nonPrivateConstructors.stream().filter(cn ->
                    !cn.getAnnotations(makeCached(Inject.class)).isEmpty() ||
                            !cn.getAnnotations(makeCached(Creator.class)).isEmpty()).findFirst().orElse(null);
            if (methodNode == null) {
                methodNode = nonPrivateConstructors.stream().filter(cn -> Modifier.isPublic(cn.getModifiers())).findFirst().orElse(null);
            }
        }
        return methodNode;
    }

    private MethodNode findDefaultConstructor() {
        List<ConstructorNode> constructors = classNode.getDeclaredConstructors();
        if (CollectionUtils.isEmpty(constructors) && !classNode.isEnum()) {
            return new ConstructorNode(Modifier.PUBLIC, new BlockStatement()); // empty default constructor
        }

        constructors = findNonPrivateMethods(constructors).stream()
                .filter(ctor -> ctor.getParameters().length == 0)
                .collect(Collectors.toList());

        if (constructors.isEmpty()) {
            return null;
        }

        if (constructors.size() == 1) {
            return constructors.get(0);
        }

        return constructors.stream()
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .findFirst().orElse(null);
    }

    private MethodNode findStaticCreator() {
        List<MethodNode> creators = findNonPrivateStaticCreators();

        if (creators.isEmpty()) {
            return null;
        }
        if (creators.size() == 1) {
            return creators.get(0);
        }

        //Can be multiple static @Creator methods. Prefer one with args here. The no arg method (if present) will
        //be picked up by staticDefaultCreatorFor
        List<MethodNode> withArgs = creators.stream()
                .filter(method -> method.getParameters().length > 0)
                .collect(Collectors.toList());

        if (withArgs.size() == 1) {
            return withArgs.get(0);
        } else {
            creators = withArgs;
        }

        return creators.stream()
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .findFirst().orElse(null);
    }

    private MethodNode findDefaultStaticCreator() {
        List<MethodNode> creators = findNonPrivateStaticCreators().stream()
                .filter(ctor -> ctor.getParameters().length == 0)
                .collect(Collectors.toList());

        if (creators.isEmpty()) {
            return null;
        }

        if (creators.size() == 1) {
            return creators.get(0);
        }

        return creators.stream()
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .findFirst().orElse(null);
    }

    private <T extends MethodNode> List<T> findNonPrivateMethods(List<T> methodNodes) {
        List<T> nonPrivateMethods = new ArrayList<>(2);
        for (MethodNode node : methodNodes) {
            if (!Modifier.isPrivate(node.getModifiers())) {
                nonPrivateMethods.add((T) node);
            }
        }
        return nonPrivateMethods;
    }

    private List<MethodNode> findNonPrivateStaticCreators() {
        List<MethodNode> creators = classNode.getAllDeclaredMethods().stream()
                .filter(method -> Modifier.isStatic(method.getModifiers()))
                .filter(method -> !Modifier.isPrivate(method.getModifiers()))
                .filter(method -> method.getReturnType().equals(classNode))
                .filter(method -> method.getAnnotations(makeCached(Creator.class)).size() > 0)
                .collect(Collectors.toList());

        if (creators.isEmpty() && classNode.isEnum()) {
            creators = classNode.getAllDeclaredMethods().stream()
                    .filter(method -> Modifier.isStatic(method.getModifiers()))
                    .filter(method -> !Modifier.isPrivate(method.getModifiers()))
                    .filter(method -> method.getReturnType().equals(classNode))
                    .filter(method -> method.getName().equals("valueOf"))
                    .collect(Collectors.toList());
        }
        return creators;
    }

    /**
     * Internal holder class for getters and setters.
     */
    private class GetterAndSetter {
        ClassElement type;
        GroovyClassElement declaringType;
        MethodNode getter;
        MethodNode setter;
        final String propertyName;

        GetterAndSetter(String propertyName) {
            this.propertyName = propertyName;
        }
    }
}
