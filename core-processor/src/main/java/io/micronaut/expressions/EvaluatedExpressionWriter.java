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
package io.micronaut.expressions;

import io.micronaut.context.expressions.AbstractEvaluatedExpression;
import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.expressions.ExpressionEvaluationContext;
import io.micronaut.expressions.context.ExpressionWithContext;
import io.micronaut.expressions.parser.CompoundEvaluatedExpressionParser;
import io.micronaut.expressions.parser.ast.ExpressionNode;
import io.micronaut.expressions.parser.compilation.ExpressionCompilationContext;
import io.micronaut.expressions.parser.compilation.ExpressionVisitorContext;
import io.micronaut.expressions.parser.exception.ExpressionCompilationException;
import io.micronaut.expressions.parser.exception.ExpressionParsingException;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.processing.JavaModelUtils;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.ClassOutputWriter;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;

/**
 * Writer for compile-time expressions.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class EvaluatedExpressionWriter implements ClassOutputWriter, Opcodes {

    private static final String CONSTRUCTOR_NAME = "<init>";
    private static final Pattern ARRAY_PATTERN = Pattern.compile("(\\[])+$");

    private static final Type TYPE_GENERATED = Type.getType(Generated.class);

    private static final Method EVALUATED_EXPRESSIONS_CONSTRUCTOR =
        new Method(CONSTRUCTOR_NAME, getConstructorDescriptor(Object.class));

    private static final Type EVALUATED_EXPRESSION_TYPE =
        Type.getType(AbstractEvaluatedExpression.class);

    private static final Set<String> WRITTEN_CLASSES = new HashSet<>();

    private final ExpressionWithContext expressionMetadata;
    private final VisitorContext visitorContext;
    private final Element originatingElement;

    public EvaluatedExpressionWriter(ExpressionWithContext expressionMetadata,
                                     VisitorContext visitorContext,
                                     Element originatingElement) {
        this.visitorContext = visitorContext;
        this.expressionMetadata = expressionMetadata;
        this.originatingElement = originatingElement;
    }

    @Override
    public void accept(ClassWriterOutputVisitor outputVisitor) throws IOException {
        String expressionClassName = expressionMetadata.expressionClassName();
        if (WRITTEN_CLASSES.contains(expressionClassName)) {
            return;
        }
        try (OutputStream outputStream = outputVisitor.visitClass(expressionClassName, originatingElement)) {
            ClassWriter classWriter = generateClassBytes(expressionClassName);
            outputStream.write(classWriter.toByteArray());
            WRITTEN_CLASSES.add(expressionClassName);
        }
    }

    private ClassWriter generateClassBytes(String expressionClassName) {
        ClassWriter classWriter = new ClassWriter(COMPUTE_MAXS | COMPUTE_FRAMES);

        startPublicClass(
            classWriter,
            getInternalName(expressionClassName),
            EVALUATED_EXPRESSION_TYPE);

        GeneratorAdapter cv = startConstructor(classWriter, Object.class);
        cv.loadThis();
        cv.loadArg(0);

        cv.invokeConstructor(EVALUATED_EXPRESSION_TYPE, EVALUATED_EXPRESSIONS_CONSTRUCTOR);
        // RETURN
        cv.returnValue();
        // MAXSTACK = 2
        // MAXLOCALS = 1
        cv.visitMaxs(2, 1);

        GeneratorAdapter evaluateMethodVisitor = startProtectedMethod(classWriter, "doEvaluate",
            Object.class.getName(), ExpressionEvaluationContext.class.getName());

        ExpressionCompilationContext ctx = new ExpressionCompilationContext(
            new ExpressionVisitorContext(expressionMetadata.evaluationContext(), visitorContext),
            evaluateMethodVisitor);

        Object annotationValue = expressionMetadata.annotationValue();

        try {
            ExpressionNode ast = new CompoundEvaluatedExpressionParser(annotationValue).parse();
            ast.compile(ctx);
            pushBoxPrimitiveIfNecessary(ast.resolveType(ctx), evaluateMethodVisitor);
        } catch (ExpressionParsingException | ExpressionCompilationException ex) {
            failCompilation(ex, annotationValue);
        }

        evaluateMethodVisitor.visitMaxs(2, 3);
        evaluateMethodVisitor.returnValue();
        return classWriter;
    }

    private void failCompilation(Throwable ex, Object initialAnnotationValue) {
        String strRepresentation = null;

        if (initialAnnotationValue instanceof String str) {
            strRepresentation = str;
        } else if (initialAnnotationValue instanceof String[] strArray) {
            strRepresentation = Arrays.toString(strArray);
        }

        String message = null;
        if (ex instanceof ExpressionParsingException parsingException) {
            message = "Failed to parse evaluated expression [" + strRepresentation + "]. " +
                          "Cause: " + parsingException.getMessage();
        } else if (ex instanceof ExpressionCompilationException compilationException) {
            message = "Failed to compile evaluated expression [" + strRepresentation + "]. " +
                          "Cause: " + compilationException.getMessage();
        }

        visitorContext.fail(message, originatingElement);
    }

    private static void pushBoxPrimitiveIfNecessary(Type fieldType, GeneratorAdapter injectMethodVisitor) {
        if (JavaModelUtils.isPrimitive(fieldType)) {
            injectMethodVisitor.valueOf(fieldType);
        }
    }

    private void startPublicClass(ClassVisitor classWriter, String className, Type superType) {
        classWriter.visit(V17, ACC_PUBLIC | ACC_SYNTHETIC, className, null, superType.getInternalName(), null);
        classWriter.visitAnnotation(TYPE_GENERATED.getDescriptor(), false);
    }

    private GeneratorAdapter startConstructor(ClassVisitor classWriter, Class<?>... argumentTypes) {
        String descriptor = getConstructorDescriptor(argumentTypes);
        return new GeneratorAdapter(classWriter.visitMethod(ACC_PUBLIC, CONSTRUCTOR_NAME, descriptor, null, null), ACC_PUBLIC, CONSTRUCTOR_NAME, descriptor);
    }

    private GeneratorAdapter startProtectedMethod(ClassWriter writer, String methodName, String returnType, String... argumentTypes) {
        return new GeneratorAdapter(writer.visitMethod(
            ACC_PROTECTED,
            methodName,
            getMethodDescriptor(returnType, argumentTypes),
            null,
            null
        ), ACC_PROTECTED,
            methodName,
            getMethodDescriptor(returnType, argumentTypes));
    }

    private static String getTypeDescriptor(Class<?> type) {
        return Type.getDescriptor(type);
    }

    private static String getTypeDescriptor(String className, String... genericTypes) {
        if (JavaModelUtils.NAME_TO_TYPE_MAP.containsKey(className)) {
            return JavaModelUtils.NAME_TO_TYPE_MAP.get(className);
        } else {
            String internalName = getInternalName(className);
            StringBuilder start = new StringBuilder(40);
            Matcher matcher = ARRAY_PATTERN.matcher(className);
            if (matcher.find()) {
                int dimensions = matcher.group(0).length() / 2;
                for (int i = 0; i < dimensions; i++) {
                    start.append('[');
                }
            }
            start.append('L').append(internalName);
            if (genericTypes != null && genericTypes.length > 0) {
                start.append('<');
                for (String genericType : genericTypes) {
                    start.append(getTypeDescriptor(genericType));
                }
                start.append('>');
            }
            return start.append(';').toString();
        }
    }

    private static String getMethodDescriptor(String returnType, String... argumentTypes) {
        StringBuilder builder = new StringBuilder();
        builder.append('(');

        for (String argumentType : argumentTypes) {
            builder.append(getTypeDescriptor(argumentType));
        }

        builder.append(')');

        builder.append(getTypeDescriptor(returnType));
        return builder.toString();
    }

    private static String getConstructorDescriptor(Class<?>... argumentTypes) {
        StringBuilder builder = new StringBuilder();
        builder.append('(');

        for (Class<?> argumentType : argumentTypes) {
            builder.append(getTypeDescriptor(argumentType));
        }

        return builder.append(")V").toString();
    }

    private static String getInternalName(String className) {
        String newClassName = className.replace('.', '/');
        Matcher matcher = ARRAY_PATTERN.matcher(newClassName);
        if (matcher.find()) {
            newClassName = matcher.replaceFirst("");
        }
        return newClassName;
    }

}
