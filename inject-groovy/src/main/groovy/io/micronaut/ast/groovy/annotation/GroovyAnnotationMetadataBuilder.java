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

import groovy.lang.GroovyObject;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.Script;
import io.micronaut.ast.groovy.utils.AstGenericUtils;
import io.micronaut.ast.groovy.utils.AstMessageUtils;
import io.micronaut.ast.groovy.utils.ExtendedParameter;
import io.micronaut.ast.groovy.visitor.GroovyVisitorContext;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;
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
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
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

    public GroovyAnnotationMetadataBuilder(SourceUnit sourceUnit, CompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit;
        this.sourceUnit = sourceUnit;
        if (sourceUnit != null) {
            final ModuleNode ast = sourceUnit.getAST();
            if (ast != null) {
                Object validator = ast.getNodeMetaData(VALIDATOR_KEY);
                if (validator instanceof AnnotatedElementValidator) {
                    elementValidator = (AnnotatedElementValidator) validator;
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
    }

    @Override
    public CacheEntry lookupOrBuildForParameter(AnnotatedNode owningType, AnnotatedNode methodElement, AnnotatedNode parameterElement) {
        return super.lookupOrBuildForParameter(owningType, methodElement, new ExtendedParameter((MethodNode) methodElement, (Parameter) parameterElement));
    }

    @Override
    protected boolean isValidationRequired(AnnotatedNode member) {
        if (member != null) {
            final List<AnnotationNode> annotations = member.getAnnotations();
            if (CollectionUtils.isNotEmpty(annotations)) {
                return annotations.stream().anyMatch((it) -> it.getClassNode().getName().startsWith("javax.validation"));
            }
        }
        return false;
    }

    @Override
    protected boolean isExcludedAnnotation(@NonNull AnnotatedNode element, @NonNull String annotationName) {
        if (element instanceof ClassNode && ((ClassNode) element).isAnnotationDefinition() && annotationName.startsWith("java.lang.annotation")) {
            return false;
        } else {
            return super.isExcludedAnnotation(element, annotationName);
        }
    }

    @Override
    protected AnnotatedNode getAnnotationMember(AnnotatedNode originatingElement, CharSequence member) {
        if (originatingElement instanceof ClassNode) {
            final List<MethodNode> methods = ((ClassNode) originatingElement).getMethods(member.toString());
            if (CollectionUtils.isNotEmpty(methods)) {
                return methods.iterator().next();
            }
        }
        return null;
    }

    @Override
    protected RetentionPolicy getRetentionPolicy(@NonNull AnnotatedNode annotation) {
        List<AnnotationNode> annotations = annotation.getAnnotations();
        for (AnnotationNode ann : annotations) {
            if (ann.getClassNode().getName().equals(Retention.class.getName())) {
                final Iterator<Expression> i = ann.getMembers().values().iterator();
                if (i.hasNext()) {
                    final Expression expr = i.next();
                    if (expr instanceof PropertyExpression) {
                        PropertyExpression pe = (PropertyExpression) expr;
                        try {
                            return RetentionPolicy.valueOf(pe.getPropertyAsString());
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
        if (element instanceof ClassNode) {
            return ((ClassNode) element).getName();
        } else if (element instanceof MethodNode) {
            return ((MethodNode) element).getName();
        } else if (element instanceof FieldNode) {
            return ((FieldNode) element).getName();
        } else if (element instanceof PropertyNode) {
            return ((PropertyNode) element).getName();
        } else if (element instanceof PackageNode) {
            return ((PackageNode) element).getName();
        }
        throw new IllegalArgumentException("Cannot establish name for node type: " + element.getClass().getName());
    }

    @Override
    protected List<? extends AnnotationNode> getAnnotationsForType(AnnotatedNode element) {
        List<AnnotationNode> annotations = element.getAnnotations();
        List<AnnotationNode> expanded = new ArrayList<>(annotations.size());
        for (AnnotationNode node : annotations) {
            Expression value = node.getMember("value");
            boolean repeatable = false;
            if (value instanceof ListExpression) {
                for (Expression expression : ((ListExpression) value).getExpressions()) {
                    if (expression instanceof AnnotationConstantExpression) {
                        String name = getRepeatableNameForType(expression.getType());
                        if (name != null && name.equals(node.getClassNode().getName())) {
                            repeatable = true;
                            expanded.add((AnnotationNode) ((AnnotationConstantExpression) expression).getValue());
                        }
                    }
                }
            }
            if (!repeatable || node.getMembers().size() > 1) {
                expanded.add(node);
            }
        }
        return expanded;
    }

    @Override
    protected List<AnnotatedNode> buildHierarchy(AnnotatedNode element, boolean inheritTypeAnnotations, boolean declaredOnly) {
        if (declaredOnly) {
            return new ArrayList<>(Collections.singletonList(element));
        } else if (element instanceof ClassNode) {
            List<AnnotatedNode> hierarchy = new ArrayList<>();
            ClassNode cn = (ClassNode) element;
            hierarchy.add(cn);
            if (cn.isAnnotationDefinition()) {
                return hierarchy;
            }
            populateTypeHierarchy(cn, hierarchy);
            Collections.reverse(hierarchy);
            return hierarchy;
        } else if (element instanceof MethodNode) {
            MethodNode mn = (MethodNode) element;
            List<AnnotatedNode> hierarchy;
            if (inheritTypeAnnotations) {
                hierarchy = buildHierarchy(mn.getDeclaringClass(), false, declaredOnly);
            } else {
                hierarchy = new ArrayList<>();
            }
            if (!mn.getAnnotations(ANN_OVERRIDE).isEmpty()) {
                hierarchy.addAll(findOverriddenMethods(mn));
            }
            hierarchy.add(mn);
            return hierarchy;
        } else if (element instanceof ExtendedParameter) {
            ExtendedParameter p = (ExtendedParameter) element;
            List<AnnotatedNode> hierarchy = new ArrayList<>();
            MethodNode methodNode = p.getMethodNode();
            if (!methodNode.getAnnotations(ANN_OVERRIDE).isEmpty()) {
                int variableIdx = Arrays.asList(methodNode.getParameters()).indexOf(p.getParameter());
                for (MethodNode overridden : findOverriddenMethods(methodNode)) {
                    hierarchy.add(new ExtendedParameter(overridden, overridden.getParameters()[variableIdx]));
                }
            }
            hierarchy.add(p);
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
            final Object v = readAnnotationValue(originatingElement, member, memberName, annotationValue);
            if (v != null) {
                validateAnnotationValue(originatingElement, annotationName, member, memberName, v);
                annotationValues.put(memberName, v);
            }
        }
    }

    @Override
    protected Map<? extends AnnotatedNode, ?> readAnnotationDefaultValues(String annotationName, AnnotatedNode annotationType) {
        Map<MethodNode, Expression> defaultValues = new LinkedHashMap<>();
        if (annotationType instanceof ClassNode) {
            ClassNode classNode = (ClassNode) annotationType;
            List<MethodNode> methods = new ArrayList<>(classNode.getMethods());

            // TODO: Remove this branch of the code after upgrading to Groovy 3.0
            // https://issues.apache.org/jira/browse/GROOVY-8696
            if (classNode.isResolved()) {
                Class<?> resolved = classNode.getTypeClass();
                for (MethodNode method : methods) {
                    try {
                        final Object defaultValue = resolved.getDeclaredMethod(method.getName()).getDefaultValue();
                        if (defaultValue != null) {
                            if (defaultValue instanceof Class) {
                                defaultValues.put(method, new ClassExpression(ClassHelper.makeCached((Class<?>) defaultValue)));
                            } else {
                                if (defaultValue instanceof String) {
                                    if (StringUtils.isNotEmpty((String) defaultValue)) {
                                        defaultValues.put(method, new ConstantExpression(defaultValue));
                                    }
                                } else {
                                    defaultValues.put(method, new ConstantExpression(defaultValue));
                                }
                            }
                        }
                    } catch (NoSuchMethodError | NoSuchMethodException e) {
                        // method no longer exists alias annotation
                        // ignore and continue
                    }
                }
            } else {
                for (MethodNode method : methods) {
                    Statement stmt = method.getCode();
                    Expression expression = null;
                    if (stmt instanceof ReturnStatement) {
                        expression = ((ReturnStatement) stmt).getExpression();
                    } else if (stmt instanceof ExpressionStatement) {
                        expression = ((ExpressionStatement) stmt).getExpression();
                    }
                    if (expression instanceof ConstantExpression) {
                        ConstantExpression ce = (ConstantExpression) expression;
                        final Object v = ce.getValue();
                        if (v != null) {
                            if (v instanceof String) {
                                if (StringUtils.isNotEmpty((String) v)) {
                                    defaultValues.put(method, new ConstantExpression(v));
                                }
                            } else {
                                defaultValues.put(method, expression);
                            }
                        }
                    }
                }
            }
        }
        return defaultValues;
    }

    @Override
    protected boolean isInheritedAnnotation(@NonNull AnnotationNode annotationMirror) {
        final List<AnnotationNode> annotations = annotationMirror.getClassNode().getAnnotations();
        if (CollectionUtils.isNotEmpty(annotations)) {
            return annotations.stream().anyMatch((ann) ->
                ann.getClassNode().getName().equals(Inherited.class.getName())
            );
        }
        return false;
    }

    @Override
    protected boolean isInheritedAnnotationType(@NonNull AnnotatedNode annotationType) {
        final List<AnnotationNode> annotations = annotationType.getAnnotations();
        if (CollectionUtils.isNotEmpty(annotations)) {
            return annotations.stream().anyMatch((ann) ->
                ann.getClassNode().getName().equals(Inherited.class.getName())
            );
        }
        return false;
    }

    @Override
    protected Map<String, ? extends AnnotatedNode> getAnnotationMembers(String annotationType) {
        final AnnotatedNode node = getAnnotationMirror(annotationType).orElse(null);
        if (node instanceof ClassNode) {
            final ClassNode cn = (ClassNode) node;
            if (cn.isAnnotationDefinition()) {
                return cn.getDeclaredMethodsMap();
            }
        }
        return Collections.emptyMap();
    }

    @Override
    protected boolean hasSimpleAnnotation(AnnotatedNode element, String simpleName) {
        if (element != null) {
            final List<AnnotationNode> annotations = element.getAnnotations();
            for (AnnotationNode ann : annotations) {
                if (ann.getClassNode().getNameWithoutPackage().equalsIgnoreCase(simpleName)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected Map<? extends AnnotatedNode, ?> readAnnotationDefaultValues(AnnotationNode annotationMirror) {
        ClassNode classNode = annotationMirror.getClassNode();
        String annotationName = classNode.getName();
        return readAnnotationDefaultValues(annotationName, classNode);
    }

    @Override
    protected Object readAnnotationValue(AnnotatedNode originatingElement, AnnotatedNode member, String memberName, Object annotationValue) {
        if (annotationValue instanceof ConstantExpression) {
            if (annotationValue instanceof AnnotationConstantExpression) {
                AnnotationConstantExpression ann = (AnnotationConstantExpression) annotationValue;
                AnnotationNode value = (AnnotationNode) ann.getValue();
                final AnnotationValue<?> av = readNestedAnnotationValue(originatingElement, value);
                if (member instanceof MethodNode && ((MethodNode) member).getReturnType().isArray()) {
                    return new AnnotationValue[]{av};
                } else {
                    return av;
                }
            } else {
                return ((ConstantExpression) annotationValue).getValue();
            }

        } else if (annotationValue instanceof PropertyExpression) {
            PropertyExpression pe = (PropertyExpression) annotationValue;
            if (pe.getObjectExpression() instanceof ClassExpression) {
                ClassExpression ce = (ClassExpression) pe.getObjectExpression();
                ClassNode propertyType = ce.getType();
                if (propertyType.isEnum()) {
                    return pe.getPropertyAsString();
                } else {
                    if (propertyType.isResolved()) {
                        Class typeClass = propertyType.getTypeClass();
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
        } else if (annotationValue instanceof ClassExpression) {
            return new AnnotationClassValue<>(((ClassExpression) annotationValue).getType().getName());
        } else if (annotationValue instanceof ListExpression) {
            ListExpression le = (ListExpression) annotationValue;
            List<Object> converted = new ArrayList<>();
            Class<?> arrayType = Object.class;
            for (Expression exp : le.getExpressions()) {
                if (exp instanceof PropertyExpression) {
                    PropertyExpression propertyExpression = (PropertyExpression) exp;
                    Expression valueExpression = propertyExpression.getProperty();
                    Expression objectExpression = propertyExpression.getObjectExpression();
                    if (valueExpression instanceof ConstantExpression && objectExpression instanceof ClassExpression) {
                        Object value = ((ConstantExpression) valueExpression).getValue();
                        if (value != null) {
                            if (value instanceof CharSequence) {
                                value = value.toString();
                            }
                            ClassNode enumType = objectExpression.getType();
                            if (enumType.isResolved()) {
                                arrayType = enumType.getTypeClass();
                            } else {
                                arrayType = String.class;
                            }
                            converted.add(value);
                        }
                    }
                }
                if (exp instanceof AnnotationConstantExpression) {
                    arrayType = AnnotationValue.class;
                    AnnotationConstantExpression ann = (AnnotationConstantExpression) exp;
                    AnnotationNode value = (AnnotationNode) ann.getValue();
                    converted.add(readNestedAnnotationValue(originatingElement, value));
                } else if (exp instanceof ConstantExpression) {
                    Object value = ((ConstantExpression) exp).getValue();
                    if (value != null) {
                        if (value instanceof CharSequence) {
                            value = value.toString();
                        }
                        arrayType = value.getClass();
                        converted.add(value);
                    }
                } else if (exp instanceof ClassExpression) {
                    arrayType = AnnotationClassValue.class;
                    ClassExpression classExp = ((ClassExpression) exp);
                    String typeName;
                    if (classExp.getType().isArray()) {
                        typeName = "[L" + classExp.getType().getComponentType().getName() + ";";
                    } else {
                        typeName = classExp.getType().getName();
                    }
                    converted.add(new AnnotationClassValue<>(typeName));
                }
            }
            // for some reason this is necessary to produce correct array type in Groovy
            return ConversionService.SHARED.convert(converted, Array.newInstance(arrayType, 0).getClass())
                .orElse(null);
        } else if (annotationValue instanceof VariableExpression) {
            VariableExpression ve = (VariableExpression) annotationValue;
            Variable variable = ve.getAccessedVariable();
            if (variable != null && variable.hasInitialExpression()) {
                return readAnnotationValue(originatingElement, member, memberName, variable.getInitialExpression());
            }
        } else if (annotationValue != null) {
            if (ClassUtils.isJavaLangType(annotationValue.getClass())) {
                return annotationValue;
            }
        }
        return null;
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
    protected OptionalValues<?> getAnnotationValues(AnnotatedNode originatingElement, AnnotatedNode member, Class<?> annotationType) {
        if (member != null) {
            final List<AnnotationNode> anns = member.getAnnotations(ClassHelper.make(annotationType));
            if (CollectionUtils.isNotEmpty(anns)) {
                AnnotationNode ann = anns.get(0);
                Map<CharSequence, Object> converted = new LinkedHashMap<>();
                ann.getMembers().forEach((key, value) ->
                    readAnnotationRawValues(originatingElement, annotationType.getName(), member, key, value, converted)
                );
                return OptionalValues.of(Object.class, converted);
            }
        }
        return OptionalValues.empty();
    }

    @Override
    protected String getAnnotationMemberName(AnnotatedNode member) {
        return ((MethodNode) member).getName();
    }

    private void populateTypeHierarchy(ClassNode classNode, List<AnnotatedNode> hierarchy) {
        while (classNode != null) {
            ClassNode[] interfaces = classNode.getInterfaces();
            for (ClassNode anInterface : interfaces) {
                if (!hierarchy.contains(anInterface) && !anInterface.getName().equals(GroovyObject.class.getName())) {
                    hierarchy.add(anInterface);
                    populateTypeHierarchy(anInterface, hierarchy);
                }
            }
            classNode = classNode.getSuperClass();
            if (classNode != null) {
                if (classNode.equals(ClassHelper.OBJECT_TYPE) || classNode.getName().equals(Script.class.getName()) || classNode.getName().equals(GroovyObjectSupport.class.getName())) {
                    break;
                } else {
                    hierarchy.add(classNode);
                }
            } else {
                break;
            }
        }
    }

    private List<MethodNode> findOverriddenMethods(MethodNode methodNode) {
        List<MethodNode> overriddenMethods = new ArrayList<>();
        ClassNode classNode = methodNode.getDeclaringClass();

        String methodName = methodNode.getName();
        Map<String, Map<String, ClassNode>> genericsInfo = AstGenericUtils.buildAllGenericElementInfo(classNode, createVisitorContext());

        classLoop:
        while (classNode != null && !classNode.getName().equals(Object.class.getName())) {
            for (ClassNode i : classNode.getAllInterfaces()) {
                for (MethodNode parent : i.getMethods(methodName)) {
                    if (methodOverrides(methodNode, parent, genericsInfo.get(i.getName()))) {
                        overriddenMethods.add(parent);
                    }
                }
            }
            classNode = classNode.getSuperClass();
            if (classNode != null && !classNode.getName().equals(Object.class.getName())) {

                for (MethodNode parent : classNode.getMethods(methodName)) {
                    if (methodOverrides(methodNode, parent, genericsInfo.get(classNode.getName()))) {
                        if (!parent.isPrivate()) {
                            overriddenMethods.add(parent);
                        }
                        if (parent.getAnnotations(ANN_OVERRIDE).isEmpty()) {
                            break classLoop;
                        }
                    }
                }
            }
        }
        return overriddenMethods;
    }

    private boolean methodOverrides(MethodNode child,
                                    MethodNode parent,
                                    Map<String, ClassNode> genericsSpec) {
        Parameter[] childParameters = child.getParameters();
        Parameter[] parentParameters = parent.getParameters();
        if (childParameters.length == parentParameters.length) {
            for (int i = 0, n = childParameters.length; i < n; i += 1) {
                ClassNode aType = childParameters[i].getType();
                ClassNode bType = parentParameters[i].getType();

                if (!aType.equals(bType)) {
                    if (bType.isGenericsPlaceHolder() && genericsSpec != null) {
                        ClassNode classNode = genericsSpec.get(bType.getUnresolvedName());
                        if (!aType.equals(classNode)) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }
}
