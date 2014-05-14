package com.atomjack.vcfp.model;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(name="Server", strict=false)
public class PlexClient extends PlexDevice {
	@Attribute
	private String host;

	public String getHost() {
		return host;
	}
	public void setHost(String host) {
		this.host = host;
	}
}
