package com.github.greenfinger.api;

import java.util.Date;
import com.github.greenfinger.model.Catalog;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 
 * @Description: CatalogInfo
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@Getter
@Setter
@ToString
public class CatalogInfo extends Catalog {

    private static final long serialVersionUID = -4801844866936776180L;

    private Integer version;
    private Date lastIndexed;
}
