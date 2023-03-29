package io.micronaut.http.server.netty.body;

import io.netty.util.ReferenceCountUtil;

public final class ImmediateSingleObjectBody extends ManagedBody<Object> implements SingleObjectBody {
    public ImmediateSingleObjectBody(Object value) {
        super(value);
    }

    @Override
    void release(Object value) {
        ReferenceCountUtil.release(value);
    }

    public Object claimForExternal() {
        return claim();
    }

    public Object valueUnclaimed() {
        return value();
    }
}
