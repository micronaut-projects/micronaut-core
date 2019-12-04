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

import com.fasterxml.aalto.stax.InputFactoryImpl;
import io.micronaut.core.annotation.Internal;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.StreamReaderDelegate;

/**
 * Stream reader that pairs xml stream with underlying byte array.
 *
 * @author sergey.vishnyakov
 */
@Internal
public final class ByteArrayXmlStreamReader extends StreamReaderDelegate {

    private static final InputFactoryImpl XML_STREAM_FACTORY = new InputFactoryImpl();

    private byte[] bytes;

    /**
     * @param bytes raw representation of xml
     */
    public ByteArrayXmlStreamReader(byte[] bytes) throws XMLStreamException {
        super(toReader(bytes));
        this.bytes = bytes;
    }

    private static XMLStreamReader toReader(byte[] bytes) throws XMLStreamException {
        return XML_STREAM_FACTORY.createAsyncFor(bytes);
    }

    /**
     * @return byte array of the underlying stream.
     */
    byte[] getBytes() {
        return this.bytes;
    }

    public XMLStreamReader copy() throws XMLStreamException {
        return new ByteArrayXmlStreamReader(bytes);
    }
}
