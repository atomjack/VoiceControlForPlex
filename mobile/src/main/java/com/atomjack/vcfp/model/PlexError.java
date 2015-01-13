package com.atomjack.vcfp.model;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.List;

@Root(strict=false)
public class PlexError {
	@ElementList(entry="error",inline=true,type=String.class)
	public List<String> errors;
}
