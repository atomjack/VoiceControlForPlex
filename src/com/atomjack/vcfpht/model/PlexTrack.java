package com.atomjack.vcfpht.model;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(strict=false)
public class PlexTrack {
	@Attribute
	private String key;
	@Attribute
	private String title;
	@Attribute(required=false)
	private String thumb;
	@Attribute(required=false)
	private String parentThumb;
	@Attribute(required=false)
	private String parentTitle;
	@Attribute(required=false)
	private String grandparentTitle;
	@Attribute(required=false)
	private String viewOffset;
	
	private String artist;
	private String album;
	
	private PlexServer server;
	
	public String getArtist() {
		return artist;
	}

	public void setArtist(String artist) {
		this.artist = artist;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getParentTitle() {
		return parentTitle;
	}

	public void setParentTitle(String parentTitle) {
		this.parentTitle = parentTitle;
	}

	public String getGrandparentTitle() {
		return grandparentTitle;
	}

	public void setGrandparentTitle(String grandparentTitle) {
		this.grandparentTitle = grandparentTitle;
	}

	public PlexServer getServer() {
		return server;
	}

	public void setServer(PlexServer server) {
		this.server = server;
	}

	public String getViewOffset() {
		return viewOffset;
	}

	public void setViewOffset(String viewOffset) {
		this.viewOffset = viewOffset;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getAlbum() {
		return album;
	}

	public void setAlbum(String album) {
		this.album = album;
	}

	public String getThumb() {
		return thumb;
	}

	public void setThumb(String thumb) {
		this.thumb = thumb;
	}
}
