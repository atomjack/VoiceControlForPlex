package com.atomjack.vcfp.model;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

@Root(strict=false)
public class Pin {
	@Element(type=String.class)
	public String code;
	@Element(type=Integer.class)
	public int id;
	@Element(required=false, name="auth_token")
	public String authToken;
}
