package com.github.greenfinger.model;

import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 
 * @Description: CatalogCredential
 * @Author: Fred Feng
 * @Date: 11/01/2025
 * @Version 1.0.0
 */
@Getter
@Setter
@ToString
public class CatalogCredential implements Serializable {

    private static final long serialVersionUID = 6214954568370349702L;

    private Long catalogId;
    private String username;
    private String password;
    private String additionalInformation;

}
