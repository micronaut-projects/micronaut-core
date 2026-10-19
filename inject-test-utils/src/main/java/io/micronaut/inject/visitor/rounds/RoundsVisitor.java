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
package io.micronaut.inject.visitor.rounds;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.GeneratedFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records the rounds of the classes of the package {@value #PACKAGE}, and registers a bean of {@link RegisteredRecord}
 * for each of them from {@link #finishRound(VisitorContext)}, named after the round, and one from
 * {@link #finish(VisitorContext)}, named {@code finish}. Visiting the class {@value #TRIGGER} generates the source of
 * the class {@value #GENERATED}, which the next round visits.
 */
public class RoundsVisitor implements TypeElementVisitor<Object, Object> {

    public static final String PACKAGE = "registerbean";
    public static final String TRIGGER = PACKAGE + ".Trigger";
    public static final String GENERATED = PACKAGE + ".GeneratedLater";

    private static final String ROUND_PREFIX = "round";
    private static final String FINISH = "finish";

    /**
     * The callbacks in the order they were called, with the classes each round visited.
     */
    public static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    private static volatile boolean registerInFinish = true;

    private final List<ClassElement> round = new ArrayList<>();
    private ClassElement lastVisited;
    private int rounds;
    private boolean triggerGenerated;
    private boolean finishRegistered;

    /**
     * @param registerInFinish Whether a bean is registered from {@link #finish(VisitorContext)}
     */
    public static void setRegisterInFinish(boolean registerInFinish) {
        RoundsVisitor.registerInFinish = registerInFinish;
    }

    /**
     * Forgets what the previous compilations recorded.
     */
    public static void reset() {
        EVENTS.clear();
        registerInFinish = true;
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.getName().startsWith(PACKAGE + ".")) {
            round.add(element);
            lastVisited = element;
            if (element.getName().equals(TRIGGER) && !triggerGenerated) {
                triggerGenerated = true;
                generate(element, context);
            }
        }
    }

    @Override
    public void finishRound(VisitorContext context) {
        if (round.isEmpty()) {
            return;
        }
        rounds++;
        List<String> classes = round.stream().map(ClassElement::getName).sorted().toList();
        EVENTS.add(ROUND_PREFIX + rounds + classes);
        ClassElement beanType = context.getRequiredClassElement(RegisteredRecord.class.getName(), context.getElementAnnotationMetadataFactory());
        context.registerBean(beanType, round.toArray(new ClassElement[0]))
            .qualifier(ROUND_PREFIX + rounds)
            .annotate(AnnotationValue.builder(Recorded.class)
                .member("classes", classes.toArray(new String[0]))
                .member("phase", ROUND_PREFIX + rounds)
                .build());
        round.clear();
    }

    @Override
    public void finish(VisitorContext context) {
        if (lastVisited == null) {
            return;
        }
        EVENTS.add(FINISH);
        if (registerInFinish && !finishRegistered) {
            finishRegistered = true;
            ClassElement beanType = context.getRequiredClassElement(RegisteredRecord.class.getName(), context.getElementAnnotationMetadataFactory());
            context.registerBean(beanType, lastVisited)
                .qualifier(FINISH)
                .annotate(AnnotationValue.builder(Recorded.class).member("phase", FINISH).build());
        }
    }

    private static void generate(ClassElement origin, VisitorContext context) {
        String simpleName = GENERATED.substring(PACKAGE.length() + 1);
        String packageDeclaration = "package " + PACKAGE;
        String source = switch (context.getLanguage()) {
            case KOTLIN -> packageDeclaration + "\n\nclass " + simpleName + "\n";
            case GROOVY -> packageDeclaration + "\n\nclass " + simpleName + " {\n}\n";
            default -> packageDeclaration + ";\n\npublic class " + simpleName + " {\n}\n";
        };
        Optional<GeneratedFile> file = context.visitGeneratedSourceFile(PACKAGE, simpleName, origin);
        try {
            file.orElseThrow(() -> new IllegalStateException("No generated source file")).write(writer -> writer.write(source));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
