/*
 *
 *  * Copyright 2017-2019 original authors
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */
package io.micronaut.xml.server.convert;

import io.micronaut.core.annotation.Internal;

import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.StreamReaderDelegate;
import java.util.function.Supplier;

/**
 * Stream reader that pairs xml stream with underlying byte array.
 *
 * @author sergey.vishnyakov
 */
@Internal
public final class ByteArrayXmlStreamReader extends StreamReaderDelegate {
    private byte[] bytes;
    private Supplier<XMLStreamReader> cloneAction;

    /**
     * @param reader stream reader we will delegate to
     * @param bytes  byte array that was fed to the given xml stream
     */
    public ByteArrayXmlStreamReader(XMLStreamReader reader, byte[] bytes, Supplier<XMLStreamReader> cloneAction) {
        super(reader);
        this.bytes = bytes;
        this.cloneAction = cloneAction;
    }

    /**
     * @return byte array of the underlying stream.
     */
    byte[] getBytes() {
        return this.bytes;
    }

    public XMLStreamReader copy() {
        return cloneAction.get();
    }
}
