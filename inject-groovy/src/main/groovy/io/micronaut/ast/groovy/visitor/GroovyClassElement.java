/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.ast.groovy.visitor;

import io.micronaut.ast.groovy.annotation.GroovyAnnotationMetadataBuilder;
import io.micronaut.ast.groovy.utils.AstAnnotationUtils;
import io.micronaut.ast.groovy.utils.AstClassUtils;
import io.micronaut.ast.groovy.utils.AstGenericUtils;
import io.micronaut.ast.groovy.utils.PublicMethodVisitor;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.lang.reflect.Modifier;
import java.util.*;

import static org.codehaus.groovy.ast.ClassHelper.makeCached;

/**
 * A class element returning data from a {@link ClassNode}.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class GroovyClassElement extends AbstractGroovyElement implements ClassElement {

    private final ClassNode classNode;
    private Map<String, Map<String, ClassNode>> genericInfo;

    /**
     * @param sourceUnit         The source unit
     * @param compilationUnit    The compilation unit
     * @param classNode          The {@link ClassNode}
     * @param annotationMetadata The annotation metadata
     */
    GroovyClassElement(SourceUnit sourceUnit, CompilationUnit compilationUnit, ClassNode classNode, AnnotationMetadata annotationMetadata) {
        this(sourceUnit, compilationUnit, classNode, annotationMetadata, null);
    }

    /**
     * @param sourceUnit         The source unit
     * @param compilationUnit    The compilation unit
     * @param classNode          The {@link ClassNode}
     * @param annotationMetadata The annotation metadata
     * @param genericInfo        The generic info
     */
    GroovyClassElement(SourceUnit sourceUnit, CompilationUnit compilationUnit, ClassNode classNode, AnnotationMetadata annotationMetadata, Map<String, Map<String, ClassNode>> genericInfo) {
        super(sourceUnit, compilationUnit, classNode, annotationMetadata);
        this.classNode = classNode;
        this.genericInfo = genericInfo;
    }

    @Override
    public boolean isPrimitive() {
        return ClassHelper.isPrimitiveType(classNode) || (classNode.isArray() && ClassHelper.isPrimitiveType(classNode.getComponentType()));
    }

    @Override
    public Optional<ClassElement> getSuperType() {
        final ClassNode superClass = classNode.getSuperClass();
        if (superClass != null && !superClass.equals(ClassHelper.OBJECT_TYPE)) {
            return Optional.of(
                    new GroovyClassElement(
                            sourceUnit,
                            compilationUnit,
                            superClass,
                            AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, superClass)
                    )
            );
        }
        return Optional.empty();
    }

    @Nonnull
    @Override
    public Optional<ConstructorElement> getPrimaryConstructor() {
        return Optional.ofNullable(findConcreteConstructor(classNode.getDeclaredConstructors())).map(constructorNode -> {
            final AnnotationMetadata annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, constructorNode);
            return new GroovyConstructorElement(this, sourceUnit, compilationUnit, constructorNode, annotationMetadata);
        });
    }

    @Override
    public boolean hasDefaultConstructor() {
        List<ConstructorNode> constructors = classNode.getDeclaredConstructors();
        if (CollectionUtils.isEmpty(constructors)) {
            return true; // Groovy has implicit default constructor
        }
        List<ConstructorNode> nonPrivateConstructors = findNonPrivateConstructors(constructors);
        return nonPrivateConstructors.stream().anyMatch(ctor -> ctor.getParameters().length == 0);
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

    @Nonnull
    @Override
    public Map<String, ClassElement> getTypeArguments(@Nonnull String type) {
        Map<String, Map<String, ClassNode>> allData = getGenericTypeInfo();

        Map<String, ClassNode> thisSpec = allData.get(getName());
        Map<String, ClassNode> forType = allData.get(type);
        if (forType != null) {
            Map<String, ClassElement> typeArgs = new LinkedHashMap<>(forType.size());
            for (Map.Entry<String, ClassNode> entry : forType.entrySet()) {
                ClassNode classNode = entry.getValue();

                ClassElement rawElement = new GroovyClassElement(sourceUnit, compilationUnit, classNode, AstAnnotationUtils.getAnnotationMetadata(
                        sourceUnit,
                        compilationUnit,
                        classNode
                ));
                if (thisSpec != null) {
                    rawElement = getGenericElement(sourceUnit, classNode, rawElement, thisSpec);
                }
                typeArgs.put(entry.getKey(), rawElement);
            }
            return Collections.unmodifiableMap(typeArgs);
        }

        return Collections.emptyMap();
    }

    @Override
    public @Nonnull
    Map<String, ClassElement> getTypeArguments() {
        Map<String, Map<String, ClassNode>> genericInfo = getGenericTypeInfo();
        Map<String, ClassNode> info = genericInfo.get(classNode.getName());
        if (info != null) {
            GenericsType[] genericsTypes = classNode.redirect().getGenericsTypes();
            if (genericsTypes != null) {
                Map<String, ClassElement> typeArgumentMap = new HashMap<>(genericsTypes.length);
                for (GenericsType gt : genericsTypes) {
                    String name = gt.getName();
                    ClassNode cn = info.get(name);
                    while (cn != null && cn.isGenericsPlaceHolder()) {
                        name = cn.getUnresolvedName();
                        cn = info.get(name);
                    }

                    if (cn != null) {
                        typeArgumentMap.put(name, new GroovyClassElement(
                                sourceUnit,
                                compilationUnit,
                                cn,
                                AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, cn)
                        ));
                    }
                }
                if (CollectionUtils.isNotEmpty(typeArgumentMap)) {
                    return typeArgumentMap;
                }
            }
        }
        Map<String, ClassNode> spec = AstGenericUtils.createGenericsSpec(classNode);
        if (!spec.isEmpty()) {
            Map<String, ClassElement> map = new LinkedHashMap<>(spec.size());
            for (Map.Entry<String, ClassNode> entry : spec.entrySet()) {
                ClassNode cn = entry.getValue();
                GroovyClassElement classElement;
                if (cn.isEnum()) {
                    classElement = new GroovyEnumElement(sourceUnit, compilationUnit, cn, AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, cn));
                } else {
                    classElement = new GroovyClassElement(sourceUnit, compilationUnit, cn, AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, cn));
                }
                map.put(entry.getKey(), classElement);
            }
            return Collections.unmodifiableMap(map);
        }
        return Collections.emptyMap();
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
                        sourceUnit,
                        compilationUnit,
                        this,
                        propertyNode.getField(),
                        AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, propertyNode.getField()),
                        propertyNode.getName(),
                        readOnly,
                        propertyNode
                ) {
                    @Nonnull
                    @Override
                    public ClassElement getType() {
                        ClassNode type = propertyNode.getType();
                        return new GroovyClassElement(sourceUnit, compilationUnit, type,
                                AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, type));
                    }
                };
                propertyElements.add(groovyPropertyElement);
            }
        }
        Map<String, GetterAndSetter> props = new LinkedHashMap<>();
        ClassNode classNode = this.classNode;
        while (classNode != null && !classNode.equals(ClassHelper.OBJECT_TYPE)) {

            classNode.visitContents(
                    new PublicMethodVisitor(null) {

                        @Override
                        protected boolean isAcceptable(MethodNode node) {
                            boolean validModifiers = node.isPublic() && !node.isStatic() && !node.isSynthetic() && !node.isAbstract();
                            if (validModifiers) {
                                String methodName = node.getName();
                                if (methodName.contains("$")) {
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
                                ClassElement getterReturnType;
                                if (returnTypeNode.isGenericsPlaceHolder()) {
                                    final String placeHolderName = returnTypeNode.getUnresolvedName();
                                    final ClassElement classElement = getTypeArguments().get(placeHolderName);
                                    if (classElement != null) {
                                        getterReturnType = classElement;
                                    } else {
                                        getterReturnType = new GroovyClassElement(sourceUnit, compilationUnit, returnTypeNode, AnnotationMetadata.EMPTY_METADATA);
                                    }
                                } else {
                                    getterReturnType = new GroovyClassElement(sourceUnit, compilationUnit, returnTypeNode, AnnotationMetadata.EMPTY_METADATA);
                                }

                                GetterAndSetter getterAndSetter = props.computeIfAbsent(propertyName, GetterAndSetter::new);
                                configureDeclaringType(declaringTypeElement, getterAndSetter);
                                getterAndSetter.type = getterReturnType;
                                getterAndSetter.getter = node;
                                if (getterAndSetter.setter != null) {
                                    ClassNode typeMirror = getterAndSetter.setter.getParameters()[0].getType();
                                    ClassElement setterParameterType = new GroovyClassElement(sourceUnit, compilationUnit, typeMirror, AnnotationMetadata.EMPTY_METADATA);
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
                                ClassElement setterParameterType = new GroovyClassElement(sourceUnit, compilationUnit, typeMirror, AnnotationMetadata.EMPTY_METADATA);

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
                                        sourceUnit,
                                        compilationUnit,
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
                            sourceUnit,
                            compilationUnit,
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
                                        sourceUnit,
                                        compilationUnit,
                                        value.setter,
                                        groovyAnnotationMetadataBuilder.buildForMethod(value.setter)
                                ));
                            }
                            return Optional.empty();
                        }

                        @Nonnull
                        @Override
                        public ClassElement getType() {
                            return value.type;
                        }

                        @Override
                        public Optional<MethodElement> getReadMethod() {
                            return Optional.of(new GroovyMethodElement(thisElement, sourceUnit, compilationUnit, value.getter, annotationMetadata));
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
        return classNode.isArray();
    }

    @Override
    public String toString() {
        return classNode.getName();
    }

    @Override
    public String getName() {
        if (isArray()) {
            return classNode.getComponentType().getName();
        } else {
            return classNode.getName();
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

    private ConstructorNode findConcreteConstructor(List<ConstructorNode> constructors) {
        if (CollectionUtils.isEmpty(constructors)) {
            return new ConstructorNode(Modifier.PUBLIC, new BlockStatement()); // empty default constructor
        }
        List<ConstructorNode> nonPrivateConstructors = findNonPrivateConstructors(constructors);

        ConstructorNode constructorNode;
        if (nonPrivateConstructors.size() == 1) {
            constructorNode = nonPrivateConstructors.get(0);
        } else {
            constructorNode = nonPrivateConstructors.stream().filter(cn ->
                    !cn.getAnnotations(makeCached(Inject.class)).isEmpty() ||
                            !cn.getAnnotations(makeCached(Creator.class)).isEmpty()).findFirst().orElse(null);
            if (constructorNode == null) {
                constructorNode = nonPrivateConstructors.stream().filter(cn -> Modifier.isPublic(cn.getModifiers())).findFirst().orElse(null);
            }
        }
        return constructorNode;
    }

    private List<ConstructorNode> findNonPrivateConstructors(List<ConstructorNode> constructorNodes) {
        List<ConstructorNode> nonPrivateConstructors = new ArrayList<>(2);
        for (ConstructorNode node : constructorNodes) {
            if (!Modifier.isPrivate(node.getModifiers())) {
                nonPrivateConstructors.add(node);
            }
        }
        return nonPrivateConstructors;
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
