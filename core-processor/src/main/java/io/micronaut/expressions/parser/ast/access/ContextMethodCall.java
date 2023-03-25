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
import io.micronaut.expressions.context.ExpressionCompilationContext;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.inject.ast.ClassElement;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.List;

import static io.micronaut.expressions.parser.ast.util.TypeDescriptors.EVALUATION_CONTEXT_TYPE;
import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.getRequiredClassElement;
import static org.objectweb.asm.Opcodes.CHECKCAST;

/**
 * Expression node used for invocation of method from expression
 * evaluation context.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class ContextMethodCall extends AbstractMethodCall {

    private static final Method GET_BEAN_METHOD =
        new Method("getBean", Type.getType(Object.class),
            new Type[]{Type.getType(Class.class)});

    public ContextMethodCall(String name, List<ExpressionNode> arguments) {
        super(name, arguments);
    }

    @Override
    protected CandidateMethod resolveUsedMethod(ExpressionVisitorContext ctx) {
        List<Type> argumentTypes = resolveArgumentTypes(ctx);

        ExpressionCompilationContext evaluationContext = ctx.compilationContext();
        List<CandidateMethod> candidateMethods =
            evaluationContext.findMethods(name)
                .stream()
                .map(method -> toCandidateMethod(ctx, method, argumentTypes))
                .filter(method -> method.isMatching(ctx.visitorContext()))
                .toList();

        if (candidateMethods.isEmpty()) {
            throw new ExpressionCompilationException(
                "No method [ " + name + stringifyArguments(ctx) + " ] available in evaluation context");
        } else if (candidateMethods.size() > 1) {
            throw new ExpressionCompilationException(
                "Ambiguous expression evaluation context reference. Found " + candidateMethods.size() +
                    " matching methods: " + candidateMethods);
        }

        return candidateMethods.iterator().next();
    }

    @Override
    public void generateBytecode(ExpressionVisitorContext ctx) {
        GeneratorAdapter mv = ctx.methodVisitor();
        Type calleeType = usedMethod.getOwningType();

        ClassElement calleeClass = getRequiredClassElement(calleeType, ctx.visitorContext());

        pushGetBeanFromContext(mv, calleeType);
        compileArguments(ctx);
        if (calleeClass.isInterface()) {
            mv.invokeInterface(calleeType, usedMethod.toAsmMethod());
        } else {
            mv.invokeVirtual(calleeType, usedMethod.toAsmMethod());
        }
    }

    /**
     * Pushing method obtaining bean of provided type from beanContext.
     *
     * @param mv       methodVisitor
     * @param beanType required bean typ
     */
    private void pushGetBeanFromContext(GeneratorAdapter mv, Type beanType) {
        mv.loadArg(0);
        mv.push(beanType);

        // invoke getBean method
        mv.invokeInterface(EVALUATION_CONTEXT_TYPE, GET_BEAN_METHOD);

        // cast the return value to the correct type
        mv.visitTypeInsn(CHECKCAST, beanType.getInternalName());
    }
}
