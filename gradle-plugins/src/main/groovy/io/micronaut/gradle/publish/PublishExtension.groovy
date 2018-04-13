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

package io.micronaut.gradle.publish

import org.gradle.util.ConfigureUtil

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class PublishExtension {

    /**
     * The slug from github
     */
    String githubSlug
    /**
     * The the publishing user
     */
    String user
    /**
     * The the publishing key
     */
    String key
    /**
     * The website URL of the plugin
     */
    String websiteUrl
    /**
     * The source control URL of the plugin
     */
    String vcsUrl
    /**
     * The license of the plugin
     */
    License license = new License()

    /**
     * The developers of the plugin
     */
    Map<String, String> developers = [:]

    /**
     * Title of the plugin, defaults to the project name
     */
    String title

    /**
     * Description of the plugin
     */
    String desc
    /**
     * THe organisation on bintray
     */
    String userOrg

    /**
     * THe repository on bintray
     */
    String repo
    /**
     * The issue tracker URL
     */
    String issueTrackerUrl
    /**
     * Whether to GPG sign
     */
    boolean gpgSign = false

    /**
     * The passphrase to sign, only required if `gpgSign == true`
     */
    String signingPassphrase
    /**
     * Whether to sync to Maven central
     */
    boolean mavenCentralSync = false

    /**
     * Username for maven central
     */
    String sonatypeOssUsername

    /**
     * Password for maven central
     */
    String sonatypeOssPassword

    License getLicense() {
        return license
    }

    /**
     * Configures the license
     *
     * @param configurer The configurer
     * @return the license instance
     */
    License license(@DelegatesTo(License) Closure configurer) {
        ConfigureUtil.configure(configurer, license)
    }

    void setLicense(License license) {
        this.license = license
    }

    void setLicense(String license) {
        this.license.name = license
    }

    static class License {
        String name
        String url
        String distribution = 'repo'

        static final License APACHE2 = new License(name:'The Apache Software License, Version 2.0', url: 'http://www.apache.org/licenses/LICENSE-2.0.txt')
        static final License EPL1 = new License(name:'Eclipse Public License - v 1.0', url: 'https://www.eclipse.org/legal/epl-v10.html')
        static final License LGPL21 = new License(name:'GNU Lesser General Public License, version 2.1', url: 'http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html')
        static final License LGPL = new License(name:'GNU Lesser General Public License', url: 'http://www.gnu.org/licenses/lgpl-3.0.html')
        static final License GPL = new License(name:'GNU General Public License', url: 'http://www.gnu.org/licenses/gpl-3.0.en.html')
        static final License CPL = new License(name:"Common Public License Version 1.0 (CPL)", url:"https://opensource.org/licenses/cpl1.0.php")
        static final License AGPL = new License(name:"GNU Affero General Public License", url: "http://www.gnu.org/licenses/agpl-3.0.html")
        static final License MIT = new License(name:"The MIT License (MIT)", url: "https://opensource.org/licenses/MIT")
        static final License BSD = new License(name:"The BSD 3-Clause License", url: "https://opensource.org/licenses/BSD-3-Clause")
        static final Map<String, License> LICENSES = [
                'Apache-2.0':APACHE2,
                'Apache':APACHE2,
                'AGPL':AGPL,
                'AGPL-3.0':AGPL,
                'GPL-3.0':GPL,
                'GPL':GPL,
                'EPL':EPL1,
                'EPL-1.0':EPL1,
                'CPL': CPL,
                'CPL-1.0': CPL,
                'LGPL': LGPL,
                'LGPL-3.0': LGPL,
                'LGPL-2.1': LGPL21,
                'BSD':BSD,
                'BSD 3-Clause':BSD,
                'MIT': MIT
        ]
    }
}
