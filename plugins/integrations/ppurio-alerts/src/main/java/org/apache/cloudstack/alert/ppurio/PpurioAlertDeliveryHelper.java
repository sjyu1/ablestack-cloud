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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.cloud.alert.AlertDeliveryHelper;
import com.cloud.alert.AlertManager;
import com.cloud.utils.component.AdapterBase;
import com.cloud.utils.component.ComponentContext;
import com.google.gson.Gson;

public class PpurioAlertDeliveryHelper extends AdapterBase implements AlertDeliveryHelper, Configurable, ApplicationContextAware {
    private static final Logger logger = LogManager.getLogger(PpurioAlertDeliveryHelper.class);
    private static final Gson gson = new Gson();

    private ApplicationContext applicationContext;

    @Override
    public boolean start() {
        if (applicationContext == null) {
            logger.warn("Unable to register Ppurio AlertDeliveryHelper delegate context because application context is null");
            return true;
        }
        ComponentContext.addDelegateContext(AlertDeliveryHelper.class, applicationContext);
        logger.info("Registered Ppurio AlertDeliveryHelper delegate context");
        return true;
    }

    @Override
    public boolean stop() {
        ComponentContext.removeDelegateContext(AlertDeliveryHelper.class);
        return true;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void sendAlert(AlertManager.AlertType alertType, long dataCenterId, Long podId, Long clusterId, String subject, String body) {
        logger.info(String.format("Received Ppurio Kakao alert delivery request [alertType=%s, dataCenterId=%s, podId=%s, clusterId=%s, subject=%s].",
                alertType, dataCenterId, podId, clusterId, subject));
        if (!PpurioAlertConfigKeys.ALERT_KAKAO_ENABLED.value()) {
            logger.info(String.format("Skipped Ppurio Kakao alert delivery because alert.kakao.ppurio.enabled is false [alertType=%s, dataCenterId=%s, podId=%s, clusterId=%s, subject=%s].",
                    alertType, dataCenterId, podId, clusterId, subject));
            return;
        }

        List<String> recipients = parseRecipients(PpurioAlertConfigKeys.RECIPIENTS.value());
        if (recipients.isEmpty()) {
            logger.warn("Ppurio Alert integration is enabled but no recipients are configured in alert.kakao.ppurio.recipients");
            return;
        }

        String apiUrl = PpurioAlertConfigKeys.API_URL.value();
        String account = PpurioAlertConfigKeys.ACCOUNT.value();
        String authKey = PpurioAlertConfigKeys.AUTH_KEY.value();
        String senderKey = PpurioAlertConfigKeys.SENDER_KEY.value();
        String templateCode = PpurioAlertConfigKeys.TEMPLATE_CODE.value();
        String senderNumber = PpurioAlertConfigKeys.SENDER_NUMBER.value();

        if (StringUtils.isAnyBlank(apiUrl, account, authKey, senderKey, templateCode, senderNumber)) {
            logger.warn("Ppurio Alert integration is enabled but one or more required settings are blank");
            return;
        }

        String message = renderMessage(alertType, subject);
        for (String recipient : recipients) {
            try {
                logPreparedMessage(apiUrl, account, senderKey, templateCode, senderNumber, recipient, message);
                sendMessage(apiUrl, account, authKey, senderKey, templateCode, senderNumber, recipient, message);
            } catch (IOException e) {
                logger.warn(String.format("Failed to send Ppurio Kakao alert to recipient [%s] for subject [%s]", recipient, subject), e);
            }
        }
    }

    protected void sendMessage(String apiUrl, String account, String authKey, String senderKey, String templateCode,
            String senderNumber, String recipient, String message) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(PpurioAlertConfigKeys.CONNECT_TIMEOUT_MS.value());
            connection.setReadTimeout(PpurioAlertConfigKeys.READ_TIMEOUT_MS.value());
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Authorization", buildAuthorizationHeader(account, authKey));

            byte[] payloadBytes = gson.toJson(buildRequestBody(account, senderKey, templateCode, senderNumber, recipient, message))
                    .getBytes(StandardCharsets.UTF_8);

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(payloadBytes);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                String responseBody = readResponse(connection);
                throw new IOException(String.format("Unexpected Ppurio response code [%s], response [%s]", responseCode, responseBody));
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    protected Map<String, Object> buildRequestBody(String account, String senderKey, String templateCode,
            String senderNumber, String recipient, String message) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("account", account);
        root.put("refkey", UUID.randomUUID().toString());
        root.put("type", "at");
        root.put("from", senderNumber);

        List<Map<String, String>> targets = new ArrayList<>();
        Map<String, String> target = new LinkedHashMap<>();
        target.put("to", recipient);
        target.put("message", message);
        targets.add(target);
        root.put("targets", targets);

        Map<String, Object> content = new LinkedHashMap<>();
        Map<String, String> kakao = new LinkedHashMap<>();
        kakao.put("senderKey", senderKey);
        kakao.put("templateCode", templateCode);
        kakao.put("message", message);
        content.put("kakao", kakao);
        root.put("content", content);
        return root;
    }

    protected String renderMessage(AlertManager.AlertType alertType, String subject) {
        String template = PpurioAlertConfigKeys.MESSAGE_TEMPLATE.value();
        return template
                .replace("${alertType}", safeValue(alertType == null ? null : alertType.getName()))
                .replace("${subject}", safeValue(subject));
    }

    protected String buildAuthorizationHeader(String account, String authKey) {
        String token = account + ":" + authKey;
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    protected List<String> parseRecipients(String recipientsConfig) {
        List<String> recipients = new ArrayList<>();
        if (StringUtils.isBlank(recipientsConfig)) {
            return recipients;
        }
        for (String recipient : recipientsConfig.split(",")) {
            String trimmed = StringUtils.trim(recipient);
            if (StringUtils.isNotBlank(trimmed)) {
                recipients.add(trimmed);
            }
        }
        return recipients;
    }

    protected String readResponse(HttpURLConnection connection) throws IOException {
        InputStream stream = connection.getErrorStream() != null ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            return "";
        }
        try (InputStream inputStream = stream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    protected String safeValue(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    protected void logPreparedMessage(String apiUrl, String account, String senderKey, String templateCode,
            String senderNumber, String recipient, String message) {
        logger.info(String.format(
                "Prepared Ppurio Kakao alert request [apiUrl=%s, account=%s, senderKey=%s, templateCode=%s, senderNumber=%s, recipient=%s, message=%s]",
                apiUrl,
                maskValue(account),
                maskValue(senderKey),
                templateCode,
                senderNumber,
                recipient,
                message.replace(System.lineSeparator(), "\\n")));
    }

    protected String maskValue(String value) {
        if (StringUtils.isBlank(value)) {
            return "-";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    @Override
    public String getConfigComponentName() {
        return AlertDeliveryHelper.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {
                PpurioAlertConfigKeys.ALERT_KAKAO_ENABLED,
                PpurioAlertConfigKeys.API_URL,
                PpurioAlertConfigKeys.ACCOUNT,
                PpurioAlertConfigKeys.AUTH_KEY,
                PpurioAlertConfigKeys.SENDER_KEY,
                PpurioAlertConfigKeys.TEMPLATE_CODE,
                PpurioAlertConfigKeys.SENDER_NUMBER,
                PpurioAlertConfigKeys.RECIPIENTS,
                PpurioAlertConfigKeys.MESSAGE_TEMPLATE,
                PpurioAlertConfigKeys.CONNECT_TIMEOUT_MS,
                PpurioAlertConfigKeys.READ_TIMEOUT_MS
        };
    }
}
