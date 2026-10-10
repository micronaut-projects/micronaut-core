package io.micronaut.visitors

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

/**
 * The processor options are exposed as system properties during the compilation, and must not leak
 * into the later compilations of the same JVM.
 */
class ProcessorOptionsSystemPropertiesSpec extends AbstractTypeElementSpec {

    static final String OPTION = 'micronaut.test.processorOptionsLeak'

    private List<String> compilerOptions = []

    def setup() {
        OptionsVisitor.reset()
    }

    def cleanup() {
        System.clearProperty(OPTION)
    }

    void 'an option of one compilation is not seen by a later compilation'() {
        when:
        compilerOptions = ["-A$OPTION=true".toString()]
        buildBeanDefinition('test.First', '''
package test;

@jakarta.inject.Singleton
class First {
}
''')

        then: 'the option is visible during the compilation, from the options and from the system properties'
        OptionsVisitor.visitOptions == ['true']
        OptionsVisitor.visitSystemProperties == ['true']
        OptionsVisitor.finishOptions == ['true']
        OptionsVisitor.finishSystemProperties == ['true']

        and: 'it is removed once the compilation is over'
        System.getProperty(OPTION) == null

        when:
        OptionsVisitor.reset()
        compilerOptions = []
        buildBeanDefinition('test.Second', '''
package test;

@jakarta.inject.Singleton
class Second {
}
''')

        then:
        OptionsVisitor.visitOptions == [null]
        OptionsVisitor.visitSystemProperties == [null]
        OptionsVisitor.finishOptions == [null]
        OptionsVisitor.finishSystemProperties == [null]
        System.getProperty(OPTION) == null
    }

    void 'a system property overridden by an option is restored once the compilation is over'() {
        given:
        System.setProperty(OPTION, 'previous')

        when:
        compilerOptions = ["-A$OPTION=true".toString()]
        buildBeanDefinition('test.First', '''
package test;

@jakarta.inject.Singleton
class First {
}
''')

        then:
        OptionsVisitor.visitOptions == ['true']
        OptionsVisitor.visitSystemProperties == ['true']
        System.getProperty(OPTION) == 'previous'

        when:
        OptionsVisitor.reset()
        compilerOptions = []
        buildBeanDefinition('test.Second', '''
package test;

@jakarta.inject.Singleton
class Second {
}
''')

        then: 'a system property set outside of the compilation is still an option'
        OptionsVisitor.visitOptions == ['previous']
        OptionsVisitor.visitSystemProperties == ['previous']
        System.getProperty(OPTION) == 'previous'
    }

    @Override
    protected JavaParser newJavaParser() {
        def visitor = new OptionsVisitor()
        def options = compilerOptions
        return new JavaParser() {
            @Override
            protected Set<String> getCompilerOptions() {
                return super.getCompilerOptions() + options
            }

            @Override
            protected TypeElementVisitorProcessor getTypeElementVisitorProcessor() {
                return new TypeElementVisitorProcessor() {
                    @Override
                    protected Collection<TypeElementVisitor> findTypeElementVisitors() {
                        return [visitor]
                    }
                }
            }
        }
    }

    static class OptionsVisitor implements TypeElementVisitor<Object, Object> {

        static List<String> visitOptions = []
        static List<String> visitSystemProperties = []
        static List<String> finishOptions = []
        static List<String> finishSystemProperties = []

        static void reset() {
            visitOptions = []
            visitSystemProperties = []
            finishOptions = []
            finishSystemProperties = []
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            visitOptions << context.options.get(OPTION)
            visitSystemProperties << System.getProperty(OPTION)
        }

        @Override
        void finish(VisitorContext context) {
            // finish is called once per round, only the last one is after the processing is over
            finishOptions = [context.options.get(OPTION)]
            finishSystemProperties = [System.getProperty(OPTION)]
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }
    }
}
