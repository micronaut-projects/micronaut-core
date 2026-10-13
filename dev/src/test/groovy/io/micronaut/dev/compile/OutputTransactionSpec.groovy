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
package io.micronaut.dev.compile

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class OutputTransactionSpec extends Specification {

    @TempDir
    Path directory

    void "a rollback that cannot restore an output keeps it in the backup and reports the failure"() {
        given: "an output removed into a transaction"
        Path output = Files.createDirectories(directory.resolve("classes/example"))
        Path foo = output.resolve("Foo.class")
        Files.writeString(foo, "original")
        Path backup = directory.resolve("classes.backup")
        OutputTransaction transaction = new OutputTransaction(backup)
        transaction.remove(foo)

        when: "the output's directory is obstructed by a file, and the transaction rolls back"
        Files.delete(output)
        Files.writeString(output, "obstruction")
        def cause = new IOException("promotion failed")
        transaction.rollback(cause)

        then: "the failure is reported, with the failure the rollback was for, and the original is still in the backup"
        IOException e = thrown()
        e.message.contains(foo.toString())
        e.message.contains(backup.toString())
        e.suppressed.contains(cause)
        Files.list(backup).withCloseable { files -> files.map { Files.readString(it) }.toList() } == ["original"]

        when: "the obstruction goes, and the rollback is retried"
        Files.delete(output)
        transaction.rollback()

        then: "the original is restored and only then the backup goes"
        Files.readString(foo) == "original"
        !Files.exists(backup)
    }

    void "a new transaction keeps aside a backup a failed rollback left behind"() {
        given: "a backup holding an output a rollback could not restore"
        Path output = Files.createDirectories(directory.resolve("classes/example"))
        Path foo = output.resolve("Foo.class")
        Files.writeString(foo, "original")
        Path backup = directory.resolve("classes.backup")
        OutputTransaction transaction = new OutputTransaction(backup)
        transaction.remove(foo)
        Files.delete(output)
        Files.writeString(output, "obstruction")
        try {
            transaction.rollback()
        } catch (IOException ignored) {
        }

        when: "the next transaction starts over the same backup directory"
        new OutputTransaction(backup)

        then: "the original is not deleted but moved aside"
        !Files.exists(backup)
        def kept = Files.list(directory).withCloseable { files -> files.filter { it.fileName.toString().startsWith("classes.backup.unrestored-") }.toList() }
        kept.size() == 1
        Files.list(kept[0]).withCloseable { files -> files.map { Files.readString(it) }.toList() } == ["original"]
    }

    void "a replacement that cannot go back to staging is not retried once the original is restored over it"() {
        given: "an output replaced by a staged file"
        Path output = Files.createDirectories(directory.resolve("classes/example"))
        Path foo = output.resolve("Foo.class")
        Files.writeString(foo, "original")
        Path staging = Files.createDirectories(directory.resolve("staging"))
        Path staged = staging.resolve("Foo.class")
        Files.writeString(staged, "new")
        Path backup = directory.resolve("classes.backup")
        OutputTransaction transaction = new OutputTransaction(backup)
        transaction.put(staged, foo)

        when: "the staging directory is obstructed, so the replacement cannot go back, and the transaction rolls back"
        Files.delete(staging)
        Files.writeString(staging, "obstruction")
        transaction.rollback()

        then: "the original is restored over the replacement, and nothing is left to retry"
        Files.readString(foo) == "original"
        !Files.exists(backup)

        when: "a later rollback runs once the obstruction is gone"
        Files.delete(staging)
        transaction.rollback()

        then: "the restored original stays"
        Files.readString(foo) == "original"
    }

    void "a rollback that restores every output deletes the backup"() {
        given:
        Path output = Files.createDirectories(directory.resolve("classes/example"))
        Path foo = output.resolve("Foo.class")
        Files.writeString(foo, "original")
        Path staged = Files.createDirectories(directory.resolve("staging")).resolve("Foo.class")
        Files.writeString(staged, "new")
        Path backup = directory.resolve("classes.backup")
        OutputTransaction transaction = new OutputTransaction(backup)

        when:
        transaction.put(staged, foo)
        transaction.rollback(new IOException("promotion failed"))

        then:
        Files.readString(foo) == "original"
        Files.readString(staged) == "new"
        !Files.exists(backup)
    }
}
