package com.github.greenfinger.model;

import java.io.Serializable;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 
 * @Description: Catalog
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@Getter
@Setter
@ToString
public class Catalog implements Serializable {

    private static final long serialVersionUID = 1980884447290929341L;
    private Long id;
    private String name;
    private String cat;
    private String url;
    private String pageEncoding;
    private String pathPattern;
    private String excludedPathPattern;
    private Integer maxFetchSize;
    private Integer depth;
    private Long interval;
    private Long duration;
    private Integer maxRetryCount;
    private String urlPathAcceptor;
    private String urlPathFilter;
    private String extractor;
    private Date lastModified;

}
