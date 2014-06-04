package com.atomjack.vcfp.model;

import android.os.Parcelable;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;

import java.util.List;

public abstract class PlexDevice implements Parcelable {
  @Attribute
  public String name;
  @Attribute
	public String address;
  @Attribute
	public String port;
  @Attribute
	public String version;
  @Attribute(required=false)
	public String product;
	@Attribute(required=false)
	public String provides;
	@Attribute(required=false)
	public String lastSeenAt;
	@ElementList(required=false, inline=true)
	public List<Connection> connections;
	@Attribute
	public String machineIdentifier;




}
