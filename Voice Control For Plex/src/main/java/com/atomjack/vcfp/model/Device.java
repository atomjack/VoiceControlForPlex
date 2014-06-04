package com.atomjack.vcfp.model;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Commit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Vector;

@Root(strict=false)
public class Device {
	@Attribute
	public String name;
	@Attribute
	public String product;
	@Attribute
	public String productVersion;
	@Attribute
	public String clientIdentifier;
	@Attribute(required=false)
	public String sourceTitle;
	@Attribute(required=false)
	public String accessToken;

	@ElementList(required=false, inline=true, entry="Connection")
	public List<Connection> connections = new ArrayList<Connection>();

	public Vector<String> provides;
	public Date lastSeenDate;
	public boolean owned;

	@Attribute(name="provides")
	private String providesStr;

	@Attribute(name="lastSeenAt")
	public int lastSeenAt;

	@Attribute(name="owned")
	private int ownedInt;

	@Commit
	public void build() {
		provides = new Vector(Arrays.asList(providesStr.split(",")));
		lastSeenDate = new Date(lastSeenAt*1000);
		owned = ownedInt == 1;
	}
}
