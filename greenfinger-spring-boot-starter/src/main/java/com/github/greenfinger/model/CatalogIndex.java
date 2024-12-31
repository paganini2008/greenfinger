package com.github.greenfinger.model;

import java.io.Serializable;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 
 * @Description: CatalogIndex
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@Getter
@Setter
@ToString
public class CatalogIndex implements Serializable {

    private static final long serialVersionUID = 599930283370705308L;

    private Long id;
    private Long catalogId;
    private Date lastModified;
    private Integer version;

    public CatalogIndex(Long catalogId, Integer version, Date lastModified) {
        this.catalogId = catalogId;
        this.version = version;
        this.lastModified = lastModified;
    }

    public CatalogIndex() {}

}
