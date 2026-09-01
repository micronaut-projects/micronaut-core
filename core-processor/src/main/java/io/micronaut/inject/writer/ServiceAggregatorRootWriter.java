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
import io.micronaut.core.io.service.ServiceAggregatorRoot;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.inject.visitor.VisitorContext;
import org.jspecify.annotations.Nullable;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;

import javax.lang.model.element.Modifier;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the {@link ServiceAggregatorRoot} for an application: the one class that constructs
 * every module {@link ServiceAggregator} on the classpath, so the runtime resolves exactly one class
 * by name instead of one per module.
 *
 * <p>This has to be generated where the whole classpath is known. An annotation processor cannot
 * enumerate the resources of its own compile classpath — {@code Filer#getResource} returns the first
 * match, never all of them — so the root belongs to the build plugin, which does know the classpath
 * and can call this writer directly with the module aggregator class names it found.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Internal
public final class ServiceAggregatorRootWriter implements ClassOutputWriter {

    /**
     * Suffix of the generated root class.
     */
    public static final String CLASS_SUFFIX = "$ServiceAggregatorRoot";

    private static final ClassTypeDef AGGREGATOR_TYPE = ClassTypeDef.of(ServiceAggregator.class);
    private static final TypeDef LIST_OF_AGGREGATORS = TypeDef.parameterized(List.class, AGGREGATOR_TYPE);
    private static final Method ADD_METHOD = ReflectionUtils.getRequiredMethod(List.class, "add", Object.class);

    private final String rootClassName;
    private final List<String> aggregatorClassNames;
    @Nullable
    private final VisitorContext visitorContext;

    /**
     * @param rootClassName        The fully qualified name of the class to generate
     * @param aggregatorClassNames The module aggregator class names on the application classpath
     * @param visitorContext       The visitor context, or {@code null} when generating outside a
     *                             compiler, as a build plugin does
     */
    public ServiceAggregatorRootWriter(String rootClassName,
                                       List<String> aggregatorClassNames,
                                       @Nullable VisitorContext visitorContext) {
        this.rootClassName = rootClassName;
        this.aggregatorClassNames = aggregatorClassNames;
        this.visitorContext = visitorContext;
    }

    @Override
    public void accept(ClassWriterOutputVisitor classWriterOutputVisitor) throws java.io.IOException {
        try (java.io.OutputStream outputStream = classWriterOutputVisitor.visitClass(rootClassName)) {
            outputStream.write(generateClassBytes());
        }
    }

    /**
     * @return The bytecode of the generated root
     */
    public byte[] generateClassBytes() {
        ClassDef.ClassDefBuilder builder = ClassDef.builder(rootClassName).synthetic()
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(ClassTypeDef.of(ServiceAggregatorRoot.class))
            .addAnnotation(AnnotationDef.builder(Generated.class).addMember("service", ServiceAggregatorRoot.SERVICE_NAME).build())
            .addMethod(MethodDef.constructor().addModifiers(Modifier.PUBLIC)
                .build((aThis, params) -> aThis.superRef().invokeConstructor()));

        builder.addMethod(MethodDef.builder("getAggregators").addModifiers(Modifier.PUBLIC).overrides()
            .returns(LIST_OF_AGGREGATORS)
            .build((aThis, params) -> ClassTypeDef.of(ArrayList.class)
                .instantiate(ExpressionDef.constant(aggregatorClassNames.size()))
                .newLocal("aggregators", aggregators -> {
                    List<StatementDef> statements = new ArrayList<>(aggregatorClassNames.size() + 1);
                    for (String aggregatorClassName : aggregatorClassNames) {
                        // each construction is guarded on its own: a module whose optional
                        // dependencies are absent should drop out rather than take the root with it
                        statements.add(
                            StatementDef.doTry(
                                aggregators.invoke(ADD_METHOD, ClassTypeDef.of(aggregatorClassName).instantiate())
                            ).doCatch(Throwable.class, ignored -> StatementDef.multi())
                        );
                    }
                    statements.add(aggregators.returning());
                    return StatementDef.multi(statements);
                })));

        return ByteCodeWriterUtils.writeByteCode(builder.build(), visitorContext);
    }

    /**
     * @return The content of the {@code META-INF/services} file advertising the generated root
     */
    public String serviceEntry() {
        return rootClassName;
    }

    /**
     * @return The resource path of the {@code META-INF/services} file advertising the root
     */
    public static String serviceResourcePath() {
        return "META-INF/services/" + ServiceAggregatorRoot.SERVICE_NAME;
    }
}
