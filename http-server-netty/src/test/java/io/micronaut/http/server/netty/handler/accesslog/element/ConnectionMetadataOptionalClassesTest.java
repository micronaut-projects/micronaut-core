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
package io.micronaut.http.server.netty.handler.accesslog.element;

import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The QUIC channel type comes from an optional netty module. Access logging must keep working
 * without it for connections that are not plain sockets (unix domain sockets, for example), where
 * {@link ConnectionMetadata#ofNettyChannel} has to consult the optional types.
 */
class ConnectionMetadataOptionalClassesTest {
    private static final String ELEMENT_PACKAGE = ConnectionMetadata.class.getPackageName() + ".";
    private static final String QUIC_PACKAGE = "io.netty.handler.codec.quic.";

    @Test
    void optionalTypesArePresentOnTheTestClasspath() {
        assertNotNull(ConnectionMetadataImpl.QUIC_CHANNEL);
        assertNotNull(ConnectionMetadataImpl.DOMAIN_SOCKET_ADDRESS);
        assertNotNull(ConnectionMetadataImpl.DOMAIN_SOCKET_CHANNEL);
    }

    @Test
    void missingOptionalTypeIsNull() {
        assertNull(ConnectionMetadataImpl.optionalClass("io.netty.handler.codec.quic.DoesNotExist"));
    }

    @Test
    void nonSocketChannelMetadataWorksWithoutTheQuicModule() throws Exception {
        ClassLoader hiding = new HidingClassLoader(getClass().getClassLoader());
        Class<?> metadata = Class.forName(ConnectionMetadata.class.getName(), true, hiding);
        Class<?> impl = Class.forName(ConnectionMetadataImpl.class.getName(), true, hiding);
        // the hiding loader defines a distinct runtime package, so package access does not apply
        assertNull(staticField(impl, "QUIC_CHANNEL"), "QUIC channel type should be absent");
        assertNotNull(staticField(impl, "DOMAIN_SOCKET_ADDRESS"), "unix-common is not hidden");

        // EmbeddedChannel is not a SocketChannel, so this is the branch that consults the optional types
        Method ofNettyChannel = metadata.getDeclaredMethod("ofNettyChannel", Channel.class);
        ofNettyChannel.setAccessible(true);
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            Object result = ofNettyChannel.invoke(null, channel);
            assertNotNull(result);
            assertEquals(hiding, result.getClass().getClassLoader());
        } finally {
            channel.finishAndReleaseAll();
        }

        Method getHostName = metadata.getDeclaredMethod("getHostName", SocketAddress.class);
        getHostName.setAccessible(true);
        assertEquals(Optional.of("localhost"), getHostName.invoke(null, new InetSocketAddress("localhost", 0)));
        assertEquals(Optional.empty(), getHostName.invoke(null, new SocketAddress() { }));
    }

    private static Object staticField(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    /**
     * Child-first for the access log element package, so those classes are initialized inside
     * this loader; refuses the QUIC package outright; delegates everything else to the parent.
     */
    private static final class HidingClassLoader extends ClassLoader {
        HidingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(QUIC_PACKAGE)) {
                throw new ClassNotFoundException(name);
            }
            if (!name.startsWith(ELEMENT_PACKAGE)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    byte[] bytes = readClass(name);
                    loaded = defineClass(name, bytes, 0, bytes.length);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        private byte[] readClass(String name) throws ClassNotFoundException {
            try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (in == null) {
                    throw new ClassNotFoundException(name);
                }
                return in.readAllBytes();
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }
}
