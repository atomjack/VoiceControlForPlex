package com.atomjack.vcfp.model;

import android.os.Parcel;
import android.os.Parcelable;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(strict=false)
public class Stream implements Parcelable {
  @Attribute(required=false)
  public String id;
  @Attribute(required=false)
  public int streamType;
  @Attribute(required=false)
  public String language;
  @Attribute(required=false)
  public int index;
  @Attribute(required=false)
  public String title;

  public boolean active;

  @Override
  public String toString() {
    return title;
  }

  public Stream() {

  }

  public Stream(String t) {
    title = t;
  }

  public Stream(Parcel in) {
    id = in.readString();
    streamType = in.readInt();
    language = in.readString();
    active = in.readByte() != 0;
    title = in.readString();
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel out, int flags) {
    out.writeString(id);
    out.writeInt(streamType);
    out.writeString(language);
    out.writeByte((byte)(active ? 1 : 0));
    out.writeString(title);
  }

  public static final Parcelable.Creator<Stream> CREATOR = new Parcelable.Creator<Stream>() {
    public Stream createFromParcel(Parcel in) {
      return new Stream(in);
    }

    public Stream[] newArray(int size) {
      return new Stream[size];
    }
  };
}
