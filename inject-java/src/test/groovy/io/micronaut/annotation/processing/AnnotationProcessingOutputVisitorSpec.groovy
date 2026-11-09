package io.micronaut.annotation.processing

import io.micronaut.inject.ast.Element
import spock.lang.Specification

import javax.annotation.processing.Filer
import javax.tools.JavaFileObject
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

class AnnotationProcessingOutputVisitorSpec extends Specification {

    Logger logger = Logger.getLogger(AnnotationProcessingOutputVisitor.name)
    Level previousLevel
    List<LogRecord> records = []
    Handler handler = new Handler() {
        @Override
        void publish(LogRecord record) {
            records << record
        }

        @Override
        void flush() {
        }

        @Override
        void close() {
        }
    }

    def setup() {
        previousLevel = logger.level
        logger.level = Level.FINE
        logger.addHandler(handler)
    }

    def cleanup() {
        logger.removeHandler(handler)
        logger.level = previousLevel
    }

    def 'a class whose originating elements provide no native element is written with none, and logged'() {
        given:
        def filer = Mock(Filer)
        def visitor = new AnnotationProcessingOutputVisitor(filer)

        when:
        visitor.visitClass('test.Foo', withoutNativeElement()).close()

        then:
        1 * filer.createClassFile('test.Foo', { it.length == 0 }) >> Stub(JavaFileObject) {
            openOutputStream() >> new ByteArrayOutputStream()
        }
        records*.parameters*.getAt(0) == ['test.Foo']
    }

    def 'a class given no originating element is not logged'() {
        given:
        def filer = Mock(Filer)
        def visitor = new AnnotationProcessingOutputVisitor(filer)

        when:
        visitor.visitClass('test.Foo', new Element[0]).close()

        then:
        1 * filer.createClassFile('test.Foo', { it.length == 0 }) >> Stub(JavaFileObject) {
            openOutputStream() >> new ByteArrayOutputStream()
        }
        records.empty
    }

    def 'a generated file whose originating elements provide no native element is logged'() {
        given:
        def visitor = new AnnotationProcessingOutputVisitor(Mock(Filer))

        when:
        def file = visitor.visitGeneratedFile('test/file.txt', withoutNativeElement())

        then:
        file.present
        records*.parameters*.getAt(0) == ['test/file.txt']
    }

    def 'nothing is logged unless debug logging is on'() {
        given:
        logger.level = Level.INFO
        def visitor = new AnnotationProcessingOutputVisitor(Mock(Filer))

        when:
        visitor.visitGeneratedFile('test/file.txt', withoutNativeElement())

        then:
        records.empty
    }

    private Element withoutNativeElement() {
        Stub(Element) {
            getNativeType() >> new Object()
        }
    }
}
