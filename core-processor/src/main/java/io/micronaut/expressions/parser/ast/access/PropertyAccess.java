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
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.PropertyElementQuery;
import org.objectweb.asm.Type;

import java.util.Collections;
import java.util.List;

import static io.micronaut.expressions.parser.ast.util.EvaluatedExpressionCompilationUtils.getRequiredClassElement;
import static java.util.Collections.emptyList;
import static java.util.function.Predicate.not;

/**
 * Expression AST node used for accessing object property.
 * Property access is under the hood an invocation of object getter method
 * of respective property.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class PropertyAccess extends ElementMethodCall {
    public PropertyAccess(ExpressionNode callee, String name, boolean nullSafe) {
        super(callee, name, emptyList(), nullSafe);
    }

    @Override
    protected CandidateMethod resolveUsedMethod(ExpressionVisitorContext ctx) {
        Type calleeType = callee.resolveType(ctx);
        ClassElement classElement = getRequiredClassElement(calleeType, ctx.visitorContext());

        List<PropertyElement> propertyElements =
            classElement.getBeanProperties(
                PropertyElementQuery.of(classElement.getAnnotationMetadata())
                    .includes(Collections.singleton(name))).stream()
                .filter(not(PropertyElement::isExcluded))
                .toList();

        if (propertyElements.size() == 0) {
            throw new ExpressionCompilationException(
                "Can not find property with name [" + name + "] in class " + calleeType);
        } else if (propertyElements.size() > 1) {
            throw new ExpressionCompilationException(
                "Ambiguous property access. Found " + propertyElements.size() +
                    " matching properties with name [" + name + "] in class " + calleeType);
        }

        PropertyElement property = propertyElements.iterator().next();
        MethodElement methodElement =
            property.getReadMethod()
                .orElseThrow(() -> new ExpressionCompilationException(
                    "Can not resolve access method for property [" + name + "] in class " + calleeType));

        return new CandidateMethod(methodElement);
    }
}
