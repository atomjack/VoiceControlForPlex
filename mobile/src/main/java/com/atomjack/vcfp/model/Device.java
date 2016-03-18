package com.atomjack.vcfp.model;

import android.os.Parcel;
import android.os.Parcelable;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Commit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Root(strict=false)
public class Device implements Parcelable {
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

  @Override
  public void writeToParcel(Parcel parcel, int i) {
    parcel.writeString(name);
    parcel.writeString(product);
    parcel.writeString(productVersion);
    parcel.writeString(clientIdentifier);
    parcel.writeString(sourceTitle);
    parcel.writeString(accessToken);
    parcel.writeTypedList(connections);
    parcel.writeList(provides);
    parcel.writeSerializable(lastSeenDate);
    parcel.writeInt(owned ? 1 : 0);
    parcel.writeInt(lastSeenAt);
  }

  public Device(Parcel in) {
    name = in.readString();
    product = in.readString();
    productVersion = in.readString();
    clientIdentifier = in.readString();
    sourceTitle = in.readString();
    accessToken = in.readString();
    in.readTypedList(connections, Connection.CREATOR);
    in.readStringList(provides);
    lastSeenDate = (Date)in.readSerializable();
    owned = in.readInt() == 1;
    lastSeenAt = in.readInt();
  }

  @Override
  public int describeContents() {
    return 0;
  }



  public static final Parcelable.Creator<Device> CREATOR = new Parcelable.Creator<Device>() {
    public Device createFromParcel(Parcel in) {
      return new Device(in);
    }
    public Device[] newArray(int size) {
      return new Device[size];
    }
  };
}
