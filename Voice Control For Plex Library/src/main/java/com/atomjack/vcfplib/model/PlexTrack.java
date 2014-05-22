package com.atomjack.vcfplib.model;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(strict=false)
public class PlexTrack {
	@Attribute
	public String key;
	@Attribute
	public String title;
	@Attribute(required=false)
	public String thumb;
	@Attribute(required=false)
	public String parentThumb;
	@Attribute(required=false)
	public String parentTitle;
	@Attribute(required=false)
	public String grandparentTitle;
	@Attribute(required=false)
	public String viewOffset;

	public String artist;
	public String album;

	public PlexServer server;
	
	public PlexServer getServer() {
		return server;
	}

	public void setServer(PlexServer server) {
		this.server = server;
	}
}
