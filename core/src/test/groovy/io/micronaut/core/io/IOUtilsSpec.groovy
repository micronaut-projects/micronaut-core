package io.micronaut.core.io

import org.opentest4j.TestAbortedException
import spock.lang.Issue
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class IOUtilsSpec extends Specification {
    def 'nested access to same zip file'() {
        given:
        Path zipPath = Files.createTempFile("micronaut-ioutils-spec", ".zip")
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry("foo/bar.txt"))
            zos.write("baz".getBytes(StandardCharsets.UTF_8))
            zos.closeEntry()
        }

        def visitedOuter = []
        def visitedInner = []

        when:
        IOUtils.eachFile(URI.create('jar:' + zipPath.toUri()), 'foo', entry -> {
            visitedOuter.add(entry.getFileName().toString())
            IOUtils.eachFile(URI.create('jar:' + zipPath.toUri()), 'foo', entryI -> {
                visitedInner.add(entryI.getFileName().toString())
            })
        })
        then:
        visitedOuter == ['bar.txt']
        visitedInner == ['bar.txt']

        cleanup:
        Files.deleteIfExists(zipPath)
    }

    def 'access to nested zip files'() {
        given:
        Path zipPath = Files.createTempFile("micronaut-ioutils-spec", ".zip")
        try (ZipOutputStream outer = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            outer.putNextEntry(new ZipEntry("foo/inner.zip"))

            ZipOutputStream inner = new ZipOutputStream(outer)
            inner.putNextEntry(new ZipEntry("bar/baz.txt"))
            inner.write("bla".getBytes(StandardCharsets.UTF_8))
            inner.closeEntry()
            inner.finish()

            outer.closeEntry()
        }

        def visitedInner = []
        def textInner = []

        when:
        IOUtils.eachFile(URI.create('jar:' + zipPath.toUri() + '!/foo/inner.zip!/xyz'), 'bar', entry -> {
            visitedInner.add(entry.getFileName().toString())
            textInner = Files.readAllLines(entry)
        })
        then:
        visitedInner == ['baz.txt']
        textInner == ['bla']

        cleanup:
        Files.deleteIfExists(zipPath)
    }

    def 'weird file name'() {
        given:
        Path tempDir = Files.createTempDirectory("micronaut-ioutils-spec")
        Path file
        try {
            file = tempDir.resolve("foo?bar.zip")
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
                zos.putNextEntry(new ZipEntry("foo/bar.txt"))
                zos.write("baz".getBytes(StandardCharsets.UTF_8))
                zos.closeEntry()
            }
        } catch (Exception e) {
            throw new TestAbortedException("Failed to create file with weird name (maybe FS doesn't support " +
                    "the name itself)", e)
        }

        def visitedOuter = []

        when:
        IOUtils.eachFile(URI.create('jar:' + file.toUri()), 'foo', entry -> {
            visitedOuter.add(entry.getFileName().toString())
        })
        then:
        visitedOuter == ['bar.txt']

        cleanup:
        if (file != null) {
            Files.deleteIfExists(file)
        }
        Files.deleteIfExists(tempDir)
    }

    @Issue('https://github.com/grails/grails-core/issues/12625/')
    def 'dir inside jar'() {
        given:
        Path zipPath = Files.createTempFile("micronaut-ioutils-spec", ".zip")
        try (ZipOutputStream outer = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            outer.putNextEntry(new ZipEntry("foo/bar/baz/test.txt"))
            outer.write("bla".getBytes(StandardCharsets.UTF_8))
            outer.closeEntry()
        }

        def visitedInner = []
        def textInner = []

        when:
        IOUtils.eachFile(URI.create('jar:' + zipPath.toUri() + '!/foo/bar!/xyz'), 'baz', entry -> {
            visitedInner.add(entry.getFileName().toString())
            textInner = Files.readAllLines(entry)
        })
        then:
        visitedInner == ['test.txt']
        textInner == ['bla']

        cleanup:
        Files.deleteIfExists(zipPath)
    }
}
