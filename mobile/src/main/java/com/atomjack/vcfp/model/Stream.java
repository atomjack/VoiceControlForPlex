package com.atomjack.vcfp.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VoiceControlForPlexApplication;

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
  @Attribute(required=false, name="default")
  private int def;
  @Attribute(required=false)
  private int selected;
  public String partId;

  public boolean isActive() {
    return selected == 1;
  }

  public void setActive(boolean active) {
    selected = active ? 1 : 0;
  }

  public String getTitle() {
    if(title != null)
      return title;
    if(language != null)
      return language;
    return VoiceControlForPlexApplication.getInstance().getString(R.string.unknown);
  }

  public static final int UNKNOWN = 0;
  public static final int VIDEO = 1;
  public static final int AUDIO = 2;
  public static final int SUBTITLE = 3;

  @Override
  public String toString() {
    return getTitle();
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
    def = in.readInt();
    title = in.readString();
    selected = in.readInt();
  }

  public static Stream getNoneSubtitleStream() {
    Stream s = new Stream(VoiceControlForPlexApplication.getInstance().getResources().getString(R.string.none));
    s.id = "0";
    s.streamType = Stream.SUBTITLE;
    return s;
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
    out.writeInt(def);
    out.writeString(title);
    out.writeInt(selected);
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
