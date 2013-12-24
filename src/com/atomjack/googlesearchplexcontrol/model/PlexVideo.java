package com.atomjack.googlesearchplexcontrol.model;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(strict=false)
public class PlexVideo {
	@Attribute
	private String key;
	@Attribute
	private String title;
	@Attribute(required=false)
	private String viewOffset;
	@Attribute(required=false)
	private String index;
	@Attribute(required=false)
	private String grandparentTitle;
	
	public String getGrandparentTitle() {
		return grandparentTitle;
	}

	public void setGrandparentTitle(String grandparentTitle) {
		this.grandparentTitle = grandparentTitle;
	}

	public String getIndex() {
		return index;
	}

	public void setIndex(String index) {
		this.index = index;
	}

	public String getViewOffset() {
		return viewOffset;
	}

	public void setViewOffset(String viewOffset) {
		this.viewOffset = viewOffset;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}
	
}
