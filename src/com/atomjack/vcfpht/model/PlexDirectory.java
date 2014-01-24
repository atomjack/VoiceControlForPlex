package com.atomjack.vcfpht.model;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(strict=false)
public class PlexDirectory {
	@Attribute
	private String key;
	@Attribute
	private String title;
	@Attribute(required=false)
	private String type;
	@Attribute(required=false)
	private String thumb;
	@Attribute(required=false)
  public String ratingKey;
  @Attribute(required=false)
  public PlexServer server;

	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public String getKey() {
		return key;
	}
	public void setKey(String key) {
		this.key = key;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getThumb() {
		return thumb;
	}
	public void setThumb(String thumb) {
		this.thumb = thumb;
	}
}
