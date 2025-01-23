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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.sourcegen.model.TypeDef;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
    @NonNull
    public TypeDef getReturnType() {
        return TypeDef.erasure(methodElement.getReturnType());
    }

    /**
     * @return Type of class that owns candidate method.
     */
    @NonNull
    public TypeDef getOwningType() {
        return TypeDef.erasure(methodElement.getOwningType());
    }

    /**
     * @return last parameter of candidate method.
     */
    @NonNull
    public ClassElement getLastParameter() {
        return CollectionUtils.last(parameterTypes);
    }

    /**
     * @return list of candidate method parameters.
     */
    @NonNull
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
                    ClassElement argumentType = argumentTypes.get(paramIndex);
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

    private int calculateVarargsIndex() {
        return CollectionUtils.last(parameterTypes) == null ? -1 : parameterTypes.size() - 1;
    }

    @Override
    public String toString() {
        return methodElement.getDescription(false);
    }
}
