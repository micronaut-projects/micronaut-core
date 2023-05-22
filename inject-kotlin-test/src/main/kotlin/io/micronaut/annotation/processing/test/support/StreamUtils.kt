/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.annotation.processing.test.support

import java.io.*


/** A combined stream that writes to all the output streams in [streams]. */
@Suppress("MemberVisibilityCanBePrivate")
internal class TeeOutputStream(val streams: Collection<OutputStream>) : OutputStream() {

    constructor(vararg streams: OutputStream) : this(streams.toList())

    @Synchronized
    @Throws(IOException::class)
    override fun write(b: Int) {
        for(stream in streams)
            stream.write(b)
    }

    @Synchronized
    @Throws(IOException::class)
    override fun write(b: ByteArray) {
        for(stream in streams)
            stream.write(b)
    }

    @Synchronized
    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        for(stream in streams)
            stream.write(b, off, len)
    }

    @Throws(IOException::class)
    override fun flush() {
        for(stream in streams)
            stream.flush()
    }

    @Throws(IOException::class)
    override fun close() {
        closeImpl(streams)
    }

    @Throws(IOException::class)
    private fun closeImpl(streamsToClose : Collection<OutputStream>) {
        try {
            streamsToClose.firstOrNull()?.close()
        }
        finally {
            if(streamsToClose.size > 1)
                closeImpl(streamsToClose.drop(1))
        }
    }
}
