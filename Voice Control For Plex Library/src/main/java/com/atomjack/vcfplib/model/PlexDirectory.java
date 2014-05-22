package com.atomjack.vcfplib.model;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(strict=false)
public class PlexDirectory {
	@Attribute
	public String key;
	@Attribute
	public String title;
	@Attribute(required=false)
	public String type;
	@Attribute(required=false)
	public String thumb;
	@Attribute(required=false)
  public String ratingKey;
  @Attribute(required=false)
  public String parentTitle;
  @Attribute(required=false)
  public String parentKey;
  @Attribute(required=false)
  public PlexServer server;
}
