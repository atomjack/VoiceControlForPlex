package com.atomjack.shared.model;

import android.os.Parcel;
import android.os.Parcelable;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(strict=false)
public class Timeline implements Parcelable {
	@Attribute
	public String state;
	@Attribute(required=false)
	public int time;
	@Attribute(required=false)
	public String type;
	@Attribute(required=false)
	public int duration;
	@Attribute(required=false)
	public String key;
	@Attribute(required=false)
	public String machineIdentifier; // id of server
  @Attribute(required=false)
  public String continuing;

	public Timeline() {

	}

	public String getTime() {
		int seconds = time / 1000;
		int hours = seconds / 3600;
		int minutes = (seconds % 3600) / 60;
		seconds = seconds % 60;

		return String.format("%d:%d:%d", hours, minutes, seconds);
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel parcel, int i) {
		parcel.writeString(state);
		parcel.writeInt(time);
		parcel.writeString(type);
		parcel.writeInt(duration);
		parcel.writeString(key);
		parcel.writeString(machineIdentifier);
    parcel.writeString(continuing);
	}

	public Timeline(Parcel in) {
		state = in.readString();
		time = in.readInt();
		type = in.readString();
		duration = in.readInt();
		key = in.readString();
		machineIdentifier = in.readString();
    continuing = in.readString();
	}

	public static final Parcelable.Creator<Timeline> CREATOR = new Parcelable.Creator<Timeline>() {
		public Timeline createFromParcel(Parcel in) {
			return new Timeline(in);
		}

		public Timeline[] newArray(int size) {
			return new Timeline[size];
		}
	};

}
