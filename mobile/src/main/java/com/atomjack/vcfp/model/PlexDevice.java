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


  @Override
  public boolean equals(Object o) {
    if(this == o)
      return true;
    if(o == null)
      return false;
    if(getClass() != o.getClass())
      return false;
    PlexDevice other = (PlexDevice)o;
    if(machineIdentifier == null) {
      if (other.machineIdentifier != null)
        return false;
    } else if(!machineIdentifier.equals(other.machineIdentifier))
      return false;
    return true;
  }

  @Override
  public int hashCode() {
    final int prime = 67;
    int result = 1;
    result = prime * result + ((machineIdentifier == null) ? 0 : machineIdentifier.hashCode());
    return result;
  }

}
