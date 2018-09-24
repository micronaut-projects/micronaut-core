/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.ast.groovy

import groovy.transform.CompileStatic
import io.micronaut.ast.groovy.utils.AstMessageUtils
import io.micronaut.ast.groovy.visitor.GroovyVisitorContext
import io.micronaut.ast.groovy.visitor.LoadedVisitor
import io.micronaut.core.io.service.ServiceDefinition
import io.micronaut.core.io.service.SoftServiceLoader
import io.micronaut.core.reflect.InstantiationUtils
import io.micronaut.inject.visitor.TypeElementVisitor
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

/**
 * Loads the type element visitors.
 *
 * @author James Kleeh
 * @since 1.0
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.INITIALIZATION)
class TypeElementVisitorStart implements ASTTransformation {

    public static final String ELEMENT_VISITORS_PROPERTY = "micronaut.element.visitors"

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {

        Map<String, LoadedVisitor> loadedVisitors = TypeElementVisitorTransform.loadedVisitors

        if (loadedVisitors == null) {
            loadedVisitors = [:]

            ModuleNode moduleNode = source.getAST()
            SoftServiceLoader serviceLoader = SoftServiceLoader.load(TypeElementVisitor, TypeElementVisitorStart.classLoader)
            for (ServiceDefinition<TypeElementVisitor> definition: serviceLoader) {
                if (definition.isPresent()) {
                    TypeElementVisitor visitor = definition.load()
                    try {
                        LoadedVisitor newLoadedVisitor = new LoadedVisitor(visitor)
                        loadedVisitors.put(definition.getName(), newLoadedVisitor)
                    } catch (TypeNotPresentException e) {
                        // skip, all classes not on classpath
                    } catch (NoClassDefFoundError e) {
                        // skip, all classes not on classpath
                    } catch (ClassNotFoundException e) {
                        // skip, all classes not on classpath
                    }
                }
            }


            def val = System.getProperty(ELEMENT_VISITORS_PROPERTY)
            if (val) {
                for (v in val.split(",")) {
                    def visitor = InstantiationUtils.tryInstantiate(v, source.classLoader).orElse(null)
                    if (visitor instanceof TypeElementVisitor) {
                        LoadedVisitor newLoadedVisitor = new LoadedVisitor(visitor)
                        loadedVisitors.put(visitor.getClass().getName(), newLoadedVisitor)
                    }
                }
            }

            for(loadedVisitor in loadedVisitors.values()) {
                try {
                    loadedVisitor.start(new GroovyVisitorContext(source))
                } catch (Throwable e) {
                    AstMessageUtils.error(
                            source,
                            moduleNode,
                            "Error starting type visitor [$loadedVisitor.visitor]: $e.message")
                }
            }
        }

        TypeElementVisitorTransform.loadedVisitors = loadedVisitors
    }
}
