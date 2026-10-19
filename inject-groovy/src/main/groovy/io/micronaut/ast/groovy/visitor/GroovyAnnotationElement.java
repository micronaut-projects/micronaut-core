/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.inject.ast.AnnotationElement;
import io.micronaut.inject.ast.AnnotationMemberElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.utils.DefaultAnnotationMemberElement;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Groovy implementation of {@link io.micronaut.inject.ast.AnnotationElement}.
 *
 * @author graemerocher
 * @since 3.1.0
 */
@Internal
final class GroovyAnnotationElement extends GroovyClassElement implements AnnotationElement {

    public GroovyAnnotationElement(GroovyVisitorContext visitorContext,
                                   GroovyNativeElement nativeElement,
                                   ElementAnnotationMetadataFactory annotationMetadataFactory) {
        this(visitorContext, nativeElement, annotationMetadataFactory, 0);
    }

    /**
     * @param visitorContext The visitor context
     * @param nativeElement The native element
     * @param annotationMetadataFactory The annotation metadata factory
     * @param arrayDimensions The number of array dimensions
     */
    GroovyAnnotationElement(GroovyVisitorContext visitorContext,
                            GroovyNativeElement nativeElement,
                            ElementAnnotationMetadataFactory annotationMetadataFactory,
                            int arrayDimensions) {
        // an annotation type cannot be generic, so the type arguments can be ignored
        super(visitorContext, nativeElement, annotationMetadataFactory, null, arrayDimensions);
    }

    @Override
    protected @NonNull GroovyClassElement copyConstructor() {
        return new GroovyAnnotationElement(visitorContext, getNativeType(), elementAnnotationMetadataFactory, getArrayDimensions());
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        return copyArrayTypeAnnotations(new GroovyAnnotationElement(visitorContext, getNativeType(), elementAnnotationMetadataFactory, arrayDimensions));
    }

    @Override
    public boolean isInherited() {
        for (AnnotationNode annotationNode : classNode.getAnnotations()) {
            if (AnnotationUtil.ANN_INHERITED.equals(annotationNode.getClassNode().getName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<ElementType> getTargets() {
        return getTargets(classNode);
    }

    /**
     * @param classNode The annotation type
     * @return The element types the annotation is applicable to
     */
    static Set<ElementType> getTargets(ClassNode classNode) {
        List<AnnotationNode> targetNodes = classNode.getAnnotations(ClassHelper.makeCached(Target.class));
        if (targetNodes.isEmpty()) {
            return DEFAULT_TARGETS;
        }
        EnumSet<ElementType> targets = EnumSet.noneOf(ElementType.class);
        for (Expression expression : targetNodes.get(0).getMembers().values()) {
            addTargets(expression, targets);
        }
        return Collections.unmodifiableSet(targets);
    }

    private static void addTargets(Expression expression, EnumSet<ElementType> targets) {
        String name = null;
        if (expression instanceof ListExpression listExpression) {
            for (Expression item : listExpression.getExpressions()) {
                addTargets(item, targets);
            }
            return;
        } else if (expression instanceof PropertyExpression propertyExpression) {
            name = propertyExpression.getPropertyAsString();
        } else if (expression instanceof VariableExpression variableExpression) {
            name = variableExpression.getName();
        }
        if (name != null) {
            for (ElementType elementType : ElementType.values()) {
                if (elementType.name().equals(name)) {
                    targets.add(elementType);
                    break;
                }
            }
        }
    }

    @Override
    public Optional<String> getRepeatableContainer() {
        return Optional.ofNullable(visitorContext.getAnnotationMetadataBuilder().getRepeatableContainerNameForType(classNode));
    }

    @Override
    public RetentionPolicy getRetentionPolicy() {
        return visitorContext.getAnnotationMetadataBuilder().getRetentionPolicy(classNode);
    }

    @Override
    public List<? extends AnnotationMemberElement> getMembers() {
        // the Groovy compiler answers the members of an annotation interface as its methods, in declaration order
        List<MethodElement> methods = getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared());
        Supplier<Map<CharSequence, Object>> defaultValues = SupplierUtil.memoized(() ->
            visitorContext.getAnnotationMetadataBuilder().getAnnotationDefaultValues(getName(), classNode));
        List<AnnotationMemberElement> members = new ArrayList<>(methods.size());
        for (MethodElement method : methods) {
            members.add(new DefaultAnnotationMemberElement(method, method.getReturnType(), defaultValues));
        }
        return members;
    }
}
