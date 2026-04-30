package org.apache.cloudstack.api.response;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;
import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

@SuppressWarnings("unused")
public class RackLayoutResponse extends BaseResponse {

    @SerializedName(ApiConstants.ID)
    @Param(description = "the ID of the rack layout")
    private String id;

    @SerializedName(ApiConstants.ZONE_ID)
    @Param(description = "the ID of the zone for this rack layout")
    private String zoneId;

    @SerializedName(ApiConstants.NAME)
    @Param(description = "the name of the rack layout (e.g., default, room1)")
    private String name;

    // 프론트엔드에서 파싱할 JSON 문자열을 그대로 담아줍니다.
    @SerializedName("content")
    @Param(description = "JSON string containing the actual rack layout configuration")
    private String content;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}