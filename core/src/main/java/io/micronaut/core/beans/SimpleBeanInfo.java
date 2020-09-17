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
package io.micronaut.core.beans;

import static io.micronaut.core.naming.NameUtils.decapitalize;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Note: Based on code found in Apache Harmony.
 *
 * @author graemerocher
 * @since 1.0
 * @deprecated Replaced by {@link BeanIntrospection}
 */
@Internal
@Deprecated
class SimpleBeanInfo implements BeanInfo {
    private static final Set<String> EXCLUDED_PROPERTIES = CollectionUtils.setOf("class", "metaClass");

    // Prefixes for methods that set or get a Property
    private static final String PREFIX_IS = "is"; //$NON-NLS-1$
    private static final String PREFIX_GET = "get"; //$NON-NLS-1$
    private static final String PREFIX_SET = "set"; //$NON-NLS-1$

    private static final String STR_GETTERS = "getters"; //$NON-NLS-1$
    private static final String STR_SETTERS = "setters"; //$NON-NLS-1$
    private static final String STR_NORMAL = "normal"; //$NON-NLS-1$
    private static final String STR_VALID = "valid"; //$NON-NLS-1$
    private static final String STR_PROPERTY_TYPE = "PropertyType"; //$NON-NLS-1$
    private static final String STR_INVALID = "invalid"; //$NON-NLS-1$

    private final Class<?> beanClass;
    private final Map<String, PropertyDescriptor> properties;

    /**
     * Constructor.
     * @param beanClass beanClass
     */
    SimpleBeanInfo(Class<?> beanClass) {
        this.beanClass = beanClass;
        List<PropertyDescriptor> propertyList = introspectProperties(introspectMethods(beanClass));
        if (CollectionUtils.isEmpty(propertyList)) {
            this.properties = Collections.emptyMap();
        } else {
            HashMap<String, PropertyDescriptor> propertyMap = new HashMap<>(propertyList.size());
            for (PropertyDescriptor propertyDescriptor : propertyList) {
                propertyMap.put(propertyDescriptor.getName(), propertyDescriptor);
            }
            this.properties = Collections.unmodifiableMap(propertyMap);
        }
    }

    @Override
    public Class<?> getBeanClass() {
        return beanClass;
    }

    @Override
    public Map<String, PropertyDescriptor> getPropertyDescriptors() {
        return properties;
    }

    /**
     * Introspects the supplied class and returns a list of the Properties of
     * the class.
     *
     * @param methodDescriptors the method descriptors
     * @return The list of Properties as an array of PropertyDescriptors
     */
    @SuppressWarnings("unchecked")
    private List<PropertyDescriptor> introspectProperties(Method[] methodDescriptors) {

        // Get the list of public non-static methods into an array
        if (methodDescriptors == null) {
            return null;
        }

        HashMap<String, HashMap> propertyTable = new HashMap<>(methodDescriptors.length);

        // Search for methods that either get or set a Property
        for (Method methodDescriptor : methodDescriptors) {
            introspectGet(methodDescriptor, propertyTable);
            introspectSet(methodDescriptor, propertyTable);
        }

        // fix possible getter & setter collisions
        fixGetSet(propertyTable);

        // Put the properties found into the PropertyDescriptor array
        ArrayList<PropertyDescriptor> propertyList = new ArrayList<>();

        for (Map.Entry<String, HashMap> entry : propertyTable.entrySet()) {
            String propertyName = entry.getKey();
            HashMap table = entry.getValue();
            if (table == null) {
                continue;
            }
            String normalTag = (String) table.get(STR_NORMAL);

            if ((normalTag == null)) {
                continue;
            }

            Method get = (Method) table.get(STR_NORMAL + PREFIX_GET);
            Method set = (Method) table.get(STR_NORMAL + PREFIX_SET);

            PropertyDescriptor propertyDesc = new PropertyDescriptor(propertyName, get, set);
            propertyList.add(propertyDesc);
        }

        return Collections.unmodifiableList(propertyList);
    }

    @SuppressWarnings("unchecked")
    private static void introspectGet(Method theMethod,
                                      HashMap<String, HashMap> propertyTable) {

        String methodName = theMethod.getName();
        int prefixLength = 0;
        String propertyName;
        Class propertyType;
        Class[] paramTypes;
        HashMap table;
        ArrayList<Method> getters;

        if (methodName == null) {
            return;
        }

        if (methodName.startsWith(PREFIX_GET)) {
            prefixLength = PREFIX_GET.length();
        }

        if (methodName.startsWith(PREFIX_IS)) {
            prefixLength = PREFIX_IS.length();
        }

        if (prefixLength == 0) {
            return;
        }

        propertyName = decapitalize(methodName.substring(prefixLength));

        // validate property name
        if (isInvalidProperty(propertyName)) {
            return;
        }

        // validate return type
        propertyType = theMethod.getReturnType();

        if (propertyType == null || propertyType == void.class) {
            return;
        }

        // isXXX return boolean
        if (prefixLength == 2 && propertyType != boolean.class) {
            return;
        }

        // validate parameter types
        paramTypes = theMethod.getParameterTypes();
        if (paramTypes.length > 1 || (paramTypes.length == 1 && paramTypes[0] != int.class)) {
            return;
        }

        table = propertyTable.computeIfAbsent(propertyName, k -> new HashMap());

        getters = (ArrayList<Method>) table.get(STR_GETTERS);
        if (getters == null) {
            getters = new ArrayList<>();
            table.put(STR_GETTERS, getters);
        }

        // add current method as a valid getter
        getters.add(theMethod);
    }

    @SuppressWarnings("unchecked")
    private static void introspectSet(Method theMethod,
                                      HashMap<String, HashMap> propertyTable) {

        String methodName = theMethod.getName();
        if (methodName == null) {
            return;
        }
        String propertyName;
        Class returnType;
        Class[] paramTypes;

        // setter method should never return type other than void
        returnType = theMethod.getReturnType();
        if (returnType != void.class) {
            return;
        }

        if (!methodName.startsWith(PREFIX_SET)) {
            return;
        }

        propertyName = decapitalize(methodName.substring(PREFIX_SET.length()));

        // validate property name
        if (isInvalidProperty(propertyName)) {
            return;
        }

        // It seems we do not need to validate return type

        // validate param types
        paramTypes = theMethod.getParameterTypes();

        if (paramTypes.length == 0 || paramTypes.length > 1) {
            return;
        }

        HashMap table = propertyTable.computeIfAbsent(propertyName, k -> new HashMap());

        ArrayList<Method> setters = (ArrayList<Method>) table.computeIfAbsent(STR_SETTERS, k -> new ArrayList<Method>());

        // add new setter
        setters.add(theMethod);
    }

    private static Method[] introspectMethods(Class<?> beanClass) {

        // Get the list of methods belonging to this class
        Method[] basicMethods = beanClass.getMethods();

        if (ArrayUtils.isEmpty(basicMethods)) {
            return null;
        }

        ArrayList<Method> methodList = new ArrayList<>(basicMethods.length);

        // Loop over the methods found, looking for public non-static methods
        for (Method basicMethod : basicMethods) {
            if (basicMethod.getDeclaringClass() == Object.class) {
                break;
            }
            int modifiers = basicMethod.getModifiers();
            if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers) && !basicMethod.isBridge() && !basicMethod.isSynthetic() && basicMethod.getName().indexOf('$') == -1) {
                methodList.add(basicMethod);
            }
        }

        // Get the list of public methods into the returned array
        int methodCount = methodList.size();
        Method[] theMethods = null;
        if (methodCount > 0) {
            theMethods = new Method[methodCount];
            theMethods = methodList.toArray(theMethods);
        }

        return theMethods;
    }

    /**
     * Checks and fixes all cases when several incompatible checkers / getters
     * were specified for single property.
     */
    @SuppressWarnings("unchecked")
    private void fixGetSet(HashMap<String, HashMap> propertyTable) {

        if (propertyTable == null) {
            return;
        }

        for (Map.Entry<String, HashMap> entry : propertyTable.entrySet()) {
            HashMap<String, Object> table = entry.getValue();
            ArrayList<Method> getters = (ArrayList<Method>) table.get(STR_GETTERS);
            ArrayList<Method> setters = (ArrayList<Method>) table.get(STR_SETTERS);

            Method normalGetter = null;
            Method normalSetter = null;

            Class<?> normalPropType = null;

            if (getters == null) {
                getters = new ArrayList<>();
            }

            if (setters == null) {
                setters = new ArrayList<>();
            }

            // retrieve getters
            Class<?>[] paramTypes;
            String methodName;
            for (Method getter : getters) {
                paramTypes = getter.getParameterTypes();
                methodName = getter.getName();
                // checks if it's a normal getter
                if (paramTypes == null || paramTypes.length == 0) {
                    // normal getter found
                    if (normalGetter == null || methodName.startsWith(PREFIX_IS)) {
                        normalGetter = getter;
                    }
                }
            }

            // retrieve normal setter
            if (normalGetter != null) {
                // Now we will try to look for normal setter of the same type.
                Class<?> propertyType = normalGetter.getReturnType();

                for (Method setter : setters) {
                    if (setter.getParameterTypes().length == 1
                        && propertyType.equals(setter.getParameterTypes()[0])) {
                        normalSetter = setter;
                        break;
                    }
                }
            } else {
                // Normal getter wasn't defined. Let's look for the last
                // defined setter

                for (Method setter : setters) {
                    if (setter.getParameterTypes().length == 1) {
                        normalSetter = setter;
                    }
                }
            }

            // determine property type
            if (normalGetter != null) {
                normalPropType = normalGetter.getReturnType();
            } else if (normalSetter != null) {
                normalPropType = normalSetter.getParameterTypes()[0];
            }

            // RULES
            // These rules were created after performing extensive black-box
            // testing of RI

            // RULE1
            // Both normal getter and setter of the same type were defined;
            // no indexed getter/setter *PAIR* of the other type defined
            if (normalGetter != null && normalSetter != null) {
                table.put(STR_NORMAL, STR_VALID);
                table.put(STR_NORMAL + PREFIX_GET, normalGetter);
                table.put(STR_NORMAL + PREFIX_SET, normalSetter);
                table.put(STR_NORMAL + STR_PROPERTY_TYPE, normalPropType);
                continue;
            }

            // RULE2
            // normal getter and/or setter was defined; no indexed
            // getters & setters defined
            if ((normalGetter != null || normalSetter != null)) {
                table.put(STR_NORMAL, STR_VALID);
                table.put(STR_NORMAL + PREFIX_GET, normalGetter);
                table.put(STR_NORMAL + PREFIX_SET, normalSetter);
                table.put(STR_NORMAL + STR_PROPERTY_TYPE, normalPropType);
                continue;
            }

            // default rule - invalid property
            table.put(STR_NORMAL, STR_INVALID);
        }
    }

    private static boolean isInvalidProperty(String propertyName) {
        return (propertyName == null) || (propertyName.length() == 0) || EXCLUDED_PROPERTIES.contains(propertyName);
    }
}
