package com.github.greenfinger.api.pojo;

import java.util.Date;
import org.apache.commons.lang3.time.DurationFormatUtils;
import com.github.greenfinger.CatalogDetails;
import com.github.greenfinger.components.Dashboard;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 
 * @Description: CatalogSummary
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@Getter
@Setter
@ToString
public class CatalogSummary {

    private final CatalogDetails catalogDetails;

    public CatalogSummary(Dashboard dashboard) {
        this.catalogDetails = dashboard.getCatalogDetails();
        this.startTime = new Date(dashboard.getStartTime());
        this.endTime = new Date(dashboard.getEndTime());
        this.completed = dashboard.isCompleted();
        this.totalUrlCount = dashboard.getTotalUrlCount();
        this.existingUrlCount = dashboard.getExistingUrlCount();
        this.filteredUrlCount = dashboard.getFilteredUrlCount();
        this.invalidUrlCount = dashboard.getInvalidUrlCount();
        this.savedResourceCount = dashboard.getSavedResourceCount();
        this.indexedResourceCount = dashboard.getIndexedResourceCount();
        this.elapsedTime =
                DurationFormatUtils.formatDuration(dashboard.getElapsedTime(), "H'H' m'm' s's'");
    }

    private Date startTime;
    private Date endTime;
    private boolean completed;
    private long totalUrlCount;
    private long existingUrlCount;
    private long filteredUrlCount;
    private long invalidUrlCount;
    private long savedResourceCount;
    private long indexedResourceCount;
    private String elapsedTime;

}
