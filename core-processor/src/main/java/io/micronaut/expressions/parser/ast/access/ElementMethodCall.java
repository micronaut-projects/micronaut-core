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
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.ast.types.TypeIdentifier;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.List;

import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.getRequiredClassElement;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

/**
 * Expression AST node used for method invocation.
 * This node represents both object method invocation and static method
 * invocation
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public sealed class ElementMethodCall extends AbstractMethodCall permits PropertyAccess {

    protected final ExpressionNode callee;
    private final boolean nullSafe;

    public ElementMethodCall(ExpressionNode callee,
                             String name,
                             List<ExpressionNode> arguments,
                             boolean nullSafe) {
        super(name, arguments);
        this.callee = callee;
        this.nullSafe = nullSafe;
    }

    @Override
    protected void generateBytecode(ExpressionVisitorContext ctx) {
        GeneratorAdapter mv = ctx.methodVisitor();
        VisitorContext visitorContext = ctx.visitorContext();
        Type calleeType = callee.resolveType(ctx);
        Method method = usedMethod.toAsmMethod();

        ClassElement calleeClass = getRequiredClassElement(calleeType, visitorContext);

        if (callee instanceof TypeIdentifier) {
            compileArguments(ctx);
            if (calleeClass.isInterface()) {
                mv.visitMethodInsn(INVOKESTATIC, calleeType.getInternalName(), name,
                    usedMethod.getDescriptor(), true);
            } else {
                mv.invokeStatic(calleeType, method);
            }
        } else {
            callee.compile(ctx);
            if (nullSafe) {
                // null safe operator is used so we need to check the result is null
                Type returnType = method.getReturnType();
                mv.storeLocal(2, returnType);
                mv.loadLocal(2, returnType);
                Label proceed = new Label();
                mv.ifNonNull(proceed);
                mv.visitInsn(ACONST_NULL);
                mv.returnValue();
                mv.visitLabel(proceed);
                mv.loadLocal(2, returnType);
            }
            compileArguments(ctx);
            if (calleeClass.isInterface()) {
                mv.invokeInterface(calleeType, method);
            } else {
                mv.invokeVirtual(calleeType, method);
            }
        }
    }

    @Override
    protected CandidateMethod resolveUsedMethod(ExpressionVisitorContext ctx) {
        List<Type> argumentTypes = resolveArgumentTypes(ctx);
        Type calleeType = callee.resolveType(ctx);

        ElementQuery<MethodElement> methodQuery = buildMethodQuery();
        List<CandidateMethod> candidateMethods =
            ctx.visitorContext()
                .getClassElement(calleeType.getClassName())
                .stream()
                .flatMap(element -> element.getEnclosedElements(methodQuery).stream())
                .map(method -> toCandidateMethod(ctx, method, argumentTypes))
                .filter(method -> method.isMatching(ctx.visitorContext()))
                .toList();

        if (candidateMethods.isEmpty()) {
            throw new ExpressionCompilationException(
                "No method [ " + name + stringifyArguments(ctx) + " ] available in class " + calleeType);
        } else if (candidateMethods.size() > 1) {
            throw new ExpressionCompilationException(
                "Ambiguous method call. Found " + candidateMethods.size() +
                    " matching methods: " + candidateMethods + " in class " + calleeType);
        }

        return candidateMethods.iterator().next();
    }

    private ElementQuery<MethodElement> buildMethodQuery() {
        ElementQuery<MethodElement> query = ElementQuery.ALL_METHODS.onlyAccessible()
                                                .named(name);

        if (callee instanceof TypeIdentifier) {
            query = query.onlyStatic();
        }

        return query;
    }
}
