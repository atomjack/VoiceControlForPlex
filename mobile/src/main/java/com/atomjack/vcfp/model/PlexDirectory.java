package com.atomjack.vcfp.model;

import android.os.Parcel;
import android.os.Parcelable;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(strict=false)
public class PlexDirectory implements Parcelable {
	@Attribute(required=false)
	public String key;
	@Attribute(required=false)
	public String title;
	@Attribute(required=false)
	public String type;
	@Attribute(required=false)
	public String thumb;
	@Attribute(required=false)
  public String ratingKey;
  @Attribute(required=false)
  public String parentTitle;
  @Attribute(required=false)
  public String parentKey;
  @Attribute(required=false)
  public PlexServer server;
  @Attribute(required=false)
  public String art;

  public PlexDirectory() {

  }

  public PlexDirectory(Parcel in) {
    key = in.readString();
    title = in.readString();
    type = in.readString();
    thumb = in.readString();
    ratingKey = in.readString();
    parentTitle = in.readString();
    parentKey = in.readString();
    server = in.readParcelable(PlexServer.class.getClassLoader());
    art = in.readString();
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel out, int flags) {
    out.writeString(key);
    out.writeString(title);
    out.writeString(type);
    out.writeString(thumb);
    out.writeString(ratingKey);
    out.writeString(parentTitle);
    out.writeString(parentKey);
    out.writeParcelable(server, flags);
    out.writeString(art);

  }

  public static final Parcelable.Creator<PlexDirectory> CREATOR = new Parcelable.Creator<PlexDirectory>() {
    public PlexDirectory createFromParcel(Parcel in) {
      return new PlexDirectory(in);
    }

    public PlexDirectory[] newArray(int size) {
      return new PlexDirectory[size];
    }
  };


}
