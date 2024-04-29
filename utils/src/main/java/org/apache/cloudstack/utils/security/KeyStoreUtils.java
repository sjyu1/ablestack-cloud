//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

package org.apache.cloudstack.utils.security;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.cloud.utils.PropertiesUtil;
import com.cloud.utils.db.DbProperties;
import com.cloud.utils.server.ServerProperties;

public class KeyStoreUtils {

    protected static Logger LOG = LogManager.getLogger(KeyStoreUtils.class);

    public static final String KS_SETUP_SCRIPT = "keystore-setup";
    public static final String KS_IMPORT_SCRIPT = "keystore-cert-import";
    public static final String KS_SYSTEMVM_IMPORT_SCRIPT = "keystore-cert-import-sysvm";

    public static final String AGENT_PROPSFILE = "agent.properties";
    public static final String KS_PASSPHRASE_PROPERTY = "keystore.passphrase";

    public static final String KS_FILENAME = "cloud.jks";
    // public static final char[] DEFAULT_KS_PASSPHRASE = "vmops.com".toCharArray();

    public static final String CACERT_FILENAME = "cloud.ca.crt";
    public static final String CERT_FILENAME = "cloud.crt";
    public static final String CSR_FILENAME = "cloud.csr";
    public static final String PKEY_FILENAME = "cloud.key";

    public static final String CERT_NEWLINE_ENCODER = "^";
    public static final String CERT_SPACE_ENCODER = "~";

    public static final String SSH_MODE = "ssh";
    public static final String AGENT_MODE = "agent";
    public static final String SECURED = "secured";

    public static boolean isHostSecured() {
        final File confFile = PropertiesUtil.findConfigFile("agent.properties");
        return confFile != null && confFile.exists() && new File(confFile.getParent() + "/" + CERT_FILENAME).exists();
    }

    public static char[] DEFAULT_KS_PASSPHRASE() {
        final File serverPropsFileEnc = PropertiesUtil.findConfigFile("server.properties.enc");
        final File serverPropsFile = PropertiesUtil.findConfigFile("server.properties");
        final String key = DbProperties.getKey();
        char[] ks_pass = null;
        InputStream is = null;
        try {
            if (serverPropsFileEnc == null) {
                LOG.info(":::::::::::::::::::::1:::::::::::::::::::::::::");
                is = new FileInputStream(serverPropsFile);
            } else {
                LOG.info(":::::::::::::::::::::2:::::::::::::::::::::::::");
                Process process = Runtime.getRuntime().exec("openssl enc -aes-256-cbc -d -K " + DbProperties.getKey() + " -pass pass:" + DbProperties.getKp() + " -saltlen 16 -md sha256 -iter 100000 -in " + serverPropsFileEnc.getAbsoluteFile());
                is = process.getInputStream();
                process.onExit();
            }
            final Properties properties = ServerProperties.getServerProperties(is);
            String keystorePassword = properties.getProperty("https.keystore.password");
            LOG.info(":::::::::::::::::::::3:::::::::::::::::::::::::");
            LOG.info(keystorePassword);
            ks_pass = keystorePassword.toCharArray();
            return ks_pass;
        } catch (final IOException e) {
            LOG.error("Failed to read configuration from server.properties file", e);
        }
        return "vmops.com".toCharArray();
    }
}
