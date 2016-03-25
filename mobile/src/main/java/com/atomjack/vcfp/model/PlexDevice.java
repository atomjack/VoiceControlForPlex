package com.atomjack.vcfp.model;

import android.os.Parcelable;

import com.atomjack.shared.NewLogger;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;

import java.util.Date;
import java.util.List;

public abstract class PlexDevice implements Parcelable {
  @Attribute(required=false)
  public String name;
  @Attribute(required=false)
	public String address;
  @Attribute(required=false)
	public String port;
  @Attribute(required=false)
	public String version;
  @Attribute(required=false)
	public String product;
	@Attribute(required=false)
	public String provides;
	@Attribute(required=false)
	public String lastSeenAt;
	@ElementList(required=false, inline=true)
	public List<Connection> connections;
	@Attribute(required=false)
	public String machineIdentifier;
  @Attribute(required=false)
  public Date lastUpdated;

  protected NewLogger logger;

  public PlexDevice() {
    lastUpdated = new Date();
    logger = new NewLogger(this);
  }




}
