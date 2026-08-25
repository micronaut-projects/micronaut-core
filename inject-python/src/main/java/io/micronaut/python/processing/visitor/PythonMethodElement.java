/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.processing.visitor;

import io.micronaut.core.annotation.Experimental;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.micronaut.aop.InterceptorBinding;
import io.micronaut.annotation.processing.visitor.ElementProvider;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PackageElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.inject.ast.UnresolvedTypeKind;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.MethodElementAnnotationsHelper;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.validation.RequiresValidation;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.util.GraalPyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import javax.lang.model.element.Element;

/**
 * Represents a Python method/function as a Micronaut {@link MethodElement}.
 * <p>
 * This class wraps a {@link FunctionDef} node of the Python AST, providing full
 * MethodElement interface implementation including parameters, return types, and visibility.
 * </p>
 *
 * @author Micronaut Team
 * @see FunctionDef
 * @see <a href="https://docs.python.org/3/library/ast.html#ast.FunctionDef">Python AST FunctionDef</a>
 * @since 5.2.0
 */
@SuppressWarnings("checkstyle:InnerTypeLast")
@Experimental
public non-sealed class PythonMethodElement extends AbstractPythonElement implements MethodElement, ElementProvider {
    private static final String ANN_CONSTRAINT = "jakarta.validation.Constraint";
    private static final String ANN_VALID = "jakarta.validation.Valid";

    private final PythonProcessingEnvironment environment;
    private final ClassElement declaringType;
    private final ClassElement owningType;
    private final ClassElement returnType;
    private final ParameterElement[] parameters;
    private final MethodElementAnnotationsHelper helper;

    private ClassElement resolvedGenericReturnType;
    private ElementAnnotationMetadata resolvedMergedMethodAnnotationMetadata;
    private AnnotationMetadata resolvedInheritedMethodAnnotationMetadata;
    private Collection<MethodElement> resolvedOverriddenMethods;
    private ParameterElement[] resolvedParameters;

    /**
     * Constructs a new {@code PythonMethodElement} from the given {@code FunctionDef}.
     *
     * @param functionDef     the function definition node; must not be {@code null}
     * @param environment     the Python processing environment; must not be {@code null}
     * @param declaringType   the class that declares this method; must not be {@code null}
     * @param owningType      the class that owns this method (may be a subclass); must not be {@code null}
     * @param metadataFactory the annotation metadata factory; must not be {@code null}
     * @throws NullPointerException if any parameter is {@code null}
     */
    public PythonMethodElement(FunctionDef functionDef,
                               PythonProcessingEnvironment environment,
                               ClassElement declaringType,
                               ClassElement owningType,
                               ElementAnnotationMetadataFactory metadataFactory) {
        super(Objects.requireNonNull(functionDef, "FunctionDef cannot be null").name(), functionDef, metadataFactory);
        this.environment = Objects.requireNonNull(environment, "PythonProcessingEnvironment cannot be null");
        this.declaringType = Objects.requireNonNull(declaringType, "Declaring type cannot be null");
        this.owningType = Objects.requireNonNull(owningType, "Owning type cannot be null");

        // Resolve return type
        this.returnType = resolveReturnType(functionDef);

        // Create parameter elements
        this.parameters = createParameters(functionDef);
        this.helper = new MethodElementAnnotationsHelper(this, metadataFactory);
        if (requiresValidation()) {
            annotate(RequiresValidation.class);
        }
    }

    @Override
    public String getDescription(boolean simple) {
        ClassElement owner = getOwningType();
        StringBuilder description = new StringBuilder();
        String indent = "";
        if (owner != null) {
            description.append("class ");
            description.append(owner.getDescription(simple)).append(":").append(System.lineSeparator());
            indent = "      ";
        }
        ClassElement genericReturnType = getDescriptionReturnType();
        description
            .append(indent)
            .append(isAsync() ? "async def " : "def ").append(getName())
            .append("(")
            .append(isStatic() ? "cls" : "self")
            .append(")")
            .append(genericReturnType.isVoid() ? "" : " -> " + genericReturnType.getDescription(simple));
        return description.toString();
    }

    @Override
    public @NonNull MethodElement withNewParameters(@NotNull @NonNull ParameterElement... newParameters) {
        return MethodElement.super.withNewParameters(newParameters);
    }

    @Override
    public @NonNull MethodElement withNewOwningType(@NonNull ClassElement owningType) {
        PythonMethodElement methodElement = new PythonMethodElement(
            getNativeType(),
            environment,
            declaringType,
            owningType,
            elementAnnotationMetadataFactory
        );
        copyValues(methodElement);
        return methodElement;
    }

    @Override
    public boolean isAbstract() {
        return getNativeType().isAbstract();
    }

    /**
     * Returns whether this method was declared with {@code async def}.
     *
     * @return Whether this method was declared with {@code async def}.
     */
    public boolean isAsync() {
        return getNativeType().isAsync();
    }

    @Override
    protected MutableAnnotationMetadataDelegate<?> getAnnotationMetadataToWrite() {
        return getOwnMethodAnnotationMetadata();
    }

    @Override
    public ElementAnnotationMetadata getMethodAnnotationMetadata() {
        if (resolvedMergedMethodAnnotationMetadata == null) {
            ElementAnnotationMetadata declaredMethodAnnotationMetadata = getOwnMethodAnnotationMetadata();
            AnnotationMetadata inheritedAnnotationMetadata = getInheritedMethodAnnotationMetadata();
            if (inheritedAnnotationMetadata.isEmpty()) {
                resolvedMergedMethodAnnotationMetadata = declaredMethodAnnotationMetadata;
            } else {
                resolvedMergedMethodAnnotationMetadata = elementAnnotationMetadataFactory.buildMutable(
                    new AnnotationMetadataHierarchy(inheritedAnnotationMetadata, declaredMethodAnnotationMetadata)
                );
            }
        }
        return resolvedMergedMethodAnnotationMetadata;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return helper.getAnnotationMetadata(presetAnnotationMetadata);
    }

    @Override
    public boolean hasStereotype(@Nullable Class<? extends Annotation> annotation) {
        return helper.getAnnotationMetadata(presetAnnotationMetadata).hasStereotype(annotation)
            || (!declaringType.equals(owningType) && declaringType.hasStereotype(annotation));
    }

    @Override
    public boolean hasStereotype(@Nullable String annotation) {
        return helper.getAnnotationMetadata(presetAnnotationMetadata).hasStereotype(annotation)
            || (!declaringType.equals(owningType) && declaringType.hasStereotype(annotation));
    }

    @Override
    public AnnotationMetadata getTargetAnnotationMetadata() {
        AnnotationMetadata targetAnnotationMetadata = getMethodAnnotationMetadata().getTargetAnnotationMetadata();
        AnnotationMetadata overriddenMethodAnnotationMetadata = getOverriddenMethodAnnotationMetadata();
        if (!overriddenMethodAnnotationMetadata.isEmpty()) {
            // Match Java/Groovy/Kotlin semantics for overridden methods: annotations inherited from
            // the overridden method must be visible to hasAnnotation, but not to hasDeclaredAnnotation.
            // This keeps the source-level declaration boundary intact while still letting downstream
            // method metadata consumers see inherited AOP and executable metadata.
            targetAnnotationMetadata = new AnnotationMetadataHierarchy(
                toNonDeclaredAnnotationMetadata(overriddenMethodAnnotationMetadata),
                targetAnnotationMetadata
            );
        }
        if (!declaringType.equals(owningType) && !declaringType.getAnnotationMetadata().isEmpty()) {
            // Inherited methods must retain class-level metadata from the type that declared them.
            // The bean definition writer first checks method.hasStereotype(Executable) before it
            // checks method.getDeclaringType().hasStereotype(Executable), so class-level @Executable
            // on a Python superclass has to be visible as inherited method metadata.
            targetAnnotationMetadata = new AnnotationMetadataHierarchy(declaringType, targetAnnotationMetadata);
        }
        if (!owningType.getAnnotationMetadata().isEmpty()) {
            // Keep inherited/owning type metadata in the annotation metadata model instead of copying
            // annotations onto generated Java stubs. The stubs are only a Java shape for processing;
            // copying annotations there would make declared/inherited metadata checks diverge from the
            // other language implementations.
            targetAnnotationMetadata = new AnnotationMetadataHierarchy(owningType, targetAnnotationMetadata);
        }
        return targetAnnotationMetadata;
    }

    private AnnotationMetadata toNonDeclaredAnnotationMetadata(AnnotationMetadata source) {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        for (String annotationName : source.getAnnotationNames()) {
            source.findAnnotation(annotationName)
                .ifPresent(annotationValue -> metadata.addAnnotation(annotationValue.getAnnotationName(), annotationValue.getValues()));
        }
        return metadata;
    }

    private ElementAnnotationMetadata getOwnMethodAnnotationMetadata() {
        return helper.getMethodAnnotationMetadata(presetAnnotationMetadata);
    }

    private AnnotationMetadata getInheritedMethodAnnotationMetadata() {
        if (resolvedInheritedMethodAnnotationMetadata == null) {
            resolvedInheritedMethodAnnotationMetadata = findInheritedMethod()
                .map(method -> (AnnotationMetadata) method.getMethodAnnotationMetadata())
                .orElse(AnnotationMetadata.EMPTY_METADATA);
        }
        return resolvedInheritedMethodAnnotationMetadata;
    }

    private AnnotationMetadata getOverriddenMethodAnnotationMetadata() {
        AnnotationMetadata inheritedMetadata = AnnotationMetadata.EMPTY_METADATA;
        for (MethodElement overriddenMethod : getOverriddenMethods()) {
            AnnotationMetadata methodMetadata = overriddenMethod.getMethodAnnotationMetadata();
            if (methodMetadata.isEmpty()) {
                continue;
            }
            inheritedMetadata = inheritedMetadata.isEmpty()
                ? methodMetadata
                : new AnnotationMetadataHierarchy(methodMetadata, inheritedMetadata);
        }
        return inheritedMetadata;
    }

    private Optional<MethodElement> findInheritedMethod() {
        if (!isAbstract() || !declaringType.equals(owningType)) {
            return Optional.empty();
        }
        List<MethodElement> candidates = new ArrayList<>();
        owningType.getSuperType()
            .ifPresent(superType -> candidates.addAll(superType.getEnclosedElements(ElementQuery.ALL_METHODS)));
        for (ClassElement anInterface : owningType.getInterfaces()) {
            candidates.addAll(anInterface.getEnclosedElements(ElementQuery.ALL_METHODS));
        }
        for (MethodElement candidate : candidates) {
            if (candidate == this || !getName().equals(candidate.getName())) {
                continue;
            }
            if (overrides(candidate) || isSubSignature(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private boolean requiresValidation() {
        if (hasValidationAnnotation(getAnnotationMetadata()) || hasValidationAnnotation(getGenericReturnType())) {
            return true;
        }
        for (ParameterElement parameter : parameters) {
            if (hasValidationAnnotation(parameter.getAnnotationMetadata())
                || hasValidationAnnotation(parameter.getGenericType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasValidationAnnotation(AnnotationMetadata metadata) {
        return metadata.hasStereotype(ANN_CONSTRAINT) || metadata.hasAnnotation(ANN_VALID);
    }

    private static boolean hasValidationAnnotation(ClassElement classElement) {
        if (hasValidationAnnotation(classElement.getAnnotationMetadata())) {
            return true;
        }
        for (ClassElement typeArgument : classElement.getTypeArguments().values()) {
            if (hasValidationAnnotation(typeArgument)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isReflectionRequired() {
        // since we are in charge of Python stub generation, this doesn't make sense
        return false;
    }

    @Override
    public boolean isReflectionRequired(ClassElement callingType) {
        // since we are in charge of Python stub generation, this doesn't make sense
        return false;
    }

    @Override
    public boolean isStatic() {
        // Use the isStatic field from FunctionDef which is set during parsing
        return getNativeType().isStatic();
    }

    /**
     * Returns the native {@link FunctionDef} object that backs this element.
     *
     * @return the underlying {@code FunctionDef} node
     */
    @Override
    public FunctionDef getNativeType() {
        return (FunctionDef) super.getNativeType();
    }

    @Override
    public ClassElement getReturnType() {
        return returnType;
    }

    @Override
    public boolean isDeclaredNullable() {
        return getAnnotationMetadata().hasDeclaredStereotype(AnnotationUtil.NULLABLE)
            || getReturnType().isDeclaredNullable();
    }

    @Override
    public boolean isNullable() {
        return getAnnotationMetadata().hasStereotype(AnnotationUtil.NULLABLE)
            || getReturnType().isNullable();
    }

    @Override
    public boolean isNonNull() {
        return getAnnotationMetadata().hasStereotype(AnnotationUtil.NON_NULL)
            || getReturnType().isNonNull();
    }

    @Override
    public boolean isDeclaredNonNull() {
        return getAnnotationMetadata().hasDeclaredStereotype(AnnotationUtil.NON_NULL)
            || getReturnType().isDeclaredNonNull();
    }

    @Override
    public ParameterElement[] getParameters() {
        if (resolvedParameters == null) {
            resolvedParameters = resolveParameters();
        }
        return resolvedParameters.clone();
    }

    @Override
    public MethodElement withParameters(ParameterElement... newParameters) {
        // Since PythonMethodElement is based on parsed Python code,
        // we create a synthetic MethodElement with the new parameters
        return new PythonMethodElement(
            getNativeType(),
            environment,
            declaringType,
            owningType,
            elementAnnotationMetadataFactory
        ) {
            @Override
            public ParameterElement[] getParameters() {
                return newParameters;
            }
        };
    }

    @Override
    public boolean isPublic() {
        // Python considers methods/attributes starting with '_' as private
        return !getName().startsWith("_");
    }

    @Override
    public boolean isPrivate() {
        return getName().startsWith("_");
    }

    @Override
    public ClassElement getDeclaringType() {
        return declaringType;
    }

    @Override
    public ClassElement getOwningType() {
        return owningType;
    }

    final boolean requiresResolvedParameterType() {
        // Keep ordinary getType() erased like Java/Groovy, but inherited generic AOP methods need
        // resolved parameter signatures so proxy override detection does not drop the introduced method.
        return !declaringType.equals(owningType)
            && owningType.hasStereotype(InterceptorBinding.class);
    }

    @Override
    public Collection<MethodElement> getOverriddenMethods() {
        if (resolvedOverriddenMethods == null) {
            resolvedOverriddenMethods = resolveOverriddenMethods();
        }
        return resolvedOverriddenMethods;
    }

    @Override
    public ClassElement getGenericReturnType() {
        return resolveGenericReturnType(getNativeType());
    }

    private ParameterElement[] resolveParameters() {
        ParameterElement[] resolved = parameters;
        for (MethodElement overriddenMethod : getOverriddenMethods()) {
            ParameterElement[] overriddenParameters = overriddenMethod.getParameters();
            if (overriddenParameters.length != resolved.length) {
                continue;
            }
            ParameterElement[] merged = null;
            for (int i = 0; i < resolved.length; i++) {
                AnnotationMetadata inheritedMetadata = overriddenParameters[i].getAnnotationMetadata();
                if (inheritedMetadata.isEmpty()) {
                    continue;
                }
                if (merged == null) {
                    merged = resolved.clone();
                }
                merged[i] = resolved[i].withAnnotationMetadata(
                    // Validation visitors mutate parameter metadata while inheriting constraints.
                    // Keep the declared child metadata concrete here; a hierarchy as the declared
                    // child cannot be mutated by AbstractAnnotationMetadataBuilder.
                    new AnnotationMetadataHierarchy(true, inheritedMetadata, MutableAnnotationMetadata.of(resolved[i].getAnnotationMetadata()))
                );
            }
            if (merged != null) {
                resolved = merged;
            }
        }
        return resolved;
    }

    private Collection<MethodElement> resolveOverriddenMethods() {
        List<MethodElement> candidates = new ArrayList<>();
        declaringType.getSuperType()
            .ifPresent(superType -> candidates.addAll(superType.getEnclosedElements(ElementQuery.ALL_METHODS)));
        for (ClassElement anInterface : declaringType.getInterfaces()) {
            candidates.addAll(anInterface.getEnclosedElements(ElementQuery.ALL_METHODS));
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<MethodElement> overriddenMethods = new ArrayList<>();
        for (MethodElement candidate : candidates) {
            if (candidate != this && isSubSignature(candidate, parameters)) {
                overriddenMethods.add(candidate);
            }
        }
        return overriddenMethods.isEmpty() ? List.of() : List.copyOf(overriddenMethods);
    }

    private boolean isSubSignature(MethodElement overridden, ParameterElement[] currentParameters) {
        if (!getName().equals(overridden.getName()) || overridden.getParameters().length != currentParameters.length) {
            return false;
        }
        ParameterElement[] overriddenParameters = overridden.getParameters();
        for (int i = 0; i < overriddenParameters.length; i++) {
            if (!currentParameters[i].getGenericType().isAssignable(overriddenParameters[i].getGenericType())) {
                return false;
            }
        }
        return getReturnType().getGenericType().isAssignable(overridden.getReturnType().getGenericType());
    }

    private ClassElement resolveGenericReturnType(FunctionDef functionDef) {
        if (resolvedGenericReturnType == null) {

            ReturnDef returnDef = functionDef.returnType();
            if (returnDef != null && returnDef.typeAnnotation() != null) {
                ClassElement baseType = GraalPyUtil.resolvePythonTypeToJava(
                    returnDef.typeAnnotation(),
                    environment.visitorContext(),
                    getBoundGenericTypes()
                );

                resolvedGenericReturnType = asyncBridgeReturnType(functionDef, withDeclaredReturnAnnotationMetadata(returnDef, baseType));
            } else {
                resolvedGenericReturnType = asyncBridgeReturnType(functionDef, unannotatedReturnType(functionDef));
            }
            if (resolvedGenericReturnType instanceof AbstractPythonClassElement pythonClassElement) {
                resolvedGenericReturnType = pythonClassElement.withTypeAnnotationsKey(functionDef);
            }
        }
        return resolvedGenericReturnType;
    }

    private ClassElement resolveReturnType(FunctionDef functionDef) {
        ReturnDef returnDef = functionDef.returnType();
        if (returnDef != null && returnDef.typeAnnotation() != null) {
            ClassElement baseType = GraalPyUtil.resolvePythonTypeToJava(
                returnDef.typeAnnotation(),
                environment.visitorContext(),
                getRawBoundGenericTypes()
            );

            baseType = withDeclaredReturnAnnotationMetadata(returnDef, baseType);
            if (baseType instanceof AbstractPythonClassElement pythonClassElement) {
                return pythonClassElement.withTypeAnnotationsKey(functionDef);
            }

            return asyncBridgeReturnType(functionDef, baseType);
        }
        return asyncBridgeReturnType(functionDef, unannotatedReturnType(functionDef));
    }

    private ClassElement getDescriptionReturnType() {
        if (!isAsync()) {
            return getGenericReturnType();
        }
        ReturnDef returnDef = getNativeType().returnType();
        if (returnDef != null && returnDef.typeAnnotation() != null) {
            return withDeclaredReturnAnnotationMetadata(
                returnDef,
                GraalPyUtil.resolvePythonTypeToJava(
                    returnDef.typeAnnotation(),
                    environment.visitorContext(),
                    getBoundGenericTypes()
                )
            );
        }
        return unannotatedReturnType(getNativeType());
    }

    private ClassElement unannotatedReturnType(FunctionDef functionDef) {
        if (functionDef.hasReturnValue()) {
            return environment.visitorContext().getClassElement(Object.class).orElse(ClassElement.of(Object.class));
        }
        return PrimitiveElement.VOID;
    }

    private ClassElement asyncBridgeReturnType(FunctionDef functionDef, ClassElement awaitedType) {
        if (!functionDef.isAsync()) {
            return awaitedType;
        }
        ClassElement completionStage = environment.visitorContext()
            .getClassElement(CompletionStage.class.getName())
            .orElseGet(() -> ClassElement.of(CompletionStage.class));
        ClassElement stageValueType = asyncStageValueType(awaitedType);
        try {
            return completionStage.withTypeArguments(Map.of("T", stageValueType));
        } catch (UnsupportedOperationException e) {
            return ClassElement.of(CompletionStage.class, AnnotationMetadata.EMPTY_METADATA, Map.of("T", stageValueType));
        }
    }

    private ClassElement asyncStageValueType(ClassElement awaitedType) {
        if (awaitedType.isVoid()) {
            return ClassElement.of(Void.class);
        }
        if (!awaitedType.isPrimitive()) {
            return awaitedType;
        }
        return switch (awaitedType.getName()) {
            case "boolean" -> ClassElement.of(Boolean.class);
            case "byte" -> ClassElement.of(Byte.class);
            case "char" -> ClassElement.of(Character.class);
            case "double" -> ClassElement.of(Double.class);
            case "float" -> ClassElement.of(Float.class);
            case "int" -> ClassElement.of(Integer.class);
            case "long" -> ClassElement.of(Long.class);
            case "short" -> ClassElement.of(Short.class);
            default -> awaitedType;
        };
    }

    private ClassElement withDeclaredReturnAnnotationMetadata(ReturnDef returnDef, ClassElement baseType) {
        AnnotationMetadata annotationMetadata = environment.visitorContext()
            .getAnnotationMetadataBuilder()
            .buildDeclared(returnDef);
        if (annotationMetadata.isEmpty()) {
            return baseType;
        }
        return withReturnAnnotationMetadata(baseType, annotationMetadata);
    }

    private ClassElement withReturnAnnotationMetadata(ClassElement baseType, AnnotationMetadata annotationMetadata) {
        AnnotationMetadata typeAnnotationMetadata = baseType.getTypeAnnotationMetadata();
        AnnotationMetadata returnAnnotationMetadata = typeAnnotationMetadata.isEmpty()
            ? annotationMetadata
            : new AnnotationMetadataHierarchy(true, typeAnnotationMetadata, annotationMetadata);
        return new ReturnTypeAnnotatedClassElement(
            baseType,
            elementAnnotationMetadataFactory.buildMutable(returnAnnotationMetadata)
        );
    }

    private record ReturnTypeAnnotatedClassElement(
        ClassElement delegate,
        ElementAnnotationMetadata typeAnnotationMetadata
    ) implements ClassElement {

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return new AnnotationMetadataHierarchy(true, delegate.getAnnotationMetadata(), typeAnnotationMetadata);
        }

        @Override
        public MutableAnnotationMetadataDelegate<AnnotationMetadata> getTypeAnnotationMetadata() {
            return typeAnnotationMetadata;
        }

        @Override
        public <T extends Annotation> ClassElement annotate(String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
            typeAnnotationMetadata.annotate(annotationType, consumer);
            return this;
        }

        @Override
        public <T extends Annotation> ClassElement annotate(AnnotationValue<T> annotationValue) {
            typeAnnotationMetadata.annotate(annotationValue);
            return this;
        }

        @Override
        public ClassElement removeAnnotation(String annotationType) {
            typeAnnotationMetadata.removeAnnotation(annotationType);
            return this;
        }

        @Override
        public <T extends Annotation> ClassElement removeAnnotationIf(Predicate<AnnotationValue<T>> predicate) {
            typeAnnotationMetadata.removeAnnotationIf(predicate);
            return this;
        }

        @Override
        public ClassElement removeStereotype(String annotationType) {
            typeAnnotationMetadata.removeStereotype(annotationType);
            return this;
        }

        @Override
        public boolean isAssignable(String type) {
            return delegate.isAssignable(type);
        }

        @Override
        public boolean isAssignable(ClassElement type) {
            return delegate.isAssignable(type);
        }

        @Override
        public boolean isAssignable(Class<?> type) {
            return delegate.isAssignable(type);
        }

        @Override
        public boolean isTypeVariable() {
            return delegate.isTypeVariable();
        }

        @Override
        public boolean hasUnresolvedTypes(UnresolvedTypeKind... kind) {
            return delegate.hasUnresolvedTypes(kind);
        }

        @Override
        public boolean isGenericPlaceholder() {
            return delegate.isGenericPlaceholder();
        }

        @Override
        public boolean isWildcard() {
            return delegate.isWildcard();
        }

        @Override
        public boolean isRawType() {
            return delegate.isRawType();
        }

        @Override
        public boolean isOptional() {
            return delegate.isOptional();
        }

        @Override
        public Optional<ClassElement> getOptionalValueType() {
            return delegate.getOptionalValueType();
        }

        @Override
        public boolean isContainerType() {
            return delegate.isContainerType();
        }

        @Override
        public boolean isRecord() {
            return delegate.isRecord();
        }

        @Override
        public boolean isInner() {
            return delegate.isInner();
        }

        @Override
        public boolean isEnum() {
            return delegate.isEnum();
        }

        @Override
        public ClassElement toArray() {
            return new ReturnTypeAnnotatedClassElement(delegate.toArray(), typeAnnotationMetadata);
        }

        @Override
        public ClassElement fromArray() {
            return new ReturnTypeAnnotatedClassElement(delegate.fromArray(), typeAnnotationMetadata);
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public boolean isPackagePrivate() {
            return delegate.isPackagePrivate();
        }

        @Override
        public boolean isSynthetic() {
            return delegate.isSynthetic();
        }

        @Override
        public boolean isProtected() {
            return delegate.isProtected();
        }

        @Override
        public boolean isPublic() {
            return delegate.isPublic();
        }

        @Override
        public Set<ElementModifier> getModifiers() {
            return delegate.getModifiers();
        }

        @Override
        public boolean isAbstract() {
            return delegate.isAbstract();
        }

        @Override
        public boolean isStatic() {
            return delegate.isStatic();
        }

        @Override
        public Optional<String> getDocumentation(boolean parseContent) {
            return delegate.getDocumentation(parseContent);
        }

        @Override
        public boolean isPrivate() {
            return delegate.isPrivate();
        }

        @Override
        public boolean isFinal() {
            return delegate.isFinal();
        }

        @Override
        public String getDescription(boolean simple) {
            return delegate.getDescription(simple);
        }

        @Override
        public Object getNativeType() {
            return delegate.getNativeType();
        }

        @Override
        public boolean isPrimitive() {
            return delegate.isPrimitive();
        }

        @Override
        public boolean isVoid() {
            return delegate.isVoid();
        }

        @Override
        public boolean isArray() {
            return delegate.isArray();
        }

        @Override
        public int getArrayDimensions() {
            return delegate.getArrayDimensions();
        }

        @Override
        public boolean isInterface() {
            return delegate.isInterface();
        }

        @Override
        public Optional<ClassElement> getSuperType() {
            return delegate.getSuperType();
        }

        @Override
        public Collection<ClassElement> getInterfaces() {
            return delegate.getInterfaces();
        }

        @Override
        public PackageElement getPackage() {
            return delegate.getPackage();
        }

        @Override
        public List<PropertyElement> getBeanProperties() {
            return delegate.getBeanProperties();
        }

        @Override
        public List<PropertyElement> getSyntheticBeanProperties() {
            return delegate.getSyntheticBeanProperties();
        }

        @Override
        public List<PropertyElement> getBeanProperties(PropertyElementQuery propertyElementQuery) {
            return delegate.getBeanProperties(propertyElementQuery);
        }

        @Override
        public List<FieldElement> getFields() {
            return delegate.getFields();
        }

        @Override
        public List<MethodElement> getMethods() {
            return delegate.getMethods();
        }

        @Override
        public <T extends io.micronaut.inject.ast.Element> List<T> getEnclosedElements(ElementQuery<T> query) {
            return delegate.getEnclosedElements(query);
        }

        @Override
        public Optional<ClassElement> getEnclosingType() {
            return delegate.getEnclosingType();
        }

        @Override
        public List<? extends ClassElement> getBoundGenericTypes() {
            return delegate.getBoundGenericTypes();
        }

        @Override
        public List<? extends GenericPlaceholderElement> getDeclaredGenericPlaceholders() {
            return delegate.getDeclaredGenericPlaceholders();
        }

        @Override
        public Map<String, ClassElement> getTypeArguments(String type) {
            return delegate.getTypeArguments(type);
        }

        @Override
        public Map<String, ClassElement> getTypeArguments() {
            return delegate.getTypeArguments();
        }

        @Override
        public ClassElement getRawClassElement() {
            return new ReturnTypeAnnotatedClassElement(delegate.getRawClassElement(), typeAnnotationMetadata);
        }

        @Override
        public BeanElementBuilder addAssociatedBean(ClassElement type) {
            return delegate.addAssociatedBean(type);
        }

        @Override
        public List<ConstructorElement> getAccessibleConstructors() {
            return delegate.getAccessibleConstructors();
        }

        @Override
        public List<MethodElement> getAccessibleStaticCreators() {
            return delegate.getAccessibleStaticCreators();
        }

        @Override
        public ClassElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
            return new ReturnTypeAnnotatedClassElement(
                delegate.withAnnotationMetadata(annotationMetadata),
                typeAnnotationMetadata
            );
        }

        @Override
        public ClassElement withTypeArguments(Map<String, ClassElement> typeArguments) {
            return new ReturnTypeAnnotatedClassElement(
                delegate.withTypeArguments(typeArguments),
                typeAnnotationMetadata
            );
        }

        @Override
        public ClassElement withTypeArguments(Collection<ClassElement> typeArguments) {
            return new ReturnTypeAnnotatedClassElement(
                delegate.withTypeArguments(typeArguments),
                typeAnnotationMetadata
            );
        }
    }

    private ParameterElement[] createParameters(FunctionDef functionDef) {
        List<ArgumentDef> arguments = functionDef.arguments().arguments();
        boolean isStatic = functionDef.isStatic();
        int size = arguments.size();
        if (size == 0) {
            return ParameterElement.ZERO_PARAMETER_ELEMENTS;
        }
        List<ParameterElement> parameters = new ArrayList<>(isStatic ? size - 1 : size);

        for (int i = isStatic ? 1 : 0; i < size; i++) {
            ArgumentDef argDef = arguments.get(i);
            parameters.add(new PythonParameterElement(argDef, environment, this, getElementAnnotationMetadataFactory()));
        }

        return parameters.toArray(ParameterElement.ZERO_PARAMETER_ELEMENTS);
    }

    @Override
    public Optional<String> getDocumentation(boolean parseContent) {
        String doc = getNativeType().documentation();
        if (doc == null) {
            return Optional.empty();
        }
        if (parseContent) {
            // Parse Python docstring to extract main description
            return Optional.of(GraalPyUtil.parsePythonDocstring(doc));
        }
        return Optional.of(doc);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PythonMethodElement that = (PythonMethodElement) o;

        return that.getNativeType().name().equals(getNativeType().name()) &&
            declaringType.equals(that.declaringType) &&
            owningType.equals(that.owningType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getNativeType().name(), declaringType, owningType);
    }

    @Override
    protected AbstractPythonElement copyThis() {
        return new PythonMethodElement(
            getNativeType(),
            environment,
            declaringType,
            owningType,
            getElementAnnotationMetadataFactory()
        );
    }

    @Override
    public MethodElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        PythonMethodElement methodElement = new PythonMethodElement(
            getNativeType(),
            environment,
            declaringType,
            owningType,
            getElementAnnotationMetadataFactory()
        );
        methodElement.presetAnnotationMetadata = annotationMetadata;
        return methodElement;
    }

    @Override
    public List<? extends GenericPlaceholderElement> getDeclaredTypeVariables() {
        List<TypeVar> typeVars = getNativeType().typeParams();
        if (typeVars.isEmpty()) {
            return Collections.emptyList();
        }

        List<GenericPlaceholderElement> placeholders = new ArrayList<>(typeVars.size());
        for (TypeVar typeVar : typeVars) {
            placeholders.add(new PythonGenericPlaceholderElement(typeVar, environment, Collections.emptyList(), this));
        }
        return placeholders;
    }

    final Map<String, ClassElement> getBoundGenericTypes() {
        Map<String, Map<String, ClassElement>> allGenerics = getOwningType().getAllTypeArguments();
        ClassDef declaringClass = getNativeType().declaringClass();
        Map<String, ClassElement> declaringGenerics = declaringClass != null
            ? allGenerics.getOrDefault(declaringClass.qualifiedName(), Map.of())
            : Map.of();
        boolean declaredOnOwningType = getDeclaringType().getName().equals(getOwningType().getName());
        if (declaredOnOwningType
            && getOwningType() instanceof PythonClassElement pythonClassElement
            && !pythonClassElement.hasExplicitTypeArguments()) {
            declaringGenerics = declaredGenericBindings(true);
        }
        if (declaringGenerics.isEmpty()) {
            declaringGenerics = declaredGenericBindings(declaredOnOwningType);
        }
        List<? extends GenericPlaceholderElement> methodTypeVariables = getDeclaredTypeVariables();
        if (declaringGenerics.isEmpty() && methodTypeVariables.isEmpty()) {
            return Map.of();
        }
        Map<String, ClassElement> boundGenerics = new LinkedHashMap<>(declaringGenerics);
        for (GenericPlaceholderElement methodTypeVariable : methodTypeVariables) {
            boundGenerics.put(methodTypeVariable.getVariableName(), methodTypeVariable);
        }
        return boundGenerics;
    }

    private Map<String, ClassElement> getRawBoundGenericTypes() {
        boolean declaredOnOwningType = getDeclaringType().getName().equals(getOwningType().getName());
        boolean preservePlaceholders = declaredOnOwningType
            && getOwningType() instanceof PythonClassElement pythonClassElement
            && !pythonClassElement.hasExplicitTypeArguments();
        Map<String, ClassElement> declaringGenerics = declaredGenericBindings(preservePlaceholders);
        List<? extends GenericPlaceholderElement> methodTypeVariables = getDeclaredTypeVariables();
        if (declaringGenerics.isEmpty() && methodTypeVariables.isEmpty()) {
            return Map.of();
        }
        Map<String, ClassElement> boundGenerics = new LinkedHashMap<>(declaringGenerics);
        for (GenericPlaceholderElement methodTypeVariable : methodTypeVariables) {
            boundGenerics.put(methodTypeVariable.getVariableName(), methodTypeVariable);
        }
        return boundGenerics;
    }

    private Map<String, ClassElement> declaredGenericBindings(boolean preservePlaceholders) {
        List<? extends GenericPlaceholderElement> placeholders = getDeclaringType().getDeclaredGenericPlaceholders();
        if (placeholders.isEmpty()) {
            return Map.of();
        }
        Map<String, ClassElement> bindings = new LinkedHashMap<>(placeholders.size());
        for (GenericPlaceholderElement placeholder : placeholders) {
            bindings.put(
                placeholder.getVariableName(),
                preservePlaceholders ? placeholder : firstBound(placeholder)
            );
        }
        return bindings;
    }

    private static ClassElement firstBound(GenericPlaceholderElement placeholder) {
        List<? extends ClassElement> bounds = placeholder.getBounds();
        if (bounds.isEmpty()) {
            return ClassElement.of(Object.class);
        }
        return bounds.getFirst();
    }

    @Override
    public @Nullable Element element() {
        return environment.originatingElement();
    }
}
