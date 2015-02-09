package com.atomjack.vcfp.model;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Commit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Root(strict=false)
public class Device {
	@Attribute(required=false)
	public String name;
	@Attribute(required=false)
	public String product;
	@Attribute(required=false)
	public String productVersion;
	@Attribute(required=false)
	public String clientIdentifier;
	@Attribute(required=false)
	public String sourceTitle;
	@Attribute(required=false)
	public String accessToken;

	@ElementList(required=false, inline=true, entry="Connection")
	public List<Connection> connections = new ArrayList<Connection>();

	public List<String> provides;
	public Date lastSeenDate;
	public boolean owned;

	@Attribute(name="provides")
	private String providesStr;

	@Attribute(name="lastSeenAt")
	public int lastSeenAt;

	@Attribute(name="owned")
	private int ownedInt;

	@Commit
	@SuppressWarnings("unused")
	public void build() {
		provides = Arrays.asList(providesStr.split(","));
		lastSeenDate = new Date(lastSeenAt*1000);
		owned = ownedInt == 1;
	}
}
