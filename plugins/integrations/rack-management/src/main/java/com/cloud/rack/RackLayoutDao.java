package com.cloud.rack;

import com.cloud.utils.db.GenericDao;

public interface RackLayoutDao extends GenericDao<RackLayoutVO, Long> {
    RackLayoutVO findByZoneAndName(Long zoneId, String name);
}