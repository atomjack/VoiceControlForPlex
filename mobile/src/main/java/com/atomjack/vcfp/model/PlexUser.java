package com.atomjack.vcfp.model;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

@Root(strict=false)
public class PlexUser {
	@Attribute(required=false)
	public String authenticationToken;
	@Element
	public String username;
  @Element
  public String email;
}
