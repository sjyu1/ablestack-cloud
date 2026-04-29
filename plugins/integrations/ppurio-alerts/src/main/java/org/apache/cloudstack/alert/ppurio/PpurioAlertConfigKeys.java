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
package org.apache.cloudstack.alert.ppurio;

import org.apache.cloudstack.framework.config.ConfigKey;

public class PpurioAlertConfigKeys {
    public static final ConfigKey<Boolean> ALERT_KAKAO_ENABLED = new ConfigKey<>(ConfigKey.CATEGORY_ALERT, Boolean.class,
            "alert.kakao.ppurio.enabled", "false",
            "Enable Kakao AlimTalk delivery for Alerts through the Ppurio Biz integration module.", true);

    public static final ConfigKey<String> API_URL = new ConfigKey<>(ConfigKey.CATEGORY_ALERT, String.class,
            "alert.kakao.ppurio.apiUrl", "https://message.ppurio.com/v1/kakao",
            "Ppurio Biz Kakao AlimTalk API URL.", true);

    public static final ConfigKey<String> ACCOUNT = new ConfigKey<>(ConfigKey.CATEGORY_ALERT, String.class,
            "alert.kakao.ppurio.account", "",
            "Ppurio Biz account identifier used to build the authorization header.", true);

    public static final ConfigKey<String> AUTH_KEY = new ConfigKey<>("Secure", String.class,
            "alert.kakao.ppurio.authKey", "",
            "Ppurio Biz authentication key used to build the authorization header.", true);

    public static final ConfigKey<String> SENDER_KEY = new ConfigKey<>(ConfigKey.CATEGORY_ALERT, String.class,
            "alert.kakao.ppurio.senderKey", "",
            "Ppurio Biz Kakao sender profile key.", true);

    public static final ConfigKey<String> TEMPLATE_CODE = new ConfigKey<>(ConfigKey.CATEGORY_ALERT, String.class,
            "alert.kakao.ppurio.templateCode", "",
            "Ppurio Biz Kakao AlimTalk template code.", true);

    public static final ConfigKey<String> SENDER_NUMBER = new ConfigKey<>(ConfigKey.CATEGORY_ALERT, String.class,
            "alert.kakao.ppurio.senderNumber", "",
            "Sender phone number registered in Ppurio Biz.", true);

    public static final ConfigKey<String> RECIPIENTS = new ConfigKey<>(ConfigKey.CATEGORY_ALERT, String.class,
            "alert.kakao.ppurio.recipients", "",
            "Comma-separated recipient phone numbers for Alert Kakao AlimTalk delivery.", true);

    public static final ConfigKey<String> MESSAGE_TEMPLATE = new ConfigKey<>(ConfigKey.CATEGORY_ALERT, String.class,
            "alert.kakao.ppurio.messageTemplate",
            "[MOLD 경보 메시지]\n타입: ${alertType}\n내용: ${subject}\n※ 해당 알림을 ABLESTACK MOLD 서비스에서 발송한 경보입니다.",
            "Kakao AlimTalk message template. Supported placeholders: ${alertType}, ${subject}", true);

    public static final ConfigKey<Integer> CONNECT_TIMEOUT_MS = new ConfigKey<>(ConfigKey.CATEGORY_ALERT, Integer.class,
            "alert.kakao.ppurio.connectTimeoutMs", "5000",
            "Connection timeout in milliseconds for the Ppurio Biz API.", true);

    public static final ConfigKey<Integer> READ_TIMEOUT_MS = new ConfigKey<>(ConfigKey.CATEGORY_ALERT, Integer.class,
            "alert.kakao.ppurio.readTimeoutMs", "10000",
            "Read timeout in milliseconds for the Ppurio Biz API.", true);

    private PpurioAlertConfigKeys() {
    }
}
