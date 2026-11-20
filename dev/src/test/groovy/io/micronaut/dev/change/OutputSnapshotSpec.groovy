package io.micronaut.dev.change

import io.micronaut.context.reload.ClassChange
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class OutputSnapshotSpec extends Specification {

    @TempDir
    Path classes

    void "the class changes are ordered by class name, whatever the file system lists first"() {
        given:
        Path pkg = Files.createDirectories(classes.resolve("example"))
        Path nested = Files.createDirectories(pkg.resolve("nested"))
        ["Zeta", "Other", "Alpha", "Greeter"].each { Files.write(pkg.resolve(it + ".class"), [1] as byte[]) }
        Files.write(nested.resolve("Inner.class"), [1] as byte[])
        Files.write(pkg.resolve("Removed.class"), [1] as byte[])
        OutputSnapshot before = OutputSnapshot.of([classes])

        when:
        ["Zeta", "Other", "Alpha", "Greeter"].each { Files.write(pkg.resolve(it + ".class"), [2] as byte[]) }
        Files.write(nested.resolve("Inner.class"), [2] as byte[])
        Files.delete(pkg.resolve("Removed.class"))
        Files.write(pkg.resolve("Added.class"), [1] as byte[])
        ChangeSet changes = before.diff(OutputSnapshot.of([classes]))

        then:
        changes.classes == [
                new ClassChange("example.Added", ClassChange.Kind.ADDED),
                new ClassChange("example.Alpha", ClassChange.Kind.MODIFIED),
                new ClassChange("example.Greeter", ClassChange.Kind.MODIFIED),
                new ClassChange("example.Other", ClassChange.Kind.MODIFIED),
                new ClassChange("example.Removed", ClassChange.Kind.REMOVED),
                new ClassChange("example.Zeta", ClassChange.Kind.MODIFIED),
                new ClassChange("example.nested.Inner", ClassChange.Kind.MODIFIED)
        ]
    }
}
