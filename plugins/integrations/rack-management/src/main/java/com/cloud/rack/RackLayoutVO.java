package com.cloud.rack;

import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.apache.cloudstack.api.InternalIdentity;

@Entity
@Table(name = "rackml_config")
public class RackLayoutVO implements InternalIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "zone_id")
    private Long zoneId;

    @Column(name = "name")
    private String name;

    @Column(name = "content", length = 65535)
    private String content;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", updatable = false)
    private Date updatedAt;

    public RackLayoutVO() {
    }

    public RackLayoutVO(Long zoneId, String name, String content) {
        this.zoneId = zoneId;
        this.name = name;
        this.content = content;
    }

    @Override
    public long getId() { return id; }
    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long zoneId) { this.zoneId = zoneId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Date getUpdatedAt() { return updatedAt; }
}