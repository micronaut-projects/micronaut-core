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
package io.micronaut.python.compiler;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Records resolved source dependencies from the javac attribution phase.
 */
final class JavaCompilationTracker implements TaskListener {
    private final Trees trees;
    private final Set<String> analyzedSources = new LinkedHashSet<>();
    private final Map<String, Set<String>> dependencies = new LinkedHashMap<>();
    private final Map<String, Set<String>> declaredTypes = new LinkedHashMap<>();
    private final Map<String, String> generatedSourceOrigins = new LinkedHashMap<>();
    private final Set<String> pythonGeneratedSources = new LinkedHashSet<>();

    JavaCompilationTracker(JavacTask task) {
        this.trees = Trees.instance(task);
    }

    @Override
    public void finished(TaskEvent event) {
        if (event.getKind() != TaskEvent.Kind.ANALYZE || event.getCompilationUnit() == null) {
            return;
        }
        CompilationUnitTree compilationUnit = event.getCompilationUnit();
        String source = sourceKey(compilationUnit.getSourceFile());
        if (source == null) {
            return;
        }
        analyzedSources.add(source);
        dependencies.computeIfAbsent(source, ignored -> new LinkedHashSet<>());
        declaredTypes.computeIfAbsent(source, ignored -> new LinkedHashSet<>());
        new ReferenceScanner(source).scan(compilationUnit, null);
    }

    IncrementalCompilationTrace trace(Map<String, Set<String>> outputs) {
        return new IncrementalCompilationTrace(
            analyzedSources,
            dependencies,
            declaredTypes,
            outputs,
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            true
        );
    }

    String sourceKey(Element element) {
        TypeElement owner = enclosingType(element);
        if (owner == null) {
            return null;
        }
        TreePath ownerPath = trees.getPath(owner);
        return ownerPath == null
            ? null
            : sourceKey(ownerPath.getCompilationUnit().getSourceFile());
    }

    String sourceKey(JavaFileObject source) {
        String key = fileSourceKey(source);
        if (key == null) {
            return null;
        }
        return generatedSourceOrigins.getOrDefault(key, key);
    }

    void recordGeneratedSource(JavaFileObject generatedSource,
                               boolean pythonProcessor,
                               Element... originatingElements) {
        if (originatingElements.length != 1) {
            return;
        }
        String generated = fileSourceKey(generatedSource);
        String origin = sourceKey(originatingElements[0]);
        if (generated != null && origin != null) {
            generatedSourceOrigins.put(generated, origin);
            if (pythonProcessor) {
                pythonGeneratedSources.add(generated);
            }
        }
    }

    boolean isPythonGeneratedSource(JavaFileObject source) {
        String key = fileSourceKey(source);
        return key != null && pythonGeneratedSources.contains(key);
    }

    private static TypeElement enclosingType(Element element) {
        Element current = element;
        while (current != null && !(current instanceof TypeElement)) {
            current = current.getEnclosingElement();
        }
        return current instanceof TypeElement typeElement ? typeElement : null;
    }

    private static String fileSourceKey(JavaFileObject source) {
        if (source == null) {
            return null;
        }
        try {
            URI uri = source.toUri();
            if (!"file".equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            Path path = Path.of(uri);
            return Files.exists(path)
                ? path.toRealPath().normalize().toString()
                : path.toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            return null;
        }
    }

    private final class ReferenceScanner extends TreePathScanner<Void, Void> {
        private final String source;

        private ReferenceScanner(String source) {
            this.source = source;
        }

        @Override
        public Void visitClass(com.sun.source.tree.ClassTree node, Void unused) {
            Element element = trees.getElement(getCurrentPath());
            if (element instanceof TypeElement typeElement) {
                declaredTypes.get(source).add(typeElement.getQualifiedName().toString());
            }
            return super.visitClass(node, null);
        }

        @Override
        public Void visitIdentifier(IdentifierTree node, Void unused) {
            recordReference(getCurrentPath());
            return super.visitIdentifier(node, null);
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree node, Void unused) {
            recordReference(getCurrentPath());
            return super.visitMemberSelect(node, null);
        }

        @Override
        public Void visitMemberReference(MemberReferenceTree node, Void unused) {
            recordReference(getCurrentPath());
            return super.visitMemberReference(node, null);
        }

        private void recordReference(TreePath path) {
            Element element = trees.getElement(path);
            String dependency = sourceKey(element);
            if (dependency != null && !source.equals(dependency)) {
                dependencies.get(source).add(dependency);
            }
        }
    }
}
