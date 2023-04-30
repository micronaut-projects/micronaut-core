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
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.ast.collection.OneDimensionalArray;
import io.micronaut.expressions.parser.ast.util.TypeDescriptors;
import io.micronaut.expressions.parser.ast.types.TypeIdentifier;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.isPrimitive;
import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.getRequiredClassElement;
import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.pushBoxPrimitiveIfNecessary;
import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.pushUnboxPrimitiveIfNecessary;
import static io.micronaut.inject.processing.JavaModelUtils.getTypeReference;

/**
 * Abstract expression AST node for method calls.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public abstract sealed class AbstractMethodCall extends ExpressionNode permits ContextMethodCall,
                                                                               ElementMethodCall {
    protected final String name;
    protected final List<ExpressionNode> arguments;

    protected CandidateMethod usedMethod;

    public AbstractMethodCall(String name,
                              List<ExpressionNode> arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    @Override
    protected Type doResolveType(ExpressionVisitorContext ctx) {
        if (usedMethod == null) {
            usedMethod = resolveUsedMethod(ctx);
        }
        return usedMethod.getReturnType();
    }

    @Override
    protected ClassElement doResolveClassElement(ExpressionVisitorContext ctx) {
        doResolveType(ctx);
        return usedMethod.getMethodElement().getGenericReturnType();
    }

    /**
     * Resolves single {@link CandidateMethod} used by this AST node.
     *
     * @param ctx Expression compilation context
     * @return AST node candidate method
     * @throws io.micronaut.expressions.parser.exception.ExpressionCompilationException if no
     * candidate method can be found or if there is more than one candidate method.
     */
    @NonNull
    protected abstract CandidateMethod resolveUsedMethod(ExpressionVisitorContext ctx);

    /**
     * Builds candidate method for method element.
     *
     * @param ctx           expression compilation context
     * @param methodElement method element
     * @param argumentTypes types of arguments used for method invocation in expression
     *
     * @return candidate method
     */
    protected CandidateMethod toCandidateMethod(ExpressionVisitorContext ctx,
                                                MethodElement methodElement,
                                                List<Type> argumentTypes) {
        VisitorContext visitorContext = ctx.visitorContext();

        List<ClassElement> arguments =
            argumentTypes.stream()
                .map(type -> getRequiredClassElement(type, visitorContext))
                .toList();

        return new CandidateMethod(methodElement, arguments);
    }

    /**
     * This method wraps original method arguments into
     * array for methods using varargs.
     *
     * @return list of arguments, including varargs arguments wrapped in array
     */
    protected List<ExpressionNode> prepareVarargsArguments() {
        List<ExpressionNode> arguments = new ArrayList<>();
        int varargsIndex = usedMethod.getVarargsIndex();

        List<ExpressionNode> nodesWrappedInArray = new ArrayList<>();
        for (int i = 0; i < this.arguments.size(); i++) {
            ExpressionNode argument = this.arguments.get(i);
            if (varargsIndex > i) {
                arguments.add(argument);
            } else {
                nodesWrappedInArray.add(argument);
            }
        }

        ClassElement lastParameter = this.usedMethod.getLastParameter();

        OneDimensionalArray varargsArray =
            new OneDimensionalArray(
                new TypeIdentifier(lastParameter.getCanonicalName()),
                nodesWrappedInArray);

        arguments.add(varargsArray);
        return arguments;
    }

    /**
     * Resolve types of method invocation arguments.
     *
     * @param ctx expression evaluation context
     *
     * @return types of method arguments
     */
    protected List<Type> resolveArgumentTypes(ExpressionVisitorContext ctx) {
        return arguments.stream()
                   .map(argument -> argument instanceof TypeIdentifier
                       ? TypeDescriptors.CLASS
                       : argument.resolveType(ctx))
                   .toList();
    }

    /**
     * Compiles method arguments.
     *
     * @param ctx expression evaluation context
     */
    protected void compileArguments(ExpressionVisitorContext ctx) {
        List<ExpressionNode> arguments = this.arguments;
        if (usedMethod.isVarArgs()) {
            arguments = prepareVarargsArguments();
        }

        for (int i = 0; i < arguments.size(); i++) {
            compileArgument(ctx, i, arguments.get(i));
        }
    }

    /**
     * Compiles given method argument.
     *
     * @param ctx           expression evaluation context
     * @param argumentIndex argument index
     * @param argument      compiled argument
     */
    private void compileArgument(ExpressionVisitorContext ctx,
                                 int argumentIndex,
                                 ExpressionNode argument) {
        GeneratorAdapter mv = ctx.methodVisitor();
        if (usedMethod.getParameters().size() > argumentIndex) {
            Type parameterType = getTypeReference(usedMethod.getParameters().get(argumentIndex));
            Type argumentType = argument.resolveType(ctx);

            argument.compile(ctx);
            if (isPrimitive(parameterType)) {
                pushUnboxPrimitiveIfNecessary(argumentType, mv);
            } else {
                pushBoxPrimitiveIfNecessary(argumentType, mv);
            }
        }
    }

    /**
     * Prepares arguments string for logging purposes.
     *
     * @param ctx expression compilation context
     *
     * @return arguments string
     */
    protected String stringifyArguments(ExpressionVisitorContext ctx) {
        return arguments.stream()
                   .map(argument -> argument.resolveType(ctx))
                   .map(Type::getClassName)
                   .collect(Collectors.joining(", ", "(", ")"));
    }
}
