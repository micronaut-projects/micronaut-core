/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.particleframework.core.beans;

class BeansUtils {

    public static final String NEW = "new"; //$NON-NLS-1$

    public static final String NEWINSTANCE = "newInstance"; //$NON-NLS-1$

    public static final String NEWARRAY = "newArray"; //$NON-NLS-1$

    public static final String FORNAME = "forName"; //$NON-NLS-1$

    public static final String GET = "get"; //$NON-NLS-1$

    public static final String IS = "is"; //$NON-NLS-1$

    public static final String SET = "set"; //$NON-NLS-1$

    public static final String ADD = "add"; //$NON-NLS-1$

    public static final String PUT = "put"; //$NON-NLS-1$

    public static final String NULL = "null"; //$NON-NLS-1$

    public static final String QUOTE = "\"\""; //$NON-NLS-1$

    static int getHashCode(Object obj) {
        return obj != null ? obj.hashCode() : 0;
    }

    static int getHashCode(boolean bool) {
        return bool ? 1 : 0;
    }

    static String toASCIIUpperCase(String string) {
        char[] charArray = string.toCharArray();
        StringBuilder sb = new StringBuilder(charArray.length);
        for (int index = 0; index < charArray.length; index++) {
            if ('a' <= charArray[index] && charArray[index] <= 'z') {
                sb.append((char) (charArray[index] - ('a' - 'A')));
            } else {
                sb.append(charArray[index]);
            }
        }
        return sb.toString();
    }


}
