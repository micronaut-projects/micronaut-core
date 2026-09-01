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
package io.micronaut.inject.writer;

import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.service.ServiceAggregator;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Generates the single {@link ServiceAggregator} implementation for a module, replacing the
 * per-implementation {@code META-INF/micronaut/<service>/<class>} marker files.
 *
 * <p>The generated class instantiates every service implementation directly, so at runtime there is
 * no class name lookup, no reflective constructor and no per-implementation resource to read. The
 * whole module costs one resource lookup and one class load.</p>
 *
 * <p>Two details of the generated shape matter for correctness:</p>
 * <ul>
 *   <li>The consumer is declared as {@code Consumer<Object>} rather than a consumer of the service
 *   type. A consumer of the service type would force the verifier to load every implementation to
 *   prove assignability, which would turn one implementation with a missing optional dependency
 *   into a {@code VerifyError} for the entire module. Passing {@link Object} keeps class resolution
 *   lazy, at the {@code new} instruction.</li>
 *   <li>Each instantiation sits in its own {@code try}/{@code catch (Throwable)} so that a missing
 *   optional dependency skips exactly one implementation, matching what the marker file scan does
 *   today. An untaken catch costs nothing at runtime, only an exception table entry.</li>
 * </ul>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Internal
public final class ServiceAggregatorWriter implements ClassOutputWriter {

    /**
     * Suffix of the generated aggregator class.
     */
    public static final String CLASS_SUFFIX = "$ServiceAggregator";

    /**
     * How many instantiations go into one chunk method.
     *
     * <p>A chunk is the unit the runtime forks onto the common pool, so this is a parallelism knob
     * rather than a size knob. Measured on a 400 bean module, collecting in one chunk costs about
     * 265ms against 155ms split into chunks, while anything from 32 to 128 per chunk lands within
     * noise of each other; below that the fork overhead starts to show. It is also comfortably
     * inside the 64KB method limit, since one entry is roughly 20 bytes of bytecode.</p>
     */
    private static final int ENTRIES_PER_CHUNK = 64;

    private static final ClassTypeDef CONSUMER_TYPE = ClassTypeDef.of(Consumer.class);
    private static final TypeDef.Primitive VOID = TypeDef.Primitive.VOID;
    private static final Method ACCEPT_METHOD = ReflectionUtils.getRequiredMethod(Consumer.class, "accept", Object.class);

    private final String aggregatorClassName;
    private final Map<String, List<String>> servicesByName;
    private final Element[] originatingElements;
    private final VisitorContext visitorContext;

    /**
     * @param aggregatorClassName The fully qualified name of the class to generate
     * @param servicesByName      The implementation class names keyed by service name
     * @param originatingElements The originating elements
     * @param visitorContext      The visitor context
     */
    public ServiceAggregatorWriter(String aggregatorClassName,
                                   Map<String, List<String>> servicesByName,
                                   Element[] originatingElements,
                                   VisitorContext visitorContext) {
        this.aggregatorClassName = aggregatorClassName;
        this.servicesByName = servicesByName;
        this.originatingElements = originatingElements;
        this.visitorContext = visitorContext;
    }

    @Override
    public void accept(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {
        try (OutputStream outputStream = classWriterOutputVisitor.visitClass(aggregatorClassName, originatingElements)) {
            outputStream.write(generateClassBytes());
        }
        // the aggregator itself is advertised through an ordinary META-INF/services file, which the
        // class loader can resolve without opening the jar as a file system
        classWriterOutputVisitor.visitServiceDescriptor(ServiceAggregator.SERVICE_NAME, aggregatorClassName);
    }

    private byte[] generateClassBytes() {
        ClassTypeDef selfType = ClassTypeDef.of(aggregatorClassName);

        FieldDef servicesField = FieldDef.builder("SERVICES", TypeDef.parameterized(Set.class, TypeDef.STRING))
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer(GenUtils.setOf(servicesByName.keySet().stream().sorted().map(n -> (ExpressionDef) ExpressionDef.constant(n)).toList()))
            .build();

        ClassDef.ClassDefBuilder builder = ClassDef.builder(aggregatorClassName).synthetic()
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(ClassTypeDef.of(ServiceAggregator.class))
            .addAnnotation(AnnotationDef.builder(Generated.class).addMember("service", ServiceAggregator.SERVICE_NAME).build())
            .addField(servicesField)
            .addMethod(MethodDef.constructor().addModifiers(Modifier.PUBLIC)
                .build((aThis, params) -> aThis.superRef().invokeConstructor()))
            .addMethod(MethodDef.builder("getServiceNames").addModifiers(Modifier.PUBLIC).overrides()
                .returns(TypeDef.parameterized(Set.class, TypeDef.STRING))
                .build((aThis, params) -> selfType.getStaticField(servicesField).returning()));

        // one method per chunk, dispatched by (service name, chunk index) so the runtime can fork
        // chunks onto the common pool the way the marker file scan forks per implementation
        Map<ExpressionDef.Constant, StatementDef> chunkCountCases = new LinkedHashMap<>();
        Map<ExpressionDef.Constant, StatementDef> collectCases = new LinkedHashMap<>();
        int methodIndex = 0;
        for (Map.Entry<String, List<String>> entry : servicesByName.entrySet()) {
            // sorted so the generated bytecode does not depend on the order javac visited types in
            List<String> implementations = entry.getValue().stream().distinct().sorted().toList();
            List<MethodDef> chunkMethods = new ArrayList<>();
            for (int from = 0; from < implementations.size(); from += ENTRIES_PER_CHUNK) {
                int to = Math.min(from + ENTRIES_PER_CHUNK, implementations.size());
                chunkMethods.add(chunkMethod("collect$" + methodIndex++, implementations.subList(from, to)));
            }
            chunkMethods.forEach(builder::addMethod);

            ExpressionDef.Constant serviceName = ExpressionDef.constant(entry.getKey());
            chunkCountCases.put(serviceName, ExpressionDef.constant(chunkMethods.size()).returning());

            Map<ExpressionDef.Constant, StatementDef> byChunk = new LinkedHashMap<>();
            for (int i = 0; i < chunkMethods.size(); i++) {
                byChunk.put(
                    ExpressionDef.constant(i),
                    selfType.invokeStatic(chunkMethods.get(i), new VariableDef.MethodParameter("consumer", CONSUMER_TYPE))
                );
            }
            collectCases.put(serviceName, new VariableDef.MethodParameter("chunk", TypeDef.Primitive.INT)
                .asStatementSwitch(VOID, byChunk, StatementDef.multi()));
        }

        builder.addMethod(MethodDef.builder("getChunkCount").addModifiers(Modifier.PUBLIC).overrides()
            .addParameter("serviceName", TypeDef.STRING)
            .returns(TypeDef.Primitive.INT)
            .build((aThis, params) -> params.get(0).asStatementSwitch(
                TypeDef.Primitive.INT, chunkCountCases, ExpressionDef.constant(0).returning())));

        builder.addMethod(MethodDef.builder("collect").addModifiers(Modifier.PUBLIC).overrides()
            .addParameter("serviceName", TypeDef.STRING)
            .addParameter("chunk", TypeDef.Primitive.INT)
            .addParameter("consumer", CONSUMER_TYPE)
            .returns(VOID)
            .build((aThis, params) -> params.get(0).asStatementSwitch(VOID, collectCases, StatementDef.multi())));

        return ByteCodeWriterUtils.writeByteCode(builder.build(), visitorContext);
    }

    private MethodDef chunkMethod(String name, List<String> implementations) {
        return MethodDef.builder(name)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .addParameter("consumer", CONSUMER_TYPE)
            .returns(VOID)
            .buildStatic(params -> {
                VariableDef consumer = params.get(0);
                List<StatementDef> statements = new ArrayList<>(implementations.size());
                for (String implementation : implementations) {
                    // invoked through Consumer#accept(Object) so the descriptor stays erased: a
                    // descriptor naming the implementation would both fail to resolve and force the
                    // verifier to load the class eagerly
                    statements.add(
                        StatementDef.doTry(
                            consumer.invoke(ACCEPT_METHOD, ClassTypeDef.of(implementation).instantiate())
                        ).doCatch(Throwable.class, ignored -> StatementDef.multi())
                    );
                }
                return StatementDef.multi(statements);
            });
    }

}
