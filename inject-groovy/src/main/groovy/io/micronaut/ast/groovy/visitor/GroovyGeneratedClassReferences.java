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
package io.micronaut.ast.groovy.visitor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.DynamicVariable;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolves the references to generated classes that Groovy could not resolve in the sources of the compilation.
 *
 * <p>Groovy resolves the names of a source in semantic analysis, and the type element visitors run at the end of
 * canonicalization, so a class a visitor generates did not exist when the sources referencing it were resolved.
 * A name that does not resolve to a class in an expression is not an error there: Groovy keeps it as a dynamic
 * variable, looked up as a property at run time, and a qualified name as a chain of property expressions on it.
 * Dynamic code then fails at run time with a {@code MissingPropertyException}, and statically compiled code
 * fails when it is type checked, in instruction selection, with an undeclared variable.</p>
 *
 * <p>The generated sources are compiled up to the end of canonicalization before instruction selection starts,
 * so the operation this class registers, first of instruction selection, finds their classes and replaces every
 * such reference with the class, as the resolver would have done had the class existed: a dynamic variable named
 * after a generated class of the package of the source or of one of its star imports, or a property chain
 * spelling the qualified name of a generated class. Local variables, parameters, fields and properties are
 * never dynamic variables, so they keep precedence over a generated class of the same name.</p>
 *
 * <p>A generated class used as a type (a declaration, a constructor call, a cast, a single type import) or from
 * a static context cannot be resolved this way: Groovy reports those as errors in semantic analysis, before
 * any visitor has run.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class GroovyGeneratedClassReferences {

    private final List<SourceUnit> generatedSources = new ArrayList<>(4);
    private Map<String, ClassNode> generatedClasses = Map.of();
    private int resolvedSources;
    private boolean registered;

    /**
     * Adds a generated source whose classes are resolved from the other sources.
     *
     * @param source The generated source
     */
    void add(SourceUnit source) {
        generatedSources.add(source);
    }

    /**
     * Registers the operation that resolves the references, first of instruction selection.
     *
     * @param compilationUnit The compilation unit
     */
    void register(CompilationUnit compilationUnit) {
        if (!registered) {
            registered = true;
            compilationUnit.addFirstPhaseOperation(this::resolve, Phases.INSTRUCTION_SELECTION);
        }
    }

    private void resolve(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        Map<String, ClassNode> classes = generatedClasses();
        if (!classes.isEmpty()) {
            new ReferenceTransformer(source, classes).visitClass(classNode);
        }
    }

    private Map<String, ClassNode> generatedClasses() {
        if (resolvedSources != generatedSources.size()) {
            Map<String, ClassNode> classes = CollectionUtils.newHashMap(generatedSources.size());
            for (SourceUnit source : generatedSources) {
                ModuleNode module = source.getAST();
                if (module != null) {
                    for (ClassNode classNode : module.getClasses()) {
                        classes.put(classNode.getName(), classNode);
                    }
                }
            }
            generatedClasses = classes;
            resolvedSources = generatedSources.size();
        }
        return generatedClasses;
    }

    /**
     * Replaces the unresolved references to generated classes in the code of a class.
     */
    private static final class ReferenceTransformer extends ClassCodeExpressionTransformer {

        private final SourceUnit source;
        private final Map<String, ClassNode> generatedClasses;

        private ReferenceTransformer(SourceUnit source, Map<String, ClassNode> generatedClasses) {
            this.source = source;
            this.generatedClasses = generatedClasses;
        }

        @Override
        protected SourceUnit getSourceUnit() {
            return source;
        }

        @Override
        public Expression transform(Expression expression) {
            if (expression instanceof VariableExpression variable) {
                ClassNode type = variable.getAccessedVariable() instanceof DynamicVariable ? findBySimpleName(variable.getName()) : null;
                return type == null ? variable : classExpression(type, variable);
            }
            if (expression instanceof PropertyExpression property) {
                String name = qualifiedName(property);
                ClassNode type = name == null ? null : generatedClasses.get(name);
                if (type != null) {
                    return classExpression(type, property);
                }
            }
            if (expression instanceof ClosureExpression closure) {
                // the expressions of a closure, or of a lambda, are not transformed by default
                closure.visit(this);
                return closure;
            }
            return super.transform(expression);
        }

        @Nullable
        private ClassNode findBySimpleName(String name) {
            ModuleNode module = source.getAST();
            String packageName = module.getPackageName();
            ClassNode type = generatedClasses.get(packageName == null ? name : packageName + name);
            if (type != null) {
                return type;
            }
            for (ImportNode starImport : module.getStarImports()) {
                type = generatedClasses.get(starImport.getPackageName() + name);
                if (type != null) {
                    return type;
                }
            }
            return null;
        }

        @Nullable
        private static String qualifiedName(PropertyExpression property) {
            if (property.isSafe() || property.isSpreadSafe() || !(property.getProperty() instanceof ConstantExpression constant)
                || !(constant.getValue() instanceof String name)) {
                return null;
            }
            Expression object = property.getObjectExpression();
            String prefix;
            if (object instanceof VariableExpression variable && variable.getAccessedVariable() instanceof DynamicVariable) {
                prefix = variable.getName();
            } else if (object instanceof PropertyExpression objectProperty) {
                prefix = qualifiedName(objectProperty);
            } else {
                prefix = null;
            }
            return prefix == null ? null : prefix + '.' + name;
        }

        private static ClassExpression classExpression(ClassNode type, Expression reference) {
            ClassExpression expression = new ClassExpression(type);
            expression.setSourcePosition(reference);
            return expression;
        }
    }
}
