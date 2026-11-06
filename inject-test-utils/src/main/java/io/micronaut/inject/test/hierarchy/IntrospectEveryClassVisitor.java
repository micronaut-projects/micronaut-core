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
package io.micronaut.inject.test.hierarchy;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Marks every class of the package {@value #PACKAGE}, and every class whose name starts with {@value #PREFIX} for the
 * Python compiler, which chooses the package of its classes, {@code @Introspected(hierarchy = true)} ahead of the
 * introspection visitor, as the TCK harness of micronaut-validation did with every class it compiled, and writes the
 * source of {@code Later extends Seed<String>} when it visits {@code Seed}, so that a later round holds both the
 * classes written so far and a new class of the application.
 */
public class IntrospectEveryClassVisitor implements TypeElementVisitor<Object, Object> {

    /**
     * The package of the classes the visitor introspects, every other class is left alone.
     */
    public static final String PACKAGE = "introspectall";

    /**
     * The prefix of the simple names of the other classes the visitor introspects.
     */
    public static final String PREFIX = "IntrospectAll";

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!element.getPackageName().equals(PACKAGE) && !element.getSimpleName().startsWith(PREFIX)) {
            return;
        }
        element.annotate(Introspected.class, builder -> builder.member("hierarchy", true));
        if (element.getSimpleName().equals("Seed") && context.getClassElement(PACKAGE + ".Later").isEmpty()) {
            String source = switch (context.getLanguage()) {
                case JAVA -> "package " + PACKAGE + "; public class Later extends Seed<String> {}";
                case KOTLIN -> "package " + PACKAGE + "\n\nclass Later : Seed<String>()\n";
                case GROOVY -> "package " + PACKAGE + "\n\nclass Later extends Seed<String> {}\n";
                default -> null;
            };
            if (source != null) {
                context.visitGeneratedSourceFile(PACKAGE, "Later", element).ifPresent(generatedFile -> {
                    try {
                        generatedFile.write(writer -> writer.write(source));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
    }

    @Override
    public int getOrder() {
        return IntrospectedTypeElementVisitor.POSITION + 100;
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
