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
package io.micronaut.ast.groovy.annotation;

import io.micronaut.ast.groovy.GroovyNativeElementHelper;
import io.micronaut.ast.groovy.utils.AstMessageUtils;
import io.micronaut.ast.groovy.utils.ExtendedParameter;
import io.micronaut.ast.groovy.visitor.GroovyVisitorContext;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.expressions.EvaluatedExpressionReference;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.annotation.AnnotatedElementValidator;
import io.micronaut.inject.visitor.VisitorContext;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.PackageNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.expr.AnnotationConstantExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.ClassNodeResolver;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Groovy implementation of {@link AbstractAnnotationMetadataBuilder}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class GroovyAnnotationMetadataBuilder extends AbstractAnnotationMetadataBuilder<AnnotatedNode, AnnotationNode> {
    public static final ClassNode ANN_OVERRIDE = ClassHelper.make(Override.class);
    public static final String VALIDATOR_KEY = "io.micronaut.VALIDATOR";

    final SourceUnit sourceUnit;
    final AnnotatedElementValidator elementValidator;
    final CompilationUnit compilationUnit;
    final GroovyNativeElementHelper nativeElementHelper;

    public GroovyAnnotationMetadataBuilder(SourceUnit sourceUnit, CompilationUnit compilationUnit) {
        this(sourceUnit, compilationUnit, new GroovyNativeElementHelper());
    }

    public GroovyAnnotationMetadataBuilder(SourceUnit sourceUnit, CompilationUnit compilationUnit, GroovyNativeElementHelper nativeElementHelper) {
        this.compilationUnit = compilationUnit;
        this.sourceUnit = sourceUnit;
        if (sourceUnit != null) {
            final ModuleNode ast = sourceUnit.getAST();
            if (ast != null) {
                Object validator = ast.getNodeMetaData(VALIDATOR_KEY);
                if (validator instanceof AnnotatedElementValidator annotatedElementValidator) {
                    elementValidator = annotatedElementValidator;
                } else {
                    this.elementValidator = SoftServiceLoader.load(AnnotatedElementValidator.class).firstAvailable().orElse(null);
                    ast.putNodeMetaData(VALIDATOR_KEY, this.elementValidator);
                }
            } else {
                this.elementValidator = null;
            }
        } else {
            this.elementValidator = null;
        }
        this.nativeElementHelper = nativeElementHelper;
    }

    @Override
    public CachedAnnotationMetadata lookupOrBuildForParameter(AnnotatedNode owningType, AnnotatedNode methodElement, AnnotatedNode parameterElement) {
        return super.lookupOrBuildForParameter(owningType, methodElement, new ExtendedParameter((MethodNode) methodElement, (Parameter) parameterElement));
    }

    @Override
    protected boolean isValidationRequired(AnnotatedNode member) {
        if (member != null) {
            final List<AnnotationNode> annotations = member.getAnnotations();
            if (CollectionUtils.isNotEmpty(annotations)) {
                return annotations.stream().anyMatch((it) ->
                    it.getClassNode().getName().startsWith("jakarta.validation"));
            }
        }
        return false;
    }

    @Override
    protected boolean isExcludedAnnotation(@NonNull AnnotatedNode element, @NonNull String annotationName) {
        if (element instanceof ClassNode classNode && classNode.isAnnotationDefinition()
            && (annotationName.startsWith("java.lang.annotation") || annotationName.startsWith("org.codehaus.groovy.transform"))) {
            return false;
        } else {
            return super.isExcludedAnnotation(element, annotationName);
        }
    }

    @Override
    protected AnnotatedNode getAnnotationMember(AnnotatedNode annotationElement, CharSequence member) {
        if (annotationElement instanceof ClassNode classNode) {
            final List<MethodNode> methods = classNode.getMethods(member.toString());
            if (CollectionUtils.isNotEmpty(methods)) {
                return methods.iterator().next();
            }
        }
        return null;
    }

    @Override
    protected String getOriginatingClassName(AnnotatedNode originatingElement) {
        if (originatingElement instanceof ClassNode classNode) {
            return classNode.getName();
        } else if (originatingElement instanceof ExtendedParameter extendedParameter) {
            return extendedParameter.getMethodNode().getDeclaringClass().getName();
        } else if (originatingElement instanceof MethodNode methodNode) {
            return methodNode.getDeclaringClass().getName();
        }

        ClassNode declaringClass = originatingElement.getDeclaringClass();
        if (declaringClass != null) {
            return declaringClass.getName();
        } else {
            return null;
        }
    }

    @Override
    protected RetentionPolicy getRetentionPolicy(@NonNull AnnotatedNode annotation) {
        List<AnnotationNode> annotations = annotation.getAnnotations();
        for (AnnotationNode ann : annotations) {
            if (ann.getClassNode().getName().equals(Retention.class.getName())) {
                final Iterator<Expression> i = ann.getMembers().values().iterator();
                if (i.hasNext()) {
                    final Expression expr = i.next();
                    if (expr instanceof PropertyExpression propertyExpression) {
                        try {
                            return RetentionPolicy.valueOf(propertyExpression.getPropertyAsString());
                        } catch (Throwable e) {
                            // should never happen
                            return RetentionPolicy.RUNTIME;
                        }
                    }
                }
            }
        }
        return RetentionPolicy.RUNTIME;
    }

    @Override
    protected AnnotatedElementValidator getElementValidator() {
        return this.elementValidator;
    }

    @Override
    protected void addError(@NonNull AnnotatedNode originatingElement, @NonNull String error) {
        AstMessageUtils.error(sourceUnit, originatingElement, error);
    }

    @Override
    protected void addWarning(@NonNull AnnotatedNode originatingElement, @NonNull String warning) {
        AstMessageUtils.warning(sourceUnit, originatingElement, warning);
    }

    @Override
    protected boolean hasAnnotation(AnnotatedNode element, Class<? extends Annotation> annotation) {
        return !element.getAnnotations(ClassHelper.makeCached(annotation)).isEmpty();
    }

    @Override
    protected boolean hasAnnotation(AnnotatedNode element, String annotation) {
        for (AnnotationNode ann : element.getAnnotations()) {
            if (ann.getClassNode().getName().equals(annotation)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean hasAnnotations(AnnotatedNode element) {
        return CollectionUtils.isNotEmpty(element.getAnnotations());
    }

    @Override
    protected VisitorContext createVisitorContext() {
        return new GroovyVisitorContext(sourceUnit, compilationUnit);
    }

    @Override
    protected AnnotatedNode getTypeForAnnotation(AnnotationNode annotationMirror) {
        return annotationMirror.getClassNode();
    }

    @Override
    protected String getRepeatableName(AnnotationNode annotationMirror) {
        return getRepeatableNameForType(annotationMirror.getClassNode());
    }

    @Override
    protected String getRepeatableNameForType(AnnotatedNode annotationType) {
        List<AnnotationNode> annotationNodes = annotationType.getAnnotations(ClassHelper.makeCached(Repeatable.class));
        if (CollectionUtils.isNotEmpty(annotationNodes)) {
            Expression expression = annotationNodes.get(0).getMember("value");
            if (expression instanceof ClassExpression) {
                return expression.getType().getName();
            }
        }
        return null;
    }

    @Override
    protected Optional<AnnotatedNode> getAnnotationMirror(String annotationName) {
        if (compilationUnit != null) {
            ClassNodeResolver.LookupResult lookupResult = compilationUnit.getClassNodeResolver().resolveName(annotationName, compilationUnit);
            if (lookupResult != null) {
                return Optional.ofNullable(lookupResult.getClassNode());
            }

            Optional<AnnotatedNode> classNode = Optional.ofNullable(compilationUnit.getClassNode(annotationName));
            if (classNode.isPresent()) {
                return classNode;
            }
        }
        ClassNode cn = ClassUtils.forName(annotationName, GroovyAnnotationMetadataBuilder.class.getClassLoader())
            .map(ClassHelper::make)
            .orElseGet(() -> ClassHelper.make(annotationName));
        if (!cn.getName().equals(ClassHelper.OBJECT)) {
            return Optional.of(cn);
        } else {
            return Optional.empty();
        }
    }

    @Override
    protected String getAnnotationTypeName(AnnotationNode annotationMirror) {
        return annotationMirror.getClassNode().getName();
    }

    @Override
    protected String getElementName(AnnotatedNode element) {
        if (element instanceof ClassNode node) {
            return node.getName();
        } else if (element instanceof MethodNode node) {
            return node.getName();
        } else if (element instanceof FieldNode node) {
            return node.getName();
        } else if (element instanceof PropertyNode node) {
            return node.getName();
        } else if (element instanceof PackageNode node) {
            return node.getName();
        }
        throw new IllegalArgumentException("Cannot establish name for node type: " + element.getClass().getName());
    }

    @Override
    protected List<? extends AnnotationNode> getAnnotationsForType(AnnotatedNode element) {
        List<AnnotationNode> annotations = element.getAnnotations();
        List<AnnotationNode> expanded = new ArrayList<>(annotations.size());
        expandAnnotations(annotations, expanded);
        return expanded;
    }

    private void expandAnnotations(List<AnnotationNode> annotations, List<AnnotationNode> expanded) {
        for (AnnotationNode node : annotations) {
            Expression value = node.getMember(AnnotationMetadata.VALUE_MEMBER);
            boolean repeatable = false;
            if (value instanceof ListExpression listExpression) {
                for (Expression expression : listExpression.getExpressions()) {
                    if (expression instanceof AnnotationConstantExpression annotationConstantExpression) {
                        String name = getRepeatableNameForType(expression.getType());
                        if (name != null && name.equals(node.getClassNode().getName())) {
                            repeatable = true;
                            expanded.add((AnnotationNode) annotationConstantExpression.getValue());
                        }
                    }
                }
            }
            if (!repeatable || node.getMembers().size() > 1) {
                expanded.add(node);
            }
        }
    }

    @Override
    protected List<AnnotatedNode> buildHierarchy(AnnotatedNode element, boolean inheritTypeAnnotations, boolean declaredOnly) {
        if (declaredOnly) {
            return new ArrayList<>(Collections.singletonList(element));
        } else if (element instanceof ClassNode classNode) {
            List<ClassNode> hierarchy = new ArrayList<>();
            if (classNode.isAnnotationDefinition()) {
                hierarchy.add(classNode);
            } else {
                nativeElementHelper.populateTypeHierarchy(classNode, hierarchy);
            }
            return (List) hierarchy;

        } else if (element instanceof MethodNode methodNode) {
            List<AnnotatedNode> hierarchy;
            if (inheritTypeAnnotations) {
                hierarchy = buildHierarchy(methodNode.getDeclaringClass(), false, declaredOnly);
            } else {
                hierarchy = new ArrayList<>();
            }
            if (!methodNode.getAnnotations(ANN_OVERRIDE).isEmpty()) {
                hierarchy.addAll(nativeElementHelper.findOverriddenMethods(methodNode.getDeclaringClass(), methodNode));
            }
            hierarchy.add(methodNode);
            return hierarchy;
        } else if (element instanceof ExtendedParameter extendedParameter) {
            List<AnnotatedNode> hierarchy = new ArrayList<>();
            MethodNode methodNode = extendedParameter.getMethodNode();
            if (!methodNode.getAnnotations(ANN_OVERRIDE).isEmpty()) {
                int variableIdx = Arrays.asList(methodNode.getParameters()).indexOf(extendedParameter.getParameter());
                for (MethodNode overridden : nativeElementHelper.findOverriddenMethods(methodNode.getDeclaringClass(), methodNode)) {
                    hierarchy.add(new ExtendedParameter(overridden, overridden.getParameters()[variableIdx]));
                }
            }
            hierarchy.add(extendedParameter);
            return hierarchy;
        } else {
            if (element == null) {
                return new ArrayList<>();
            } else {
                return new ArrayList<>(Collections.singletonList(element));
            }
        }
    }

    @Override
    protected void readAnnotationRawValues(
        AnnotatedNode originatingElement,
        String annotationName,
        AnnotatedNode member,
        String memberName,
        Object annotationValue,
        Map<CharSequence, Object> annotationValues) {
        if (!annotationValues.containsKey(memberName)) {
            Object v = readAnnotationValue(originatingElement, member, annotationName, memberName, annotationValue);
            if (v != null) {
                validateAnnotationValue(originatingElement, annotationName, member, memberName, v);
                annotationValues.put(memberName, v);
            }
        }
    }

    @Override
    protected Map<? extends AnnotatedNode, ?> readAnnotationDefaultValues(String annotationName, AnnotatedNode annotationType) {
        Map<MethodNode, Expression> defaultValues = new LinkedHashMap<>();
        if (annotationType instanceof ClassNode classNode) {
            List<MethodNode> methods = new ArrayList<>(classNode.getMethods());
            for (MethodNode method : methods) {
                Statement stmt = method.getCode();
                Expression expression = null;
                if (stmt instanceof ReturnStatement returnStatement) {
                    expression = returnStatement.getExpression();
                } else if (stmt instanceof ExpressionStatement expressionStatement) {
                    expression = expressionStatement.getExpression();
                }
                if (expression instanceof ConstantExpression constantExpression) {
                    final Object v = constantExpression.getValue();
                    if (v instanceof String s) {
                        defaultValues.put(method, new ConstantExpression(s));
                    } else if (v != null) {
                        defaultValues.put(method, expression);
                    }
                }
            }
        }
        return defaultValues;
    }

    @Override
    protected Object readAnnotationValue(AnnotatedNode originatingElement, AnnotatedNode member, String annotationName, String memberName, Object annotationValue) {
        if (annotationValue instanceof ConstantExpression constantExpression) {
            return readConstantExpression(originatingElement, annotationName, member, constantExpression);
        } else if (annotationValue instanceof PropertyExpression pe) {
            if (pe.getObjectExpression() instanceof ClassExpression classExpression) {
                ClassNode propertyType = classExpression.getType();
                if (propertyType.isEnum()) {
                    return pe.getPropertyAsString();
                } else {
                    if (propertyType.isResolved()) {
                        Class<?> typeClass = propertyType.getTypeClass();
                        try {
                            final Field f = ReflectionUtils.getRequiredField(typeClass, pe.getPropertyAsString());
                            f.setAccessible(true);
                            return f.get(typeClass);
                        } catch (Throwable e) {
                            // ignore
                        }
                    }
                }
            }
        } else if (annotationValue instanceof ClassExpression classExpression) {
            return new AnnotationClassValue<>(classExpression.getType().getName());
        } else if (annotationValue instanceof ListExpression listExpression) {
            List<Expression> expressions = listExpression.getExpressions();
            List<Object> converted = new ArrayList<>(expressions.size());
            for (Expression exp : expressions) {
                if (exp instanceof PropertyExpression propertyExpression) {
                    Expression valueExpression = propertyExpression.getProperty();
                    Expression objectExpression = propertyExpression.getObjectExpression();
                    if (valueExpression instanceof ConstantExpression constantExpression && objectExpression instanceof ClassExpression) {
                        Object value = readConstantExpression(originatingElement, annotationName, member, constantExpression);
                        if (value != null) {
                            converted.add(value);
                        }
                    }
                }
                if (exp instanceof ConstantExpression constantExpression) {
                    Object value = readConstantExpression(originatingElement, annotationName, member, constantExpression);
                    if (value != null) {
                        // if value is an expression reference, since we're iterating through a list,
                        //  we extract initial annotation value to wrap it into a single expression reference
                        //  after the iteration is complete
                        if (value instanceof EvaluatedExpressionReference expressionReference) {
                            value = expressionReference.annotationValue();
                        }
                        converted.add(value);
                    }
                } else if (exp instanceof ClassExpression classExpression) {
                    String typeName;
                    if (classExpression.getType().isArray()) {
                        typeName = "[L" + classExpression.getType().getComponentType().getName() + ";";
                    } else {
                        typeName = classExpression.getType().getName();
                    }
                    converted.add(new AnnotationClassValue<>(typeName));
                }
            }
            Object array = toArray(member, converted);
            if (isEvaluatedExpression(array)) {
                return buildEvaluatedExpressionReference(originatingElement, annotationName, memberName, array);
            }
            return array;
        } else if (annotationValue instanceof VariableExpression variableExpression) {
            Variable variable = variableExpression.getAccessedVariable();
            if (variable != null && variable.hasInitialExpression()) {
                return readAnnotationValue(originatingElement, member, annotationName, memberName, variable.getInitialExpression());
            }
        } else if (annotationValue != null) {
            if (ClassUtils.isJavaLangType(annotationValue.getClass())) {
                return annotationValue;
            }
        }
        return null;
    }

    private static Object toArray(AnnotatedNode member, Collection<?> collection) {
        if (!(member instanceof MethodNode methodNode)) {
            throw new IllegalStateException("Expected instance of MethodNode got: " + member);
        }
        ClassNode returnType = methodNode.getReturnType();
        if (!returnType.isArray()) {
            throw new IllegalStateException("Expected a method returning an array got: " + member);
        }
        Class<?> arrayType = Object.class;
        ClassNode component = returnType.getComponentType();
        if (component != null) {
            if (component.isEnum()) {
                arrayType = String.class;
                collection = collection.stream().map(val -> {
                    if (val instanceof Enum<?> anEnum) {
                        return anEnum.name();
                    }
                    return val;
                }).toList();
            } else if (component.isResolved()) {
                arrayType = component.getTypeClass();
                if (Annotation.class.isAssignableFrom(arrayType)) {
                    arrayType = AnnotationValue.class;
                } else if (Class.class.isAssignableFrom(arrayType)) {
                    arrayType = AnnotationClassValue.class;
                } else if (EvaluatedExpressionReference.class.isAssignableFrom(arrayType)) {
                    arrayType = EvaluatedExpressionReference.class;
                }
            }
        }
        if (collection.isEmpty()) {
            return Array.newInstance(arrayType, 0);
        }
        if (collection.stream().allMatch(val -> val instanceof AnnotationClassValue)) {
            arrayType = AnnotationClassValue.class;
        } else if (collection.stream().allMatch(val -> val instanceof AnnotationValue)) {
            arrayType = AnnotationValue.class;
        } else if (collection.stream().anyMatch(val -> val instanceof EvaluatedExpressionReference)) {
            arrayType = Object.class;
        }
        if (arrayType.isPrimitive()) {
            Class<?> wrapperType = ReflectionUtils.getWrapperType(arrayType);
            Class<?> primitiveArrayType = Array.newInstance(arrayType, 0).getClass();
            Object[] emptyWrapperArray = (Object[]) Array.newInstance(wrapperType, 0);
            Object[] wrapperArray = collection.toArray(emptyWrapperArray);
            // Convert to a proper primitive type array
            return ConversionService.SHARED.convertRequired(wrapperArray, primitiveArrayType);
        }
        return ConversionService.SHARED.convert(collection, Array.newInstance(arrayType, 0).getClass())
            .orElse(null);
    }

    private Object readConstantExpression(AnnotatedNode originatingElement, String annotationName, AnnotatedNode member, ConstantExpression constantExpression) {
        if (constantExpression instanceof AnnotationConstantExpression ann) {
            AnnotationNode value = (AnnotationNode) ann.getValue();
            return readNestedAnnotationValue(originatingElement, value);
        } else {
            Object value = constantExpression.getValue();
            if (value == null) {
                return null;
            }
            if (isEvaluatedExpression(value)) {
                String memberName = getAnnotationMemberName(member);
                return buildEvaluatedExpressionReference(originatingElement, annotationName, memberName, value);
            }
            if (value instanceof Collection<?> collection) {
                collection = collection.stream().map(this::convertConstantValue).toList();
                Object array = toArray(member, collection);
                if (isEvaluatedExpression(array)) {
                    return buildEvaluatedExpressionReference(originatingElement, annotationName, getAnnotationMemberName(member), array);
                }
                return array;
            }
            return convertConstantValue(value);
        }
    }

    @SuppressWarnings("java:S1872")
    private Object convertConstantValue(Object value) {
        if (value instanceof ClassNode classNode) {
            return new AnnotationClassValue<>(classNode.getName());
        }
        Class<?> valueClass = value.getClass();
        // Groovy 4.0.6 will return EnumConstantWrapper as a default value
        if (valueClass.getName().equals("org.codehaus.groovy.ast.decompiled.EnumConstantWrapper")) {
            return ReflectionUtils.getFieldValue(valueClass, "constant", value).orElse(null);
        }
        if (valueClass.getName().equals("org.codehaus.groovy.ast.decompiled.TypeWrapper")) {
            String desc = (String) ReflectionUtils.getFieldValue(valueClass, "desc", value).orElse(null);
            if (desc == null) {
                return null;
            }
            // Desc will return "Ljava/lang/String;"
            StringBuilder arraySuffix = new StringBuilder();
            while (desc.startsWith("[")) {
                desc = desc.substring(1);
                arraySuffix.append("[]");
            }
            String className = desc.substring(1, desc.length() - 1).replace("/", ".") + arraySuffix;
            return new AnnotationClassValue<>(className);
        }
        if (value instanceof CharSequence) {
            value = value.toString();
        }
        return value;
    }

    @Override
    protected Map<? extends AnnotatedNode, ?> readAnnotationRawValues(AnnotationNode annotationMirror) {
        Map<String, Expression> members = annotationMirror.getMembers();
        Map<MethodNode, Object> values = new LinkedHashMap<>();
        ClassNode annotationClassNode = annotationMirror.getClassNode();
        members.forEach((key, value) ->
            values.put(annotationClassNode.getMethods(key).get(0), value));
        return values;
    }

    @Override
    protected <K extends Annotation> Optional<AnnotationValue<K>> getAnnotationValues(AnnotatedNode originatingElement, AnnotatedNode member, Class<K> annotationType) {
        if (member != null) {
            ClassNode annotationTypeNode = ClassHelper.make(annotationType);
            final List<AnnotationNode> anns = member.getAnnotations(annotationTypeNode);
            if (CollectionUtils.isNotEmpty(anns)) {
                AnnotationNode ann = anns.get(0);
                Map<CharSequence, Object> converted = new LinkedHashMap<>();
                ClassNode annotationNode = ann.getClassNode();
                for (Map.Entry<String, Expression> entry : ann.getMembers().entrySet()) {
                    String key = entry.getKey();
                    Expression value = entry.getValue();
                    AnnotatedNode annotationMember = annotationNode.getMethod(key, new Parameter[0]);
                    readAnnotationRawValues(originatingElement, annotationType.getName(), annotationMember, key, value, converted);
                }
                Map<CharSequence, Object> annotationDefaults = getCachedAnnotationDefaults(annotationType.getName(), annotationTypeNode);
                if (!annotationDefaults.isEmpty()) {
                    Iterator<Map.Entry<CharSequence, Object>> i = converted.entrySet().iterator();
                    while (i.hasNext()) {
                        Map.Entry<CharSequence, Object> next = i.next();
                        Object v = annotationDefaults.get(next.getKey());
                        if (v != null && v.equals(next.getValue())) {
                            i.remove();
                        }
                    }
                }
                return Optional.of(AnnotationValue.builder(annotationType).members(converted).build());
            }
        }
        return Optional.empty();
    }

    @Override
    protected String getAnnotationMemberName(AnnotatedNode member) {
        return ((MethodNode) member).getName();
    }

}
