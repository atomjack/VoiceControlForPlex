package com.atomjack.vcfp.model;

import android.os.Parcel;
import android.os.Parcelable;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(strict=false)
public class PlexTrack extends PlexMedia {
	@Attribute(required=false)
	public String parentThumb;
	@Attribute(required=false)
	public String parentTitle;

  public String getArtist() {
    return grandparentTitle;
  }

  public String getAlbum() {
    return parentTitle;
  }

	@Override
	public int describeContents() {
		return 0;
	}

	public PlexTrack() {

	}

	@Override
	public void writeToParcel(Parcel out, int i) {
    super.writeToParcel(out, i);
		out.writeString(parentThumb);
		out.writeString(parentTitle);
	}

	public PlexTrack(Parcel in) {
    super(in);
		parentThumb = in.readString();
		parentTitle = in.readString();
	}

	public static final Parcelable.Creator<PlexTrack> CREATOR = new Parcelable.Creator<PlexTrack>() {
		public PlexTrack createFromParcel(Parcel in) {
			return new PlexTrack(in);
		}

		public PlexTrack[] newArray(int size) {
			return new PlexTrack[size];
		}
	};

  public Part getPart() {
    return media.get(0).parts.get(0);
  }

}

