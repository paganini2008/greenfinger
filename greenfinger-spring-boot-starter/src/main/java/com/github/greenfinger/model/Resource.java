package com.github.greenfinger.model;

import java.io.Serializable;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

/**
 * 
 * @Description: Resource
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@Getter
@Setter
public class Resource implements Serializable {

    private static final long serialVersionUID = -4629236151028422706L;
    private Long id;
    private String title;
    private String html;
    private String url;
    private String cat;
    private Date createTime;
    private Integer version;
    private Long catalogId;

    public String toString() {
        return "[Resource] id: " + id + ", title: " + title + ", url: " + url + ", version: "
                + version;
    }

}
