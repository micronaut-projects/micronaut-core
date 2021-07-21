/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.beans.visitor;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.AbstractBeanMethod;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.naming.Named;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.AnnotationMetadataWriter;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.beans.AbstractExecutableBeanMethod;
import io.micronaut.inject.processing.JavaModelUtils;
import io.micronaut.inject.writer.AbstractClassFileWriter;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * Writes {@link io.micronaut.core.beans.BeanMethod} instances for introspections.
 *
 * @author graemerocher
 * @since 2.3.0
 */
@Internal
final class BeanMethodWriter extends AbstractClassFileWriter implements Named {
    protected static final org.objectweb.asm.commons.Method METHOD_INVOKE_INTERNAL = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(AbstractBeanMethod.class, "invokeInternal", Object.class, Object[].class));
    private static final Type TYPE_BEAN_PROPERTY = Type.getType(AbstractExecutableBeanMethod.class);
    private final MethodElement methodElement;
    private final Type type;
    private final ClassWriter classWriter;
    private final BeanIntrospectionWriter introspectionWriter;
    private final HashMap<String, GeneratorAdapter> loadTypeMethods = new HashMap<>();
    private final Map<String, Integer> defaults = new HashMap<>();

    /**
     * Default constructor.
     * @param introspectionWriter The introspection writer
     * @param introspectionType The introspection type
     * @param index The index
     * @param methodElement The method element
     */
    @Internal
    BeanMethodWriter(
            @NonNull BeanIntrospectionWriter introspectionWriter,
            Type introspectionType,
            int index,
            MethodElement methodElement) {
        super(methodElement, methodElement.getDeclaringType());
        this.type = JavaModelUtils.getTypeReference(ClassElement.of(introspectionType.getClassName() + "$$exec" + index));
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        this.methodElement = methodElement;
        this.introspectionWriter = introspectionWriter;
    }

    /**
     * @return The type
     */
    public Type getType() {
        return type;
    }

    @NonNull
    @Override
    public String getName() {
        return type.getClassName();
    }

    @Override
    public void accept(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {
        try (OutputStream classOutput = classWriterOutputVisitor.visitClass(getName(), getOriginatingElements())) {
            startFinalClass(classWriter, type.getInternalName(), TYPE_BEAN_PROPERTY);
            writeConstructor();
            writeInvoke();
            finalizeClass(classOutput);
        }
    }

    private void writeInvoke() {
        GeneratorAdapter invokeMethod = startPublicMethod(classWriter, METHOD_INVOKE_INTERNAL);
        ClassElement returnType = methodElement.getReturnType();
        ParameterElement[] parameters = methodElement.getParameters();
        List<ParameterElement> argumentTypes = Arrays.asList(parameters);
        String methodDescriptor = getMethodDescriptor(returnType, argumentTypes);
        invokeMethod.loadArg(0);
        pushCastToType(invokeMethod, introspectionWriter.getBeanType());
        for (int i = 0; i < parameters.length; i++) {
            ParameterElement parameter = parameters[i];
            invokeMethod.loadArg(1);
            invokeMethod.push(i);
            invokeMethod.visitInsn(AALOAD);
            pushCastToType(invokeMethod, parameter.getType());
        }
        ClassElement declaringType = methodElement.getDeclaringType();
        invokeMethod.visitMethodInsn(
                declaringType.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                JavaModelUtils.getTypeReference(declaringType).getInternalName(),
                methodElement.getName(),
                methodDescriptor,
                declaringType.isInterface()
        );
        if (returnType.getName().equals("void")) {
            invokeMethod.visitInsn(ACONST_NULL);
        } else {
            pushBoxPrimitiveIfNecessary(returnType, invokeMethod);
        }
        invokeMethod.returnValue();
        invokeMethod.visitMaxs(1, 1);
        invokeMethod.visitEnd();
    }

    private void finalizeClass(OutputStream classOutput) throws IOException {
        for (GeneratorAdapter generator : loadTypeMethods.values()) {
            generator.visitMaxs(3, 1);
            generator.visitEnd();
        }
        classOutput.write(classWriter.toByteArray());
    }

    private void writeConstructor() {
        final GeneratorAdapter constructor = startConstructor(classWriter, BeanIntrospection.class);
        ClassElement genericReturnType = methodElement.getGenericReturnType();
        constructor.loadThis();
        // 1st argument: the introspection
        constructor.loadArg(0);
        // 2nd argument: the return type
        if (genericReturnType.isPrimitive() && !genericReturnType.isArray()) {
            String constantName = genericReturnType.getName().toUpperCase(Locale.ENGLISH);
            // refer to constant for primitives
            Type type = Type.getType(Argument.class);
            constructor.getStatic(type, constantName, type);
        } else {
            pushCreateArgument(
                    introspectionWriter.getBeanType().getClassName(),
                    type,
                    classWriter,
                    constructor,
                    "R",
                    genericReturnType,
                    genericReturnType.getAnnotationMetadata(),
                    genericReturnType.getTypeArguments(),
                    new HashMap<>(),
                    loadTypeMethods
            );
        }
        // 3rd argument: the method name
        constructor.push(methodElement.getName());

        // 4th argument: the annotation metadata
        AnnotationMetadata annotationMetadata = methodElement.getAnnotationMetadata();
        if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
            // only keep declared
            annotationMetadata = ((AnnotationMetadataHierarchy) annotationMetadata).getDeclaredMetadata();
        }

        if (annotationMetadata instanceof DefaultAnnotationMetadata) {
            final DefaultAnnotationMetadata defaultMetadata = (DefaultAnnotationMetadata) annotationMetadata;
            for (ParameterElement pe : methodElement.getParameters()) {
                DefaultAnnotationMetadata.contributeDefaults(defaultMetadata, pe.getAnnotationMetadata());
            }
            if (defaultMetadata.isEmpty()) {
                constructor.visitInsn(ACONST_NULL);
            } else {
                AnnotationMetadataWriter.instantiateNewMetadata(type, classWriter, constructor, defaultMetadata, defaults, loadTypeMethods);
            }
        } else {
            constructor.visitInsn(ACONST_NULL);
        }

        // 5th argument: the arguments
        pushBuildArgumentsForMethod(
                introspectionWriter.getBeanType().getClassName(),
                type,
                classWriter,
                constructor,
                Arrays.asList(methodElement.getParameters()),
                new HashMap<>(),
                loadTypeMethods
        );

        invokeConstructor(constructor, AbstractExecutableBeanMethod.class, BeanIntrospection.class, Argument.class, String.class, AnnotationMetadata.class, Argument[].class);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(20, 2);
        constructor.visitEnd();
    }
}
