package com.atomjack.vcfp.model;

import android.os.Parcel;
import android.os.Parcelable;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.ArrayList;
import java.util.List;

@Root(strict=false)
public class Part implements Parcelable {
  @Attribute(required=false)
  public String id;
  @ElementList(required=false, inline=true, entry="Stream")
  public List<Stream> streams = new ArrayList<>();
  @Attribute(required=false)
  public String key;
  @Attribute(required=false)
  public int duration;

  public Part() {

  }

  public Part(Parcel in) {
    id = in.readString();
    streams = new ArrayList<>();
    in.readTypedList(streams, Stream.CREATOR);
    key = in.readString();
    duration = in.readInt();
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel out, int flags) {
    out.writeString(id);
    out.writeTypedList(streams);
    out.writeString(key);
    out.writeInt(duration);
  }

  public static final Parcelable.Creator<Part> CREATOR = new Parcelable.Creator<Part>() {
    public Part createFromParcel(Parcel in) {
      return new Part(in);
    }

    public Part[] newArray(int size) {
      return new Part[size];
    }
  };
}
