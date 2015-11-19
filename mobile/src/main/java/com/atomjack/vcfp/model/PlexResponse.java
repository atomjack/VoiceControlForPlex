package com.atomjack.vcfp.model;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(strict=false)
public class PlexResponse {
	@Attribute(required=false)
	public int code = -1;
	@Attribute(required=false)
	public String status;
}
