/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.netty;

import io.micronaut.core.annotation.Internal;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DiskAttribute;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.MemoryAttribute;
import io.netty.handler.codec.http.multipart.MemoryFileUpload;
import io.netty.handler.codec.http.multipart.MixedAttribute;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Copied from {@link io.netty.handler.codec.http.multipart.DefaultHttpDataFactory}, but with
 * {@link MixedFileUploadPatched}, pending fix for https://github.com/netty/netty/issues/12627.
 */
@Internal
public class MicronautHttpDataFactory implements HttpDataFactory {

    /**
     * Proposed default MINSIZE as 16 KB.
     */
    public static final long MINSIZE = 0x4000;
    /**
     * Proposed default MAXSIZE = -1 as UNLIMITED.
     */
    public static final long MAXSIZE = -1;

    private final boolean useDisk;

    private final boolean checkSize;

    private long minSize;

    private long maxSize = MAXSIZE;

    private Charset charset = HttpConstants.DEFAULT_CHARSET;

    private String baseDir;

    private boolean deleteOnExit; // false is a good default cause true leaks

    /**
     * Keep all {@link HttpData}s until cleaning methods are called.
     * We need to use {@link IdentityHashMap} because different requests may be equal.
     * See {@link io.netty.handler.codec.http.DefaultHttpRequest#hashCode} and
     * {@link io.netty.handler.codec.http.DefaultHttpRequest#equals}.
     * Similarly, when removing data items, we need to check their identities because
     * different data items may be equal.
     */
    private final Map<HttpRequest, List<HttpData>> requestFileDeleteMap =
        Collections.synchronizedMap(new IdentityHashMap<HttpRequest, List<HttpData>>());

    /**
     * HttpData will be in memory if less than default size (16KB).
     * The type will be Mixed.
     */
    public MicronautHttpDataFactory() {
        useDisk = false;
        checkSize = true;
        minSize = MINSIZE;
    }

    public MicronautHttpDataFactory(Charset charset) {
        this();
        this.charset = charset;
    }

    public MicronautHttpDataFactory(boolean useDisk) {
        this.useDisk = useDisk;
        checkSize = false;
    }

    public MicronautHttpDataFactory(boolean useDisk, Charset charset) {
        this(useDisk);
        this.charset = charset;
    }

    public MicronautHttpDataFactory(long minSize) {
        useDisk = false;
        checkSize = true;
        this.minSize = minSize;
    }

    public MicronautHttpDataFactory(long minSize, Charset charset) {
        this(minSize);
        this.charset = charset;
    }

    /**
     * Override global {@link DiskAttribute#baseDirectory} and {@link DiskFileUpload#baseDirectory} values.
     *
     * @param baseDir directory path where to store disk attributes and file uploads.
     */
    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * Override global {@link DiskAttribute#deleteOnExitTemporaryFile} and
     * {@link DiskFileUpload#deleteOnExitTemporaryFile} values.
     *
     * @param deleteOnExit true if temporary files should be deleted with the JVM, false otherwise.
     */
    public void setDeleteOnExit(boolean deleteOnExit) {
        this.deleteOnExit = deleteOnExit;
    }

    @Override
    public void setMaxLimit(long maxSize) {
        this.maxSize = maxSize;
    }

    /**
     * @return the associated list of {@link HttpData} for the request
     */
    private List<HttpData> getList(HttpRequest request) {
        List<HttpData> list = requestFileDeleteMap.get(request);
        if (list == null) {
            list = new ArrayList<HttpData>();
            requestFileDeleteMap.put(request, list);
        }
        return list;
    }

    @Override
    public Attribute createAttribute(HttpRequest request, String name) {
        if (useDisk) {
            Attribute attribute = new DiskAttribute(name, charset, baseDir, deleteOnExit);
            attribute.setMaxSize(maxSize);
            List<HttpData> list = getList(request);
            list.add(attribute);
            return attribute;
        }
        if (checkSize) {
            Attribute attribute = new MixedAttribute(name, minSize, charset, baseDir, deleteOnExit);
            attribute.setMaxSize(maxSize);
            List<HttpData> list = getList(request);
            list.add(attribute);
            return attribute;
        }
        MemoryAttribute attribute = new MemoryAttribute(name);
        attribute.setMaxSize(maxSize);
        return attribute;
    }

    @Override
    public Attribute createAttribute(HttpRequest request, String name, long definedSize) {
        if (useDisk) {
            Attribute attribute = new DiskAttribute(name, definedSize, charset, baseDir, deleteOnExit);
            attribute.setMaxSize(maxSize);
            List<HttpData> list = getList(request);
            list.add(attribute);
            return attribute;
        }
        if (checkSize) {
            Attribute attribute = new MixedAttribute(name, definedSize, minSize, charset, baseDir, deleteOnExit);
            attribute.setMaxSize(maxSize);
            List<HttpData> list = getList(request);
            list.add(attribute);
            return attribute;
        }
        MemoryAttribute attribute = new MemoryAttribute(name, definedSize);
        attribute.setMaxSize(maxSize);
        return attribute;
    }

    private static void checkHttpDataSize(HttpData data) {
        try {
            data.checkSize(data.length());
        } catch (IOException ignored) {
            throw new IllegalArgumentException("Attribute bigger than maxSize allowed");
        }
    }

    @Override
    public Attribute createAttribute(HttpRequest request, String name, String value) {
        if (useDisk) {
            Attribute attribute;
            try {
                attribute = new DiskAttribute(name, value, charset, baseDir, deleteOnExit);
                attribute.setMaxSize(maxSize);
            } catch (IOException e) {
                // revert to Mixed mode
                attribute = new MixedAttribute(name, value, minSize, charset, baseDir, deleteOnExit);
                attribute.setMaxSize(maxSize);
            }
            checkHttpDataSize(attribute);
            List<HttpData> list = getList(request);
            list.add(attribute);
            return attribute;
        }
        if (checkSize) {
            Attribute attribute = new MixedAttribute(name, value, minSize, charset, baseDir, deleteOnExit);
            attribute.setMaxSize(maxSize);
            checkHttpDataSize(attribute);
            List<HttpData> list = getList(request);
            list.add(attribute);
            return attribute;
        }
        try {
            MemoryAttribute attribute = new MemoryAttribute(name, value, charset);
            attribute.setMaxSize(maxSize);
            checkHttpDataSize(attribute);
            return attribute;
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public FileUpload createFileUpload(HttpRequest request, String name, String filename,
                                       String contentType, String contentTransferEncoding, Charset charset,
                                       long size) {
        if (useDisk) {
            FileUpload fileUpload = new DiskFileUpload(name, filename, contentType,
                contentTransferEncoding, charset, size, baseDir, deleteOnExit);
            fileUpload.setMaxSize(maxSize);
            checkHttpDataSize(fileUpload);
            List<HttpData> list = getList(request);
            list.add(fileUpload);
            return fileUpload;
        }
        if (checkSize) {
            FileUpload fileUpload = new MixedFileUploadPatched(name, filename, contentType,
                contentTransferEncoding, charset, size, minSize, baseDir, deleteOnExit);
            fileUpload.setMaxSize(maxSize);
            checkHttpDataSize(fileUpload);
            List<HttpData> list = getList(request);
            list.add(fileUpload);
            return fileUpload;
        }
        MemoryFileUpload fileUpload = new MemoryFileUpload(name, filename, contentType,
            contentTransferEncoding, charset, size);
        fileUpload.setMaxSize(maxSize);
        checkHttpDataSize(fileUpload);
        return fileUpload;
    }

    @Override
    public void removeHttpDataFromClean(HttpRequest request, InterfaceHttpData data) {
        if (!(data instanceof HttpData)) {
            return;
        }

        // Do not use getList because it adds empty list to requestFileDeleteMap
        // if request is not found
        List<HttpData> list = requestFileDeleteMap.get(request);
        if (list == null) {
            return;
        }

        // Can't simply call list.remove(data), because different data items may be equal.
        // Need to check identity.
        Iterator<HttpData> i = list.iterator();
        while (i.hasNext()) {
            HttpData n = i.next();
            if (n == data) {
                i.remove();

                // Remove empty list to avoid memory leak
                if (list.isEmpty()) {
                    requestFileDeleteMap.remove(request);
                }

                return;
            }
        }
    }

    @Override
    public void cleanRequestHttpData(HttpRequest request) {
        List<HttpData> list = requestFileDeleteMap.remove(request);
        if (list != null) {
            for (HttpData data : list) {
                data.release();
            }
        }
    }

    @Override
    public void cleanAllHttpData() {
        Iterator<Map.Entry<HttpRequest, List<HttpData>>> i = requestFileDeleteMap.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry<HttpRequest, List<HttpData>> e = i.next();

            // Calling i.remove() here will cause "java.lang.IllegalStateException: Entry was removed"
            // at e.getValue() below

            List<HttpData> list = e.getValue();
            for (HttpData data : list) {
                data.release();
            }

            i.remove();
        }
    }

    @Override
    public void cleanRequestHttpDatas(HttpRequest request) {
        cleanRequestHttpData(request);
    }

    @Override
    public void cleanAllHttpDatas() {
        cleanAllHttpData();
    }
}
