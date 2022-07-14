package io.micronaut.core.io

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
}
