package com.cloud.rack;

import org.springframework.stereotype.Component;

import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

@Component
public class RackLayoutDaoImpl extends GenericDaoBase<RackLayoutVO, Long> implements RackLayoutDao {

    private final SearchBuilder<RackLayoutVO> ZoneNameSearch;

    public RackLayoutDaoImpl() {
        ZoneNameSearch = createSearchBuilder();
        ZoneNameSearch.and("zoneId", ZoneNameSearch.entity().getZoneId(), SearchCriteria.Op.EQ);
        ZoneNameSearch.and("name", ZoneNameSearch.entity().getName(), SearchCriteria.Op.EQ);
        ZoneNameSearch.done();
    }

    @Override
    public RackLayoutVO findByZoneAndName(Long zoneId, String name) {
        SearchCriteria<RackLayoutVO> sc = ZoneNameSearch.create();
        sc.setParameters("zoneId", zoneId);
        sc.setParameters("name", name);
        return findOneBy(sc);
    }
}