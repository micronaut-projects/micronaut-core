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
package io.micronaut.inject.beans.visitor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.expressions.context.ExpressionContextReference;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.writer.AbstractClassFileWriter;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A class file writer that writes a {@link ExpressionContextReference}.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
final class ExpressionContextReferenceWriter extends AbstractClassFileWriter {
    private static final String EXPRESSION_CTX_SUFFIX = "$ExprCtxRef";

    private final String referenceName;
    private final Type targetClassType;
    private final ClassWriter referenceWriter;

    public ExpressionContextReferenceWriter(ClassElement classElement) {
        super(classElement);
        final String name = classElement.getName();
        this.referenceWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        this.referenceName = computeReferenceName(name);
        this.targetClassType = getTypeReferenceForName(name);
    }

    private String computeReferenceName(String className) {
        final String packageName = NameUtils.getPackageName(className);
        final String shortName = NameUtils.getSimpleName(className);
        return packageName + ".$" + shortName + EXPRESSION_CTX_SUFFIX;
    }

    @Override
    public void accept(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {
        Type superType = Type.getType(ExpressionContextReference.class);
        classWriterOutputVisitor.visitServiceDescriptor(
            ExpressionContextReference.class,
            referenceName,
            getOriginatingElement());

        String internalName = getTypeReferenceForName(referenceName).getInternalName();
        try (OutputStream referenceStream = classWriterOutputVisitor.visitClass(referenceName,
            getOriginatingElements())) {
            startService(referenceWriter, ExpressionContextReference.class, internalName,
                superType);
            ClassWriter classWriter = generateClassBytes(referenceWriter);
            referenceStream.write(classWriter.toByteArray());
        }
    }

    private ClassWriter generateClassBytes(ClassWriter classWriter) {
        GeneratorAdapter cv = startConstructor(classWriter);
        cv.loadThis();
        invokeConstructor(cv, ExpressionContextReference.class);
        cv.returnValue();
        cv.visitMaxs(2, 1);
        GeneratorAdapter getTypeMethod = startPublicMethodZeroArgs(classWriter, String.class,
            "getType");
        getTypeMethod.push(targetClassType.getClassName());
        getTypeMethod.returnValue();
        getTypeMethod.visitMaxs(2, 1);
        getTypeMethod.endMethod();
        return classWriter;
    }
}
