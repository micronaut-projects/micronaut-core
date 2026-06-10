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
package io.micronaut.python.processing.util;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.JavaIdioms;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;
import io.micronaut.sourcegen.model.FieldDef;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.micronaut.sourcegen.model.ExpressionDef.MathBinaryOperation.OpType.ADDITION;
import static io.micronaut.sourcegen.model.ExpressionDef.MathBinaryOperation.OpType.MULTIPLICATION;

/**
 * Generates Object method implementations (toString, equals, hashCode)
 * for generated POJOs that mirror Python introspected beans.
 */
@Experimental
public final class ObjectHelper {

    private static final ExpressionDef HASH_MULTIPLIER = ExpressionDef.primitiveConstant(31);
    private static final ClassTypeDef ARRAYS = ClassTypeDef.of(Arrays.class);

    private ObjectHelper() {
    }

    /**
     * Adds toString, equals and hashCode methods to the given class builder.
     * Only readable (non write-only) properties are considered.
     *
     * @param classDefBuilder the builder of the generated class
     * @param selfType the type of the generated class
     * @param properties all bean properties of the source element
     * @param propertyFields map from property name to backing FieldDef in the generated class
     */
    public static void addObjectMethods(ClassDef.ClassDefBuilder classDefBuilder,
                                        ClassTypeDef selfType,
                                        List<PropertyElement> properties,
                                        Map<String, FieldDef> propertyFields) {
        List<PropertyElement> readableProps = properties.stream()
            .filter(p -> !p.isWriteOnly())
            .toList();
        List<PropertyElement> toStringProps = readableProps.stream()
            .filter(ObjectHelper::isToStringSafe)
            .toList();
        createToStringMethod(classDefBuilder, selfType, toStringProps, propertyFields);
        createEqualsMethod(classDefBuilder, selfType, readableProps, propertyFields);
        createHashCodeMethod(classDefBuilder, selfType, readableProps, propertyFields);
    }

    private static boolean isToStringSafe(PropertyElement property) {
        if (isSimpleToStringType(property.getType())) {
            return true;
        }
        if (property.getType().isAssignable(Collection.class)) {
            return property.getGenericType()
                .getFirstTypeArgument()
                .map(ObjectHelper::isSimpleToStringType)
                .orElse(false);
        }
        if (property.getType().isAssignable(Map.class)) {
            return false;
        }
        return false;
    }

    private static boolean isSimpleToStringType(ClassElement type) {
        if (type.isPrimitive() || type.isEnum()) {
            return true;
        }
        String typeName = type.getName();
        return typeName.startsWith("java.lang.") || typeName.startsWith("java.time.");
    }

    private static void createToStringMethod(ClassDef.ClassDefBuilder classDefBuilder,
                                             ClassTypeDef selfType,
                                             List<PropertyElement> properties,
                                             Map<String, FieldDef> propertyFields) {
        MethodDef method = MethodDef.builder("toString")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeDef.STRING)
            .build((self, parameterDef) -> {
                List<ExpressionDef> expressions = new ArrayList<>();
                expressions.add(ExpressionDef.constant(selfType.getSimpleName() + "["));
                for (int i = 0; i < properties.size(); i++) {
                    PropertyElement beanProperty = properties.get(i);
                    FieldDef field = propertyFields.get(beanProperty.getName());
                    if (field == null) {
                        continue;
                    }
                    ExpressionDef value = self.field(field);
                    expressions.add(ExpressionDef.constant(beanProperty.getName() + "="));
                    expressions.add(propertyToString(beanProperty, value));
                    expressions.add(ExpressionDef.constant((i == properties.size() - 1) ? "]" : ", "));
                }
                return JavaIdioms.concatStrings(expressions).returning();
            });
        classDefBuilder.addMethod(method);
    }

    private static void createEqualsMethod(ClassDef.ClassDefBuilder classDefBuilder,
                                           ClassTypeDef selfType,
                                           List<PropertyElement> properties,
                                           Map<String, FieldDef> propertyFields) {
        MethodDef method = MethodDef.builder("equals")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeDef.Primitive.BOOLEAN)
            .addParameter("o", TypeDef.OBJECT.makeNullable())
            .build((self, parameters) -> {
                VariableDef.MethodParameter o = parameters.get(0);
                return StatementDef.multi(
                    self.equalsReferentially(o).ifTrue(ExpressionDef.trueValue().returning()),
                    o.isNull().or(self.invokeGetClass().notEqualsReferentially(o.invokeGetClass()))
                        .doIf(ExpressionDef.falseValue().returning()),
                    o.cast(selfType).newLocal("other", other -> {
                        ExpressionDef.ConditionExpressionDef exp = null;
                        for (PropertyElement beanProperty : properties) {
                            FieldDef field = propertyFields.get(beanProperty.getName());
                            if (field == null) {
                                continue;
                            }
                            ExpressionDef left = self.field(field);
                            ExpressionDef right = other.field(field);

                            ExpressionDef.ConditionExpressionDef cmp;
                            if (beanProperty.getType().isArray()) {
                                boolean primitiveComponent = beanProperty.getType().fromArray().isPrimitive();
                                String methodName = primitiveComponent ? "equals" : "deepEquals";
                                cmp = ARRAYS.invokeStatic(methodName, TypeDef.Primitive.BOOLEAN, left, right).isTrue();
                            } else if (!beanProperty.isPrimitive()) {
                                // Use Objects.equals for null-safe object equality
                                cmp = ClassTypeDef.of(Objects.class)
                                    .invokeStatic("equals", TypeDef.Primitive.BOOLEAN, left, right)
                                    .isTrue();
                            } else {
                                cmp = left.equalsStructurally(right);
                            }

                            if (exp == null) {
                                exp = cmp;
                            } else {
                                exp = exp.and(cmp);
                            }
                        }
                        return Objects.requireNonNullElseGet(exp, ExpressionDef::trueValue).returning();
                    })
                );
            });
        classDefBuilder.addMethod(method);
    }

    private static void createHashCodeMethod(ClassDef.ClassDefBuilder classDefBuilder,
                                             ClassTypeDef selfType,
                                             List<PropertyElement> properties,
                                             Map<String, FieldDef> propertyFields) {
        Iterator<PropertyElement> it = properties.iterator();
        MethodDef method = MethodDef.builder("hashCode")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeDef.Primitive.INT)
            .build((self, params) -> {
                if (!it.hasNext()) {
                    return ExpressionDef.primitiveConstant(0).returning();
                }
                PropertyElement first = it.next();
                FieldDef firstField = propertyFields.get(first.getName());
                if (firstField == null) {
                    return ExpressionDef.primitiveConstant(0).returning();
                }
                ExpressionDef firstHash = propertyHash(first, self.field(firstField));
                return firstHash.newLocal("hashValue", hash -> {
                    List<StatementDef> stmts = new ArrayList<>();
                    while (it.hasNext()) {
                        PropertyElement p = it.next();
                        FieldDef f = propertyFields.get(p.getName());
                        if (f == null) {
                            continue;
                        }
                        ExpressionDef part = propertyHash(p, self.field(f));
                        ExpressionDef combined = hash.math(MULTIPLICATION, HASH_MULTIPLIER).math(ADDITION, part);
                        stmts.add(hash.assign(combined));
                    }
                    stmts.add(hash.returning());
                    return StatementDef.multi(stmts);
                });
            });
        classDefBuilder.addMethod(method);
    }

    private static ExpressionDef propertyToString(PropertyElement prop, ExpressionDef value) {
        if (prop.getType().isArray()) {
            boolean primitiveComponent = prop.getType().fromArray().isPrimitive();
            String method = primitiveComponent ? "toString" : "deepToString";
            return ARRAYS.invokeStatic(method, TypeDef.STRING, value);
        }
        return value;
    }

    private static ExpressionDef propertyHash(PropertyElement prop, ExpressionDef value) {
        if (prop.getType().isArray()) {
            boolean primitiveComponent = prop.getType().fromArray().isPrimitive();
            String method = primitiveComponent ? "hashCode" : "deepHashCode";
            return ARRAYS.invokeStatic(method, TypeDef.Primitive.INT, value);
        }
        return value.invokeHashCode();
    }
}
