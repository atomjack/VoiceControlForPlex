package com.atomjack.vcfp.model;

import android.os.Parcel;
import android.os.Parcelable;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.ArrayList;

@Root(strict=false)
public class PlexTrack extends PlexMedia {
	@Attribute(required=false)
	public String parentThumb;
	@Attribute(required=false)
	public String parentTitle;

	public String artist;
	public String album;

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
		out.writeString(artist);
		out.writeString(album);
	}

	public PlexTrack(Parcel in) {
    super(in);
		parentThumb = in.readString();
		parentTitle = in.readString();
		artist = in.readString();
		album = in.readString();
	}

	public static final Parcelable.Creator<PlexTrack> CREATOR = new Parcelable.Creator<PlexTrack>() {
		public PlexTrack createFromParcel(Parcel in) {
			return new PlexTrack(in);
		}

		public PlexTrack[] newArray(int size) {
			return new PlexTrack[size];
		}
	};


}

