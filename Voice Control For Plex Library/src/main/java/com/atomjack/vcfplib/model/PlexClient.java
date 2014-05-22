package com.atomjack.vcfplib.model;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(name="Server", strict=false)
public class PlexClient extends PlexDevice {
	@Attribute
	public String host;
}
