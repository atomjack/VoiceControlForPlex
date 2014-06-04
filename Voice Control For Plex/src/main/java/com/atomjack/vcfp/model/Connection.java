package com.atomjack.vcfp.model;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Commit;

@Root(strict=false)
public class Connection {
	@Attribute
	public String protocol;
	@Attribute
	public String address;
	@Attribute
	public String port;
	@Attribute
	public String uri;
	public boolean local;

	@Attribute(required=false, name="local")
	private int localStr;

	@Commit
	public void build() {
		local = localStr == 1;
	}

}
