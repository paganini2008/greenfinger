package indi.atlantis.framework.greenfinger.model;

import java.io.Serializable;
import java.util.Date;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 
 * CatalogIndex
 *
 * @author Fred Feng
 * 
 * @since 1.0
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

}
