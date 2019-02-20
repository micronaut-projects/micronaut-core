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

import io.micronaut.ast.groovy.utils.AstAnnotationUtils;
import io.micronaut.ast.groovy.utils.AstClassUtils;
import io.micronaut.ast.groovy.utils.AstGenericUtils;
import io.micronaut.ast.groovy.utils.PublicMethodVisitor;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.control.SourceUnit;

import java.lang.reflect.Modifier;
import java.util.*;

/**
 * A class element returning data from a {@link ClassNode}.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class GroovyClassElement extends AbstractGroovyElement implements ClassElement {

    private final SourceUnit sourceUnit;
    private final ClassNode classNode;

    /**
     * @param sourceUnit         The source unit
     * @param classNode          The {@link ClassNode}
     * @param annotationMetadata The annotation metadata
     */
    GroovyClassElement(SourceUnit sourceUnit, ClassNode classNode, AnnotationMetadata annotationMetadata) {
        super(classNode, annotationMetadata);
        this.classNode = classNode;
        this.sourceUnit = sourceUnit;
    }

    @Override
    public Optional<ClassElement> getSuperType() {
        final ClassNode superClass = classNode.getSuperClass();
        if (superClass != null && !superClass.equals(ClassHelper.OBJECT_TYPE)) {
            return Optional.of(
                    new GroovyClassElement(
                            sourceUnit,
                            superClass,
                            AstAnnotationUtils.getAnnotationMetadata(sourceUnit, superClass)
                    )
            );
        }
        return Optional.empty();
    }

    @Override
    public Map<String, ClassElement> getTypeArguments() {
        Map<String, ClassNode> spec = AstGenericUtils.createGenericsSpec(classNode);
        if (!spec.isEmpty()) {
            Map<String, ClassElement> map = new LinkedHashMap<>(spec.size());
            for (Map.Entry<String, ClassNode> entry : spec.entrySet()) {
                ClassNode cn = entry.getValue();
                GroovyClassElement classElement;
                if (cn.isEnum()) {
                    classElement = new GroovyEnumElement(sourceUnit, cn, AstAnnotationUtils.getAnnotationMetadata(sourceUnit, cn));
                } else {
                    classElement = new GroovyClassElement(sourceUnit, cn, AstAnnotationUtils.getAnnotationMetadata(sourceUnit, cn));
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
                GroovyPropertyElement groovyPropertyElement = new GroovyPropertyElement(
                        this,
                        propertyNode.getField(),
                        AstAnnotationUtils.getAnnotationMetadata(sourceUnit, propertyNode.getField()),
                        new GroovyClassElement(sourceUnit, propertyNode.getType(),
                                AnnotationMetadata.EMPTY_METADATA),
                        propertyNode.getName(),
                        false,
                        propertyNode
                );
                propertyElements.add(groovyPropertyElement);
            }
        }
        Map<String, GetterAndSetter> props = new LinkedHashMap<>();
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
                                    getterReturnType = new GroovyClassElement(sourceUnit, returnTypeNode, AnnotationMetadata.EMPTY_METADATA);
                                }
                            } else {
                                getterReturnType = new GroovyClassElement(sourceUnit, returnTypeNode, AnnotationMetadata.EMPTY_METADATA);
                            }

                            GetterAndSetter getterAndSetter = props.computeIfAbsent(propertyName, GetterAndSetter::new);
                            configureDeclaringType(declaringTypeElement, getterAndSetter);
                            getterAndSetter.type = getterReturnType;
                            getterAndSetter.getter = node;
                            if (getterAndSetter.setter != null) {
                                ClassNode typeMirror = getterAndSetter.setter.getParameters()[0].getType();
                                ClassElement setterParameterType = new GroovyClassElement(sourceUnit, typeMirror, AnnotationMetadata.EMPTY_METADATA);
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
                            ClassElement setterParameterType = new GroovyClassElement(sourceUnit, typeMirror, AnnotationMetadata.EMPTY_METADATA);

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
                        if (beanPropertyData.declaringType == null && !classNode.equals(declaringTypeElement)) {
                            beanPropertyData.declaringType = new GroovyClassElement(
                                    sourceUnit,
                                    declaringTypeElement,
                                    AstAnnotationUtils.getAnnotationMetadata(sourceUnit, declaringTypeElement)
                            );
                        }
                    }
                });
        if (!props.isEmpty()) {
            for (Map.Entry<String, GetterAndSetter> entry : props.entrySet()) {
                String propertyName = entry.getKey();
                GetterAndSetter value = entry.getValue();
                if (value.getter != null) {
                    GroovyPropertyElement propertyElement = new GroovyPropertyElement(
                            value.declaringType == null ? this : value.declaringType,
                            value.getter,
                            AstAnnotationUtils.getAnnotationMetadata(sourceUnit, value.getter),
                            value.type,
                            propertyName,
                            value.setter == null,
                            value.getter);
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
        return AstClassUtils.isSubclassOf(classNode, type);
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
