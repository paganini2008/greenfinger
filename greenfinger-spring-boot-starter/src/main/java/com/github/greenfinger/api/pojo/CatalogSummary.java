package com.github.greenfinger.api.pojo;

import java.util.Date;
import org.apache.commons.lang3.time.DurationFormatUtils;
import com.github.greenfinger.components.Dashboard;
import com.github.greenfinger.model.Catalog;
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

    private final Catalog catalog;

    public CatalogSummary(Catalog catalog, Dashboard dashboardData) {
        this.catalog = catalog;
        this.startTime = new Date(dashboardData.getStartTime());
        this.completed = dashboardData.isCompleted();
        this.urlCount = dashboardData.getTotalUrlCount();
        this.existedUrlCount = dashboardData.getExistingUrlCount();
        this.filteredUrlCount = dashboardData.getFilteredUrlCount();
        this.invalidUrlCount = dashboardData.getInvalidUrlCount();
        this.savedCount = dashboardData.getSavedResourceCount();
        this.indexedCount = dashboardData.getIndexedResourceCount();
        this.elapsedTime = DurationFormatUtils.formatDuration(dashboardData.getElapsedTime(),
                "H'H' m'm' s's'");
    }

    private Date startTime;
    private boolean completed;
    private long urlCount;
    private long existedUrlCount;
    private long filteredUrlCount;
    private long invalidUrlCount;
    private long savedCount;
    private long indexedCount;
    private String elapsedTime;

}