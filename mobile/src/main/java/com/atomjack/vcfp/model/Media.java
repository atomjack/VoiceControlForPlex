package com.atomjack.vcfp.model;

import android.os.Parcel;
import android.os.Parcelable;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.ArrayList;
import java.util.List;

@Root(strict=false)
public class Media implements Parcelable {
  @ElementList(required=false, inline=true, entry="Part")
  public List<Part> parts = new ArrayList<Part>();

  public Media() {

  }

  public Media(Parcel in) {
    parts = new ArrayList<Part>();
    in.readTypedList(parts, Part.CREATOR);
  }
  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel out, int flags) {
    out.writeTypedList(parts);
  }

  public static final Parcelable.Creator<Media> CREATOR = new Parcelable.Creator<Media>() {
    public Media createFromParcel(Parcel in) {
      return new Media(in);
    }

    public Media[] newArray(int size) {
      return new Media[size];
    }
  };
}