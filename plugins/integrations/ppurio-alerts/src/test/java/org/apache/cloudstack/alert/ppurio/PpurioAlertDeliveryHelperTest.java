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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.cloud.alert.AlertManager;

public class PpurioAlertDeliveryHelperTest {
    private final PpurioAlertDeliveryHelper helper = new PpurioAlertDeliveryHelper();

    @Test
    public void parseRecipientsTrimsConfiguredNumbersAndSkipsBlanks() {
        List<String> recipients = helper.parseRecipients(" 01011112222, ,01033334444,, 01055556666 ");

        assertEquals(3, recipients.size());
        assertEquals("01011112222", recipients.get(0));
        assertEquals("01033334444", recipients.get(1));
        assertEquals("01055556666", recipients.get(2));
    }

    @Test
    public void renderMessageUsesDefaultTemplateAndSafeValues() {
        String message = helper.renderMessage(AlertManager.AlertType.ALERT_TYPE_CPU, "subject");

        assertTrue(message.contains("[MOLD 경보 메시지]"));
        assertTrue(message.contains("타입: ALERT.CPU"));
        assertTrue(message.contains("내용: subject"));
        assertTrue(message.contains("해당 알림을 ABLESTACK MOLD 서비스에서 발송한 경보입니다."));
    }

    @Test
    public void buildAuthorizationHeaderUsesBasicAccountAndAuthKeyToken() {
        String header = helper.buildAuthorizationHeader("account", "authKey");

        String expectedToken = Base64.getEncoder().encodeToString("account:authKey".getBytes(StandardCharsets.UTF_8));
        assertEquals("Basic " + expectedToken, header);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void buildRequestBodyIncludesKakaoContentAndTarget() {
        Map<String, Object> request = helper.buildRequestBody("account", "senderKey", "templateCode", "01011112222", "01033334444", "message");

        assertEquals("account", request.get("account"));
        assertNotNull(request.get("refkey"));
        assertEquals("at", request.get("type"));
        assertEquals("01011112222", request.get("from"));

        List<Map<String, String>> targets = (List<Map<String, String>>)request.get("targets");
        assertEquals(1, targets.size());
        assertEquals("01033334444", targets.get(0).get("to"));
        assertEquals("message", targets.get(0).get("message"));

        Map<String, Object> content = (Map<String, Object>)request.get("content");
        Map<String, String> kakao = (Map<String, String>)content.get("kakao");
        assertEquals("senderKey", kakao.get("senderKey"));
        assertEquals("templateCode", kakao.get("templateCode"));
        assertEquals("message", kakao.get("message"));
    }
}
