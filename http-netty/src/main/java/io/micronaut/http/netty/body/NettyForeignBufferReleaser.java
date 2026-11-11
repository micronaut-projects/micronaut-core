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
package io.micronaut.http.netty.body;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.body.stream.ForeignBufferReleaser;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import jakarta.inject.Singleton;

/**
 * Releases a Netty buffer that a Reactor input of a chunked reader discards, e.g. one it held
 * before it was mapped to a piece: the readers that do not depend on Netty, e.g. the JSON
 * readers of json-core, receive this bean.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
@BootstrapContextCompatible
public final class NettyForeignBufferReleaser implements ForeignBufferReleaser {

    /**
     * The instance, for the readers that are created without a context.
     */
    public static final NettyForeignBufferReleaser INSTANCE = new NettyForeignBufferReleaser();

    @Override
    public void release(Object object) {
        if (object instanceof ReferenceCounted counted && counted.refCnt() > 0) {
            ReferenceCountUtil.safeRelease(counted);
        }
    }
}
