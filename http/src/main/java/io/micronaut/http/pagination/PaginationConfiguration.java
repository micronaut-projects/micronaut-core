/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.http.pagination;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Allows configuring the {@link io.micronaut.http.bind.binders.PageableArgumentBinder}.
 *
 * @author boros
 * @since 1.0.1
 */
@ConfigurationProperties(PaginationConfiguration.PREFIX)
public class PaginationConfiguration {

    public static final String PREFIX = "micronaut.pagination";

    private PaginationSizeConfiguration size = new PaginationSizeConfiguration();
    private PaginationPageConfiguration page = new PaginationPageConfiguration();

    /**
     * @return the page size configuration
     */
    public PaginationSizeConfiguration getSizeConfiguration() {
        return size;
    }

    /**
     * @return the page number configuration
     */
    public PaginationPageConfiguration getPageConfiguration() {
        return page;
    }

    /**
     * @param pageConfiguration the page number configuration
     */
    public void setPage(PaginationPageConfiguration pageConfiguration) {
        if (pageConfiguration != null) {
            this.page = pageConfiguration;
        }
    }

    /**
     * @param sizeConfiguration the page size configuration
     */
    public void setSize(PaginationSizeConfiguration sizeConfiguration) {
        if (sizeConfiguration != null) {

            if (sizeConfiguration.getDefault() < sizeConfiguration.getMin()) {
                throw new IllegalArgumentException("Limit default value cannot be less then minimal value");
            }

            if (sizeConfiguration.getDefault() > sizeConfiguration.getMax()) {
                throw new IllegalArgumentException("Limit default value cannot be more then maximal value");
            }

            this.size = sizeConfiguration;
        }
    }

    /**
     * Page size configuration.
     */
    @ConfigurationProperties("size")
    public static class PaginationSizeConfiguration {

        private static final String DEFAULT_NAME = "size";

        private static final int DEFAULT_MAX = 100;
        private static final int DEFAULT_MIN = 10;
        private static final int DEFAULT = DEFAULT_MIN;

        private String name = DEFAULT_NAME;
        private int max = DEFAULT_MAX;
        private int min = DEFAULT_MIN;
        private int defaultSize = DEFAULT;

        /**
         * @return the name of page size request parameter
         */
        public String getName() {
            return name;
        }

        /**
         * @return the default page size value, if request parameter is not present
         */
        public int getDefault() {
            return defaultSize;
        }

        /**
         * @param defaultSize the defaultSize to set
         */
        public void setDefault(int defaultSize) {
            this.defaultSize = defaultSize;
        }

        /**
         * @return the minimum value for page size
         */
        public int getMin() {
            return min;
        }

        /**
         * @param min the min to set
         */
        public void setMin(int min) {
            this.min = min;
        }

        /**
         * @return the maximum value for page size
         */
        public int getMax() {
            return max;
        }

        /**
         * @param max the max to set
         */
        public void setMax(int max) {
            this.max = max;
        }

        /**
         * @param name the name to set
         */
        public void setName(String name) {
            this.name = name;
        }

    }

    /**
     * Page number configuration.
     */
    @ConfigurationProperties("page")
    public static class PaginationPageConfiguration {

        private static final String DEFAULT_NAME = "page";
        private static final int DEFAULT_VALUE = 0;

        private String name = DEFAULT_NAME;

        /**
         * @return the name of the page number request parameter
         */
        public String getName() {
            return name;
        }

        /**
         * @param name the name to set
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * @return default page number
         */
        public int getDefault() {
            return DEFAULT_VALUE;
        }

    }

}
