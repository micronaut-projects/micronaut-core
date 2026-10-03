package io.micronaut.inject.descriptor

import io.micronaut.http.server.netty.NettyHttpServer
import io.micronaut.inject.BeanDefinitionReference
import spock.lang.Specification
import spock.lang.TempDir

import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * A runtime released before descriptors existed finds the definitions by the names of their entries and never opens
 * one, so the content of an entry changes nothing it finds. This runs the loader of releases on a jar of this build.
 *
 * <p>Micronaut does not otherwise support classes compiled by a newer release on an older runtime: this only shows
 * that the content of the entries, which is all this build changes for those loaders, is inert for them.</p>
 */
class ReleasedLoaderSpec extends Specification {

    private static final String SERVICE = BeanDefinitionReference.name
    private static final String ENTRIES = "META-INF/micronaut/" + SERVICE + "/"

    @TempDir
    private Path temp

    void "the loader of micronaut-core #version finds the same definitions in entries with descriptors as in empty ones"() {
        given: "the loader of the release, in a class loader of its own"
        File core = new File(System.getProperty("released.micronaut-core.$version"))
        URLClassLoader release = new URLClassLoader([core.toURI().toURL()] as URL[], ClassLoader.platformClassLoader)
        Class<?> loader = release.loadClass('io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils')
        Method findAll = loader.getMethod('findAllMicronautMetaServices', ClassLoader)
        Method findEntries = loader.getMethod('findMicronautMetaServiceEntries', ClassLoader, String)

        and: "the jar of a module of this build, and a copy of it with every entry emptied"
        Path jar = Path.of(NettyHttpServer.protectionDomain.codeSource.location.toURI())
        Path copy = emptiedCopy(jar, temp.resolve('emptied.jar'))
        URLClassLoader withDescriptors = new URLClassLoader([jar.toUri().toURL()] as URL[], (ClassLoader) null)
        URLClassLoader emptied = new URLClassLoader([copy.toUri().toURL()] as URL[], (ClassLoader) null)

        expect: "the loader is the one of the release, and the jar has descriptors"
        loader.protectionDomain.codeSource.location.path.endsWith("/micronaut-core-${version}.jar")
        Files.isRegularFile(jar) && jar.fileName.toString().startsWith('micronaut-http-server-netty-')
        described(jar) > 10
        described(copy) == 0

        when:
        Map<String, Set<String>> found = (Map<String, Set<String>>) findAll.invoke(null, withDescriptors)
        Map<String, Set<String>> foundEmptied = (Map<String, Set<String>>) findAll.invoke(null, emptied)
        Set<String> entries = (Set<String>) findEntries.invoke(null, withDescriptors, SERVICE)
        Set<String> entriesEmptied = (Set<String>) findEntries.invoke(null, emptied, SERVICE)

        then:
        found[SERVICE].size() > 10
        found == foundEmptied
        entries == found[SERVICE]
        entries == entriesEmptied

        cleanup:
        withDescriptors?.close()
        emptied?.close()
        release?.close()

        where:
        version << ['4.10.30', '5.2.12']
    }

    /**
     * @return The number of entries of definitions with content
     */
    private static int described(Path jar) {
        new ZipFile(jar.toFile()).withCloseable { zip ->
            zip.entries().toList().count { ZipEntry entry -> entry.name.startsWith(ENTRIES) && entry.name != ENTRIES && entry.size > 0 }
        }
    }

    /**
     * Copies a jar with the entries of the definitions emptied, as a processor without descriptors writes them.
     */
    private static Path emptiedCopy(Path jar, Path target) {
        ZipFile zip = new ZipFile(jar.toFile())
        ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(target))
        try {
            zip.entries().toList().each { ZipEntry entry -> copy(zip, entry, out) }
        } finally {
            out.close()
            zip.close()
        }
        return target
    }

    private static void copy(ZipFile zip, ZipEntry entry, ZipOutputStream out) {
        out.putNextEntry(new ZipEntry(entry.name))
        if (!entry.directory && !entry.name.startsWith(ENTRIES)) {
            zip.getInputStream(entry).withCloseable { out << it }
        }
        out.closeEntry()
    }
}
