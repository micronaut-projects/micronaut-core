/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.expressions.parser.ast.access;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.sourcegen.model.TypeDef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.isAssignable;

/**
 * Class representing candidate method used in evaluated expression.
 * Encapsulates logic determining whether invocation of method in expression
 * with concrete arguments matches list of parameters of concrete method.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
final class CandidateMethod {
    private final MethodElement methodElement;
    private final List<ClassElement> parameterTypes;
    private final List<ClassElement> argumentTypes;

    private int varargsIndex = -1;

    public CandidateMethod(MethodElement methodElement, List<ClassElement> argumentTypes) {
        this.methodElement = methodElement;
        this.argumentTypes = argumentTypes;
        this.parameterTypes = Arrays.stream(methodElement.getParameters())
                                  .map(ParameterElement::getType)
                                  .toList();
    }

    public CandidateMethod(MethodElement methodElement) {
        this(methodElement, Collections.emptyList());
    }

    /**
     * @return The method element.
     */
    public MethodElement getMethodElement() {
        return methodElement;
    }

    /**
     * Whether candidate method is vargars method.
     *
     * @return true if it is
     */
    public boolean isVarArgs() {
        return getVarargsIndex() != -1;
    }

    /**
     * Returns index of varargs parameter. If method has no varargs
     * parameter, -1 is returned.
     *
     * @return varargs index or -1
     */
    public int getVarargsIndex() {
        return varargsIndex;
    }

    /**
     * @return Returns candidate method return type.
     */
    public TypeDef getReturnType() {
        return TypeDef.erasure(methodElement.getReturnType());
    }

    /**
     * @return Type of class that owns candidate method.
     */
    public TypeDef getOwningType() {
        return TypeDef.erasure(methodElement.getOwningType());
    }

    /**
     * @return last parameter of candidate method.
     */
    public ClassElement getLastParameter() {
        return CollectionUtils.last(parameterTypes);
    }

    /**
     * @return list of candidate method parameters.
     */
    public List<ClassElement> getParameters() {
        return parameterTypes;
    }

    /**
     * Checks list of arguments against list of method parameters to decide whether there is
     * a match. This check also supports varargs resolution for cases when method is explicitly
     * defined as varargs method or when last method parameter is a one-dimensional array.
     *
     * @return
     */
    public boolean isMatching() {
        int totalParams = parameterTypes.size();
        int totalArguments = argumentTypes.size();

        if (totalParams == 0) {
            return totalArguments == 0;
        } else if (totalArguments < totalParams - 1) {
            // list of arguments may be shorter than list of parameters only by 1 element and
            // only in case last parameter is varargs parameter, otherwise method doesn't match
            return false;
        }

        ClassElement lastArgument = CollectionUtils.last(argumentTypes);
        ClassElement lastParameter = getLastParameter();
        boolean varargsCandidate = methodElement.isVarArgs() ||
                                       (lastParameter.isArray() && lastParameter.getArrayDimensions() == 1);

        if (varargsCandidate) {
            // maybe just array argument
            if (totalArguments == totalParams && isAssignable(lastParameter, lastArgument)) {
                return true;
            }

            if (isMatchingVarargs()) {
                this.varargsIndex = calculateVarargsIndex();
                return true;
            }

            return false;
        }

        if (totalArguments != totalParams) {
            return false;
        }

        for (int i = 0; i < parameterTypes.size(); i++) {
            ClassElement argumentType = argumentTypes.get(i);
            ClassElement parameterType = parameterTypes.get(i);

            if (!isAssignable(parameterType, argumentType)) {
                return false;
            }
        }
        return true;
    }

    private boolean isMatchingVarargs() {
        for (int paramIndex = 0; paramIndex < parameterTypes.size(); paramIndex++) {
            ClassElement parameterType = parameterTypes.get(paramIndex);

            boolean isLastParameter = paramIndex == parameterTypes.size() - 1;
            if (isLastParameter) {
                parameterType = parameterType.fromArray();

                if (argumentTypes.size() < paramIndex) {
                    // if we got here it means that last parameter is varargs but methods
                    // arguments list doesn't include an argument for varargs parameter, so
                    // an empty array is used as varargs argument, which is treated as a match
                    return true;
                }

                // check whether all remaining arguments match parameter type
                for (int argIndex = paramIndex; argIndex < argumentTypes.size(); argIndex++) {
                    ClassElement argumentType = argumentTypes.get(argIndex);
                    if (!isAssignable(parameterType, argumentType)) {
                        return false;
                    }
                }

                return true;
            }

            // too little arguments, no match
            if (argumentTypes.size() < paramIndex) {
                return false;
            }

            // no match if argument is not assignable to parameter
            if (!isAssignable(parameterType, argumentTypes.get(paramIndex))) {
                return false;
            }
        }

        return false;
    }

    /**
     * Selects the most specific of the matching candidate methods, following the rules of
     * JLS 15.12.2. Candidates applicable without boxing or varargs expansion are preferred over
     * candidates applicable with boxing, which are preferred over candidates that need varargs
     * expansion. Among the candidates of the same phase, the method whose parameter types are all
     * subtypes of the parameter types of the other candidates is selected.
     *
     * @param candidates the matching candidates, see {@link #isMatching()}
     * @return the most specific candidate or an empty optional if the call is ambiguous
     */
    static Optional<CandidateMethod> selectMostSpecific(List<CandidateMethod> candidates) {
        if (candidates.size() == 1) {
            return Optional.of(candidates.getFirst());
        }
        List<CandidateMethod> applicable = candidates.stream().filter(CandidateMethod::isStrictMatch).toList();
        if (applicable.isEmpty()) {
            applicable = candidates.stream().filter(candidate -> !candidate.isVarArgs()).toList();
        }
        if (applicable.isEmpty()) {
            applicable = candidates;
        }

        List<CandidateMethod> maximallySpecific = new ArrayList<>();
        for (CandidateMethod candidate : applicable) {
            boolean mostSpecific = true;
            for (CandidateMethod other : applicable) {
                if (candidate != other && !candidate.isMoreSpecificThan(other)) {
                    mostSpecific = false;
                    break;
                }
            }
            if (mostSpecific) {
                maximallySpecific.add(candidate);
            }
        }
        if (maximallySpecific.isEmpty()) {
            return Optional.empty();
        }
        // several methods with the same signature, for example the same method inherited from
        // several interfaces: prefer the one owned and declared by the most specific type.
        // Methods owned by unrelated types, such as two expression evaluation context beans, stay ambiguous
        CandidateMethod selected = maximallySpecific.getFirst();
        for (CandidateMethod candidate : maximallySpecific) {
            if (!candidate.hasSameParameterTypes(selected)) {
                return Optional.empty();
            }
            ClassElement owningType = candidate.methodElement.getOwningType();
            ClassElement selectedOwningType = selected.methodElement.getOwningType();
            if (!owningType.getName().equals(selectedOwningType.getName())) {
                if (owningType.isAssignable(selectedOwningType)) {
                    selected = candidate;
                } else if (!selectedOwningType.isAssignable(owningType)) {
                    return Optional.empty();
                }
            } else if (candidate.isMoreSpecificDeclarationThan(selected)) {
                selected = candidate;
            }
        }
        return Optional.of(selected);
    }

    /**
     * Whether the arguments match the parameters of this method without boxing, unboxing or
     * varargs expansion.
     *
     * @return true if they do
     */
    private boolean isStrictMatch() {
        if (isVarArgs() || argumentTypes.size() != parameterTypes.size()) {
            return false;
        }
        for (int i = 0; i < parameterTypes.size(); i++) {
            if (!isSubtype(argumentTypes.get(i), parameterTypes.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether this method is at least as specific as the other method for the arguments of
     * the invocation: each parameter type of this method is a subtype of the corresponding
     * parameter type of the other method.
     *
     * @param other the other candidate
     * @return true if this method is at least as specific
     */
    private boolean isMoreSpecificThan(CandidateMethod other) {
        int arity = Math.max(argumentTypes.size(), Math.max(parameterTypes.size(), other.parameterTypes.size()));
        if (!isVarArgs() && !other.isVarArgs()) {
            if (parameterTypes.size() != other.parameterTypes.size()) {
                return false;
            }
            arity = parameterTypes.size();
        }
        for (int i = 0; i < arity; i++) {
            if (!isSubtype(getParameterType(i), other.getParameterType(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether this method is a more specific declaration than the other method with the same signature:
     * it has a more specific (covariant) return type or is declared by a more specific type.
     *
     * @param other the other candidate
     * @return true if it is
     */
    private boolean isMoreSpecificDeclarationThan(CandidateMethod other) {
        ClassElement returnType = methodElement.getReturnType();
        ClassElement otherReturnType = other.methodElement.getReturnType();
        if (!returnType.getName().equals(otherReturnType.getName())) {
            return isSubtype(returnType, otherReturnType);
        }
        return methodElement.getDeclaringType().isAssignable(other.methodElement.getDeclaringType());
    }

    private ClassElement getParameterType(int index) {
        if (isVarArgs() && index >= varargsIndex) {
            return getLastParameter().fromArray();
        }
        return parameterTypes.get(index);
    }

    private boolean hasSameParameterTypes(CandidateMethod other) {
        if (isVarArgs() != other.isVarArgs() || parameterTypes.size() != other.parameterTypes.size()) {
            return false;
        }
        for (int i = 0; i < parameterTypes.size(); i++) {
            ClassElement type = parameterTypes.get(i);
            ClassElement otherType = other.parameterTypes.get(i);
            if (!type.getName().equals(otherType.getName()) || type.getArrayDimensions() != otherType.getArrayDimensions()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the type is a subtype of the super type without boxing or unboxing conversions.
     *
     * @param type the type
     * @param superType the super type
     * @return true if it is
     */
    private static boolean isSubtype(ClassElement type, ClassElement superType) {
        if (type.getArrayDimensions() != superType.getArrayDimensions()) {
            // arrays are only assignable to arrays of the same dimension here, which matches isAssignable
            return false;
        }
        if (type.isPrimitive() || superType.isPrimitive()) {
            return type.isPrimitive() && superType.isPrimitive() && isPrimitiveSubtype(type.getName(), superType.getName());
        }
        return type.isAssignable(superType);
    }

    /**
     * Whether a primitive type is a subtype of another primitive type, which includes widening primitive conversions (JLS 4.10.1).
     *
     * @param type the primitive type name
     * @param superType the primitive super type name
     * @return true if it is
     */
    private static boolean isPrimitiveSubtype(String type, String superType) {
        if (type.equals(superType)) {
            return true;
        }
        return switch (type) {
            case "byte" -> isPrimitiveSubtype("short", superType);
            case "short", "char" -> isPrimitiveSubtype("int", superType);
            case "int" -> isPrimitiveSubtype("long", superType);
            case "long" -> isPrimitiveSubtype("float", superType);
            case "float" -> "double".equals(superType);
            default -> false;
        };
    }

    private int calculateVarargsIndex() {
        return CollectionUtils.last(parameterTypes) == null ? -1 : parameterTypes.size() - 1;
    }

    @Override
    public String toString() {
        return methodElement.getDescription(false);
    }
}
