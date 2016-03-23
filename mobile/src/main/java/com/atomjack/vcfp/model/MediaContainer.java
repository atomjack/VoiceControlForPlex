package com.atomjack.vcfp.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.atomjack.shared.model.Timeline;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.ArrayList;
import java.util.List;

@Root(strict=false)
public class MediaContainer implements Parcelable {
	@Attribute(required=false)
	public String machineIdentifier;
	
	@Attribute(required=false)
	public String friendlyName;
	
	@Attribute(required=false)
	public String title1;
	
	@Attribute(required=false)
	public String grandparentTitle;

	@ElementList(required=false, inline=true, name="Server")
	public List<PlexClient> clients = new ArrayList<PlexClient>();
	
	@ElementList(required=false, inline=true, entry="Directory")
	public List<PlexDirectory> directories = new ArrayList<PlexDirectory>();
	
	@ElementList(required=false, inline=true, entry="Video")
	public ArrayList<PlexVideo> videos = new ArrayList<PlexVideo>();
	
	@ElementList(required=false, inline=true, entry="Track")
	public ArrayList<PlexTrack> tracks = new ArrayList<PlexTrack>();

	@ElementList(required=false, inline=true, entry="Device")
	public List<Device> devices = new ArrayList<Device>();

	@Attribute(required=false)
	public String token;

	@Attribute(required=false)
	public String location;

	@Attribute(required=false)
	public String commandID;

	@ElementList(required=false, inline=true, entry="Timeline")
	public List<Timeline> timelines;

  @Attribute(required=false)
  public String art;

  @Attribute(required=false)
  public String playQueueID;

  public MediaContainer() {

  }

	public Timeline getTimeline(String type) {
		if(timelines != null) {
			for (Timeline t : timelines) {
				if (t.type.equals(type))
					return t;
			}
		}
		return null;
	}

  public Timeline getActiveTimeline() {
    for(Timeline t : timelines) {
      if(t.state != null && !t.state.equals("stopped"))
        return t;
    }
    return null;
  }

  @Override
  public void writeToParcel(Parcel parcel, int i) {
    parcel.writeString(machineIdentifier);
    parcel.writeString(friendlyName);
    parcel.writeString(title1);
    parcel.writeString(grandparentTitle);
    parcel.writeString(token);
    parcel.writeString(location);
    parcel.writeString(commandID);
    parcel.writeString(art);
    parcel.writeString(playQueueID);
    parcel.writeTypedList(clients);
    parcel.writeTypedList(directories);
    parcel.writeTypedList(videos);
    parcel.writeTypedList(tracks);
    parcel.writeTypedList(devices);
  }

  public MediaContainer(Parcel in) {
    machineIdentifier = in.readString();
    friendlyName = in.readString();
    title1 = in.readString();
    grandparentTitle = in.readString();
    token = in.readString();
    location = in.readString();
    commandID = in.readString();
    art = in.readString();
    playQueueID = in.readString();
    in.readTypedList(clients, PlexClient.CREATOR);
    in.readTypedList(directories, PlexDirectory.CREATOR);
    in.readTypedList(videos, PlexVideo.CREATOR);
    in.readTypedList(tracks, PlexTrack.CREATOR);
    in.readTypedList(devices, Device.CREATOR);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Parcelable.Creator<MediaContainer> CREATOR = new Parcelable.Creator<MediaContainer>() {
    public MediaContainer createFromParcel(Parcel in) {
      return new MediaContainer(in);
    }
    public MediaContainer[] newArray(int size) {
      return new MediaContainer[size];
    }
  };
}
