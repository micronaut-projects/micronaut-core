/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.web.router.processor;

import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;
import io.micronaut.web.router.spi.ControllerRoute;
import io.micronaut.web.router.spi.RouteCandidateSink;
import io.micronaut.web.router.spi.RoutePlan;
import io.micronaut.web.router.spi.RoutePlanSupport;
import io.micronaut.web.router.spi.RouteSlot;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the class of a {@link CompiledRoutePlan} with Sourcegen: the slot descriptors, one
 * static method per slot, and the parser, one static method per node of the tree of the lowered
 * segments of the compiled slots. At a node the parser reads the next segment of the path, tries
 * each literal child and each variable child with its rule, and reports the slots of the node
 * when the path ends there; every matching slot is reported, the router selects among them.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class RoutePlanWriter {

    private static final ClassTypeDef SLOT_TYPE = ClassTypeDef.of(RouteSlot.class);
    private static final ClassTypeDef CONTROLLER_TYPE = ClassTypeDef.of(ControllerRoute.class);
    private static final ClassTypeDef TEMPLATE_TYPE = ClassTypeDef.of(RouteTemplate.class);
    private static final ClassTypeDef SINK_TYPE = ClassTypeDef.of(RouteCandidateSink.class);
    private static final ClassTypeDef SUPPORT_TYPE = ClassTypeDef.of(RoutePlanSupport.class);
    private static final TypeDef.Array SPANS_TYPE = TypeDef.Primitive.INT.array();
    private static final List<TypeDef> SLOT_CONSTRUCTOR = List.of(
        TypeDef.STRING, TypeDef.STRING, TEMPLATE_TYPE, TypeDef.STRING, TypeDef.STRING, TypeDef.Primitive.INT,
        TypeDef.Primitive.INT, TypeDef.Primitive.INT, TypeDef.STRING.array(), TypeDef.Primitive.BOOLEAN, TypeDef.STRING, CONTROLLER_TYPE
    );
    private static final List<TypeDef> CONTROLLER_CONSTRUCTOR = List.of(
        TypeDef.STRING, TypeDef.STRING, TypeDef.STRING.array(), TypeDef.Primitive.BOOLEAN, TypeDef.STRING.array(),
        TypeDef.STRING.array(), TypeDef.Primitive.BOOLEAN, TypeDef.Primitive.INT
    );
    private static final List<TypeDef> NODE_PARAMETERS = List.of(TypeDef.STRING, TypeDef.Primitive.INT, SPANS_TYPE, SINK_TYPE);
    private static final List<TypeDef> RULE_PARAMETERS = List.of(TypeDef.STRING, TypeDef.Primitive.INT, TypeDef.Primitive.INT);

    private RoutePlanWriter() {
    }

    /**
     * @param plan The plan
     * @return The class of the plan
     */
    static ClassDef write(CompiledRoutePlan plan) {
        ClassTypeDef thisType = ClassTypeDef.of(plan.className());
        ClassDef.ClassDefBuilder builder = ClassDef.builder(plan.className())
            .synthetic()
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(AnnotationDef.builder(Generated.class).addMember("service", RoutePlan.class.getName()).build())
            .addSuperinterface(ClassTypeDef.of(RoutePlan.class));

        builder.addMethod(constant("id", TypeDef.STRING, ExpressionDef.constant(plan.id())));
        builder.addMethod(constant("abiVersion", TypeDef.Primitive.INT, ExpressionDef.constant(RoutePlan.ABI_VERSION)));
        builder.addMethod(constant("inputProfile", TypeDef.STRING, ExpressionDef.constant(RoutePlan.INPUT_PROFILE)));
        builder.addMethod(constant("selectionPolicy", TypeDef.STRING, ExpressionDef.constant(RoutePlan.SELECTION_POLICY)));
        builder.addMethod(constant("fingerprint", TypeDef.STRING, ExpressionDef.constant(plan.fingerprint())));
        builder.addMethod(constant("owners", TypeDef.STRING.array(), strings(plan.owners().toArray(String[]::new))));
        builder.addMethod(constant("commonPrefix", TypeDef.STRING, ExpressionDef.constant(plan.commonPrefix())));
        builder.addMethod(constant("maxCaptures", TypeDef.Primitive.INT, ExpressionDef.constant(plan.maxCaptures())));

        // a method per slot keeps every method far below the bytecode size limit
        List<ExpressionDef> slotCalls = new ArrayList<>(plan.slots().size());
        for (int i = 0; i < plan.slots().size(); i++) {
            RouteSlot slot = plan.slots().get(i);
            MethodDef slotMethod = MethodDef.builder("$slot" + i)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(SLOT_TYPE)
                .build((aThis, parameters) -> slot(slot).returning());
            builder.addMethod(slotMethod);
            slotCalls.add(thisType.invokeStatic(slotMethod.getName(), SLOT_TYPE));
        }
        builder.addMethod(MethodDef.builder("slots")
            .addModifiers(Modifier.PUBLIC)
            .returns(SLOT_TYPE.array())
            .build((aThis, parameters) -> SLOT_TYPE.array().instantiate(slotCalls).returning()));

        Node root = tree(plan);
        List<Node> nodes = new ArrayList<>();
        number(root, nodes);
        builder.addMethod(MethodDef.builder("match")
            .addModifiers(Modifier.PUBLIC)
            .addParameter("path", TypeDef.STRING)
            .addParameter("sink", SINK_TYPE)
            .build((aThis, parameters) -> {
                VariableDef path = parameters.get(0);
                VariableDef sink = parameters.get(1);
                return SPANS_TYPE.instantiate(2 * plan.maxCaptures()).newLocal("spans", spans -> {
                    StatementDef atRoot = candidates(root, sink, path, spans);
                    StatementDef below = root.hasChildren()
                        ? invokeNode(thisType, root, path, ExpressionDef.constant(0), spans, sink)
                        : StatementDef.multi();
                    return SUPPORT_TYPE.invokeStatic("isRoot", TypeDef.Primitive.BOOLEAN, path).isTrue().doIfElse(atRoot, below);
                });
            }));
        for (Node node : nodes) {
            if (node == root && !root.hasChildren()) {
                continue;
            }
            builder.addMethod(nodeMethod(thisType, node, node == root));
        }
        return builder.build();
    }

    private static MethodDef constant(String name, TypeDef type, ExpressionDef value) {
        return MethodDef.builder(name)
            .addModifiers(Modifier.PUBLIC)
            .returns(type)
            .build((aThis, parameters) -> value.returning());
    }

    private static ExpressionDef slot(RouteSlot slot) {
        ControllerRoute controller = slot.controller();
        return SLOT_TYPE.instantiate(
            SLOT_CONSTRUCTOR,
            ExpressionDef.constant(slot.key()),
            ExpressionDef.constant(slot.httpMethodName()),
            TEMPLATE_TYPE.instantiate(List.of(TypeDef.STRING, TypeDef.STRING),
                ExpressionDef.constant(slot.template().engineId()), ExpressionDef.constant(slot.template().expression())),
            ExpressionDef.constant(slot.engineVersion()),
            ExpressionDef.constant(slot.requiredPrefix()),
            ExpressionDef.constant(slot.rawLength()),
            ExpressionDef.constant(slot.pathVariableCount()),
            ExpressionDef.constant(slot.patternVariableCount()),
            strings(slot.captures()),
            ExpressionDef.constant(slot.compiled()),
            slot.fallbackReason() == null ? ExpressionDef.nullValue() : ExpressionDef.constant(slot.fallbackReason()),
            controller == null ? ExpressionDef.nullValue() : CONTROLLER_TYPE.instantiate(
                CONTROLLER_CONSTRUCTOR,
                ExpressionDef.constant(controller.ownerType()),
                ExpressionDef.constant(controller.methodName()),
                strings(controller.argumentTypes()),
                ExpressionDef.constant(controller.declaringTypeTarget()),
                strings(controller.consumes()),
                strings(controller.produces()),
                ExpressionDef.constant(controller.implicitHead()),
                ExpressionDef.constant(controller.port())
            )
        );
    }

    private static ExpressionDef strings(String @Nullable [] values) {
        if (values == null) {
            return ExpressionDef.nullValue();
        }
        return TypeDef.STRING.array().instantiate(Arrays.stream(values).map(value -> (ExpressionDef) ExpressionDef.constant(value)).toList());
    }

    /**
     * The method of a node: {@code nX(String path, int slash, int[] spans, RouteCandidateSink sink)},
     * called with the index of the slash that starts the next segment, or the length of the path
     * at its end.
     */
    private static MethodDef nodeMethod(ClassTypeDef thisType, Node node, boolean root) {
        return MethodDef.builder(nodeName(node))
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .addParameter("path", TypeDef.STRING)
            .addParameter("slash", TypeDef.Primitive.INT)
            .addParameter("spans", SPANS_TYPE)
            .addParameter("sink", SINK_TYPE)
            .build((aThis, parameters) -> {
                VariableDef path = parameters.get(0);
                VariableDef slash = parameters.get(1);
                VariableDef spans = parameters.get(2);
                VariableDef sink = parameters.get(3);
                // the root path is answered by match(), before the parser reads a segment
                StatementDef atEnd = root ? StatementDef.multi() : candidates(node, sink, path, spans);
                if (!node.hasChildren()) {
                    return slash.compare(ExpressionDef.ComparisonOperation.OpType.EQUAL_TO, path.invoke("length", TypeDef.Primitive.INT))
                        .doIf(atEnd);
                }
                StatementDef segment = SUPPORT_TYPE.invokeStatic("segmentEnd", TypeDef.Primitive.INT, path, slash).newLocal("end", end -> {
                    List<StatementDef> children = new ArrayList<>();
                    for (Map.Entry<String, Node> literal : node.literals.entrySet()) {
                        children.add(SUPPORT_TYPE.invokeStatic("literal", TypeDef.Primitive.BOOLEAN, path, slash, end, ExpressionDef.constant(literal.getKey()))
                            .isTrue()
                            .doIf(invokeNode(thisType, literal.getValue(), path, end, spans, sink)));
                    }
                    for (Map.Entry<LoweredTemplate.VariableRule, Node> variable : node.variables.entrySet()) {
                        LoweredTemplate.VariableRule rule = variable.getKey();
                        children.add(ClassTypeDef.of(rule.ownerType())
                            .invokeStatic(rule.methodName(), RULE_PARAMETERS, TypeDef.Primitive.BOOLEAN, List.of(path, slash, end))
                            .isTrue()
                            .doIf(StatementDef.multi(
                                SUPPORT_TYPE.invokeStatic("capture", TypeDef.VOID, spans, ExpressionDef.constant(node.depth), slash, end),
                                invokeNode(thisType, variable.getValue(), path, end, spans, sink)
                            )));
                    }
                    return end.compare(ExpressionDef.ComparisonOperation.OpType.GREATER_THAN_OR_EQUAL, ExpressionDef.constant(0))
                        .doIf(StatementDef.multi(children));
                });
                return slash.compare(ExpressionDef.ComparisonOperation.OpType.EQUAL_TO, path.invoke("length", TypeDef.Primitive.INT))
                    .doIfElse(atEnd, segment);
            });
    }

    private static StatementDef invokeNode(ClassTypeDef thisType, Node node, ExpressionDef path, ExpressionDef slash, ExpressionDef spans, ExpressionDef sink) {
        return thisType.invokeStatic(nodeName(node), NODE_PARAMETERS, TypeDef.VOID, List.of(path, slash, spans, sink));
    }

    private static StatementDef candidates(Node node, ExpressionDef sink, ExpressionDef path, ExpressionDef spans) {
        List<StatementDef> statements = new ArrayList<>(node.slots.size());
        for (int slot : node.slots) {
            statements.add(sink.invoke("candidate", TypeDef.VOID, ExpressionDef.constant(slot), path, spans));
        }
        return StatementDef.multi(statements);
    }

    private static String nodeName(Node node) {
        return "$node" + node.id;
    }

    private static Node tree(CompiledRoutePlan plan) {
        Node root = new Node(0);
        for (int i = 0; i < plan.slots().size(); i++) {
            if (!plan.slots().get(i).compiled()) {
                continue;
            }
            Node node = root;
            for (LoweredTemplate.Segment segment : plan.lowered().get(i).segments()) {
                LoweredTemplate.VariableRule rule = segment.rule();
                if (rule != null) {
                    int depth = node.depth + 1;
                    node = node.variables.computeIfAbsent(rule, r -> new Node(depth));
                } else {
                    int depth = node.depth;
                    node = node.literals.computeIfAbsent(String.valueOf(segment.literal()), l -> new Node(depth));
                }
            }
            node.slots.add(i);
        }
        return root;
    }

    private static void number(Node node, List<Node> nodes) {
        node.id = nodes.size();
        nodes.add(node);
        node.literals.values().forEach(child -> number(child, nodes));
        node.variables.values().forEach(child -> number(child, nodes));
    }

    /**
     * A node of the tree of lowered segments.
     */
    private static final class Node {
        final Map<String, Node> literals = new LinkedHashMap<>();
        final Map<LoweredTemplate.VariableRule, Node> variables = new LinkedHashMap<>();
        final List<Integer> slots = new ArrayList<>(1);
        /**
         * The number of variables captured before the node.
         */
        final int depth;
        int id;

        Node(int depth) {
            this.depth = depth;
        }

        boolean hasChildren() {
            return !literals.isEmpty() || !variables.isEmpty();
        }
    }
}
