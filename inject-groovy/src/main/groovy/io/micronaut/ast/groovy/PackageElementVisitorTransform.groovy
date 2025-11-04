/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.ast.groovy

import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import io.micronaut.ast.groovy.visitor.GroovyPackageElement
import io.micronaut.ast.groovy.visitor.GroovyVisitorContext
import io.micronaut.ast.groovy.visitor.PackageLoadedVisitor
import io.micronaut.core.annotation.Generated
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.PackageNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

/**
 * Package element visitors.
 *
 * @author Denis Stepanov
 * @since 4.10
 */
@CompileStatic
// IMPORTANT NOTE: This transform runs in phase SEMANTIC_ANALYSIS so it runs before InjectTransform
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
final class PackageElementVisitorTransform implements ASTTransformation, CompilationUnitAware {

    private static ClassNode generatedNode = new ClassNode(Generated)
    protected static ThreadLocal<List<PackageLoadedVisitor>> packageLoadedVisitorsLocal = new ThreadLocal<>()
    private CompilationUnit compilationUnit

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        ModuleNode moduleNode = source.getAST()

        var visitorContext = new GroovyVisitorContext(source, compilationUnit)

        def packageLoadedVisitors = packageLoadedVisitorsLocal.get()
        if (!packageLoadedVisitors.isEmpty()) {
            // Packages should be visited before type element visitors
            PackageNode packageNode = moduleNode.getPackage()
            if (packageNode != null) {
                GroovyPackageElement el = new GroovyPackageElement(visitorContext, packageNode, visitorContext.getElementAnnotationMetadataFactory())
                for (var loadedVisitor in (packageLoadedVisitors)) {
                    if (loadedVisitor.matches(el) && packageNode.getAnnotations(generatedNode).empty) {
                        loadedVisitor.getVisitor().visitPackage(el, visitorContext)
                    }
                }
            }
        }

    }

    @Override
    void setCompilationUnit(CompilationUnit unit) {
        this.compilationUnit = unit
    }

}
