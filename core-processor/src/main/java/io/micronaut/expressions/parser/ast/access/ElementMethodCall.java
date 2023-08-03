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
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.ast.types.TypeIdentifier;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.processing.JavaModelUtils;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.List;
import java.util.Optional;

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

    private static final Type TYPE_OPTIONAL = Type.getType(Optional.class);
    private static final Method METHOD_OR_ELSE = Method.getMethod(ReflectionUtils.getRequiredInternalMethod(Optional.class, "orElse", Object.class));
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

    /**
     * @return Is the method call null safe
     */
    protected boolean isNullSafe() {
        return nullSafe;
    }

    @Override
    protected void generateBytecode(ExpressionVisitorContext ctx) {
        GeneratorAdapter mv = ctx.methodVisitor();
        ClassElement calleeClass = callee.resolveClassElement(ctx);
        Method method = usedMethod.toAsmMethod();
        Type calleeType = JavaModelUtils.getTypeReference(calleeClass);

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
                if (calleeClass.isAssignable(Optional.class)) {
                    mv.checkCast(TYPE_OPTIONAL);
                    // safe navigate optional
                    mv.visitInsn(ACONST_NULL);
                    mv.invokeVirtual(TYPE_OPTIONAL, METHOD_OR_ELSE);
                    // recompute new return type
                    calleeClass = calleeClass.getFirstTypeArgument().orElse(ClassElement.of(Object.class));
                    calleeType = JavaModelUtils.getTypeReference(calleeClass);
                    mv.checkCast(calleeType);
                }
                // null safe operator is used, so we need to check the result is null
                mv.storeLocal(2, calleeType);
                mv.loadLocal(2, calleeType);
                Label proceed = new Label();
                mv.ifNonNull(proceed);
                mv.visitInsn(ACONST_NULL);
                mv.returnValue();
                mv.visitLabel(proceed);
                mv.loadLocal(2, calleeType);
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
        ClassElement classElement = callee.resolveClassElement(ctx);

        if (isNullSafe() && classElement.isAssignable(Optional.class)) {
            // safe navigate optional
            classElement = classElement.getFirstTypeArgument().orElse(classElement);
        }


        ElementQuery<MethodElement> methodQuery = buildMethodQuery();
        List<CandidateMethod> candidateMethods = classElement.getEnclosedElements(methodQuery).stream()
                .map(method -> toCandidateMethod(ctx, method, argumentTypes))
                .filter(method -> method.isMatching(ctx.visitorContext()))
                .toList();

        if (candidateMethods.isEmpty()) {
            throw new ExpressionCompilationException(
                "No method [ " + name + stringifyArguments(ctx) + " ] available in class " + classElement.getName());
        } else if (candidateMethods.size() > 1) {
            throw new ExpressionCompilationException(
                "Ambiguous method call. Found " + candidateMethods.size() +
                    " matching methods: " + candidateMethods + " in class " + classElement.getName());
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
