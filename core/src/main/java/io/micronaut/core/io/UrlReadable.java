/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.core.io;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.StringUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

/**
 * Implementation of {@link Readable} for {@link URL}.
 *
 * @author graemerocher
 * @since 1.1.0
 */
@Internal
class UrlReadable implements Readable {

    private final URL url;

    /**
     * Default constructor.
     * @param url The URL
     */
    UrlReadable(URL url) {
        ArgumentUtils.requireNonNull("url", url);
        this.url = url;
    }

    @NonNull
    @Override
    public InputStream asInputStream() throws IOException {
        URLConnection con = this.url.openConnection();
        con.setUseCaches(true);
        try {
            return con.getInputStream();
        } catch (IOException ex) {
            if (con instanceof HttpURLConnection) {
                ((HttpURLConnection) con).disconnect();
            }
            throw ex;
        }
    }

    @Override
    public boolean exists() {
        try {
            final String protocol = url.getProtocol();
            if (StringUtils.isNotEmpty(protocol) && ("file".equalsIgnoreCase(protocol) || protocol.startsWith("vfs"))) {
                try {
                    return new File(url.toURI().getSchemeSpecificPart()).exists();
                } catch (URISyntaxException ex) {
                    return new File(url.getFile()).exists();
                }
            }
            URLConnection con = url.openConnection();
            con.setUseCaches(true);
            final boolean isHttp = con instanceof HttpURLConnection;
            if (isHttp) {
                final HttpURLConnection httpURLConnection = (HttpURLConnection) con;
                httpURLConnection.setRequestMethod("HEAD");
                int code = httpURLConnection.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    return true;
                }
                if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                    return false;
                }
            }
            if (con.getContentLengthLong() >= 0) {
                return true;
            }
            if (isHttp) {
                // No HTTP OK status, and no content-length header: give up
                ((HttpURLConnection) con).disconnect();
                return false;
            }
            // Fall back to stream existence: can we open the stream?
            asInputStream().close();
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    @Override
    public String getName() {
        return url.getPath();
    }
}
