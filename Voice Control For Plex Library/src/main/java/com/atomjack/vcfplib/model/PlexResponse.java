package com.atomjack.vcfplib.model;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(strict=false)
public class PlexResponse {
	@Attribute(required=false)
	public String code;
	@Attribute(required=false)
	public String status;
}
