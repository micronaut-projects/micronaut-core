/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.session;

import org.particleframework.core.convert.value.MutableConvertibleValues;

import java.time.Duration;
import java.time.Instant;

/**
 * <p>An interface representing a user session.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Session extends MutableConvertibleValues<Object> {

    /**
     *
     * Returns the time when this session was created
     *
     * @return	An {@link Instant} instance
     *
     * @exception IllegalStateException	if this method is called on an
     *					invalidated session
     */
    Instant getCreationTime();


    /**
     * A unique identifier for the session
     *
     * @return	The id of the session
     */
    String getId();


    /**
     *
     * Returns the last time the client sent a request associated with
     * this session as an {@link Instant}
     *
     * <p>Actions that your application takes, such as getting or setting
     * a value associated with the session, do not affect the access
     * time.
     *
     * @return				a <code>long</code>
     *					representing the last time
     *					the client sent a request associated
     *					with this session, expressed in
     *					milliseconds since 1/1/1970 GMT
     *
     * @exception IllegalStateException	if this method is called on an
     *					invalidated session
     */
    Instant getLastAccessedTime();



    /**
     * Specifies the duration between client requests before session should be invalidated.
     *
     * @param duration	A duration specifying the max inactive interval
     */
    void setMaxInactiveInterval(Duration duration);


    /**
     * Returns the maximum time interval as a {@link Duration} that
     * sessions will  be kept open between client accesses. After this interval, the servlet container
     * will invalidate the session.  The maximum time interval can be set
     * with the <code>setMaxInactiveInterval</code> method.
     *
     *
     * @return		A duration specifying the time should session should remain open
     *			between client requests
     *
     * @see		#setMaxInactiveInterval
     */
    Duration getMaxInactiveInterval();

    /**
     * @return Whether the session has expired
     */
    boolean isExpired();
}
