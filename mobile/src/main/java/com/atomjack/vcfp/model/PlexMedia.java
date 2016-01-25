package com.atomjack.vcfp.model;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import com.atomjack.shared.Logger;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.net.PlexHttpClient;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Root(strict=false)
public abstract class PlexMedia implements Parcelable {
  public enum IMAGE_KEY {
    NOTIFICATION_THUMB,
    NOTIFICATION_THUMB_BIG,
    NOTIFICATION_THUMB_MUSIC,
    WEAR_BACKGROUND
  }

  public final static Map<IMAGE_KEY, int[]> IMAGE_SIZES = new HashMap<IMAGE_KEY, int[]>() {
    {
      put(IMAGE_KEY.NOTIFICATION_THUMB, new int[] {114, 64});
      put(IMAGE_KEY.NOTIFICATION_THUMB_BIG, new int[] {87, 128});
      put(IMAGE_KEY.NOTIFICATION_THUMB_MUSIC, new int[] {64, 64});
      put(IMAGE_KEY.WEAR_BACKGROUND, new int[] {320, 320});
    }
  };

  public static final int TYPE_MOVIE = 0;
  public static final int TYPE_SHOW = 1;
  public static final int TYPE_MUSIC = 2;

  public boolean isMovie() {
    return false;
  }

  public boolean isMusic() {
    return this instanceof PlexTrack;
  }

  public boolean isShow() {
    return false;
  }

  public boolean isClip() { return false; }

  public String getType() {
    if(isMovie())
      return "movie";
    else if(isMusic())
      return "music";
    else if(isShow())
      return "show";
    else if(isClip())
      return "clip";
    else
      return "unknown";
  }

  @Attribute(required=false)
	public String key;
  @Attribute(required=false)
  public String ratingKey;
	@Attribute(required=false)
	public String title;
  @Attribute(required=false)
  public String art;
	@Attribute(required=false)
	public String viewOffset = "0";
	@Attribute(required=false)
	public PlexServer server;
  @Attribute(required=false)
  public String grandparentKey;
	@Attribute(required=false)
	public String grandparentTitle;
  @Attribute(required=false)
  public String grandparentThumb;
  @Attribute(required=false)
  public String grandparentArt;
	@Attribute(required=false)
	public int duration;
	@Attribute(required=false)
	public String thumb;
  @ElementList(required=false, inline=true, entry="Media")
  public List<Media> media = new ArrayList<Media>();

  public int activeSubtitleStream = 0;
  public String parentArt;

	public String getTitle() {
		return title;
	}

	// PlexVideo will override this and return the episode title if it's a show, or "" if not
	public String getEpisodeTitle() {
		return "";
	}

	public String getSummary() {
		return "";
	}

  public String getDurationTimecode() {
    return VoiceControlForPlexApplication.secondsToTimecode(duration/1000);
  }

  public InputStream getNotificationThumb(IMAGE_KEY key) {
    int width;
    width = IMAGE_SIZES.get(key)[0];
    int height;
    height = IMAGE_SIZES.get(key)[1];
    String whichThumb = null;
    if(isMovie()) {
      Logger.d("width/height: %d/%d", width, height);
      if(width > height)
        whichThumb = art;
      else
        whichThumb = thumb;

    } else if(isShow()) {
      if(width > height)
        whichThumb = parentArt;
      else
        whichThumb = grandparentThumb;
    }
    Logger.d("whichThumb: %s", whichThumb);
    return getThumb(width, height, whichThumb);
  }

  public InputStream getThumb(int width, int height) {
    return getThumb(width, height, thumb);
  }

  public InputStream getThumb(int width, int height, String whichThumb) {
    if(whichThumb == null)
      whichThumb = thumb;
    String path = String.format("/photo/:/transcode?width=%d&height=%d&url=%s", width, height, Uri.encode(String.format("http://127.0.0.1:32400%s", whichThumb)));
    String url = server.buildURL(path);
    Logger.d("thumb url: %s", url);
    try {
      byte[] imageData = PlexHttpClient.getSyncBytes(url);
      InputStream is = new ByteArrayInputStream(imageData);
      is.reset();
      return is;
    } catch (SocketTimeoutException e) {
      Logger.d("Couldn't get thumb");
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

	@Override
	public int describeContents() {
		return 0;
	}

  private List<Stream> streams;

  public List<Stream> getStreams() {
    // The list of streams needs to have a "none" subtitle stream added to it (if there is at least
    // one subtitle stream). Subsequent calls to get the list of streams should get this list, since any
    // manipulation of which (audio/video) stream is active will need to be saved to it - the list of
    // streams in media/parts will not reflect the updating of active streams.
    if (streams == null) {
      streams = new ArrayList<>();
      Media m = media.get(0);
      Part p = m.parts.get(0);

      List<Stream> ss = new ArrayList<>();
      Stream none = Stream.getNoneSubtitleStream();
      for (int i = 0; i < p.streams.size(); i++) {
        if (p.streams.get(i).streamType == Stream.SUBTITLE && !ss.contains(none)) {
          ss.add(none);
        }
        ss.add(p.streams.get(i));
      }
      boolean subsActive = false;
      for (Stream s : ss) {
        if(s.streamType == Stream.SUBTITLE && s.isActive())
          subsActive = true;
      }
      if(!subsActive)
        none.setActive(true);
      streams = ss;
    }
    return streams;
  }

  public List<Stream> getStreams(int streamType) {
    List<Stream> s = new ArrayList<Stream>();
    for (Stream stream : getStreams()) {
      if (stream.streamType == streamType)
        s.add(stream);
    }
    return s;
  }

  public Stream getActiveStream(int streamType) {
    List<Stream> streams = getStreams(streamType);
    for(Stream stream : streams) {
      if(stream.isActive())
        return stream;
    }

    return streams.get(0);
  }

  public void setActiveStream(Stream s) {
    for(Stream ss : streams) {
      if(ss.streamType == s.streamType) {
        ss.setActive(s.id.equals(ss.id));
      }
    }
  }

  public PlexMedia() {

  }

  public void writeToParcel(Parcel out, int flags) {
    out.writeString(key);
    out.writeString(title);
    out.writeString(viewOffset);
    out.writeString(grandparentTitle);
    out.writeString(grandparentThumb);
    out.writeString(grandparentArt);
    out.writeString(parentArt);
    out.writeString(thumb);
    out.writeString(art);
    out.writeInt(duration);
    out.writeString(ratingKey);
    out.writeParcelable(server, flags);
    out.writeTypedList(media);
    out.writeString(grandparentKey);
  }

  public PlexMedia(Parcel in) {
    key = in.readString();
    title = in.readString();
    viewOffset = in.readString();
    grandparentTitle = in.readString();
    grandparentThumb = in.readString();
    grandparentArt = in.readString();
    parentArt = in.readString();
    thumb = in.readString();
    art = in.readString();
    duration = in.readInt();
    ratingKey = in.readString();
    server = in.readParcelable(PlexServer.class.getClassLoader());
    media = new ArrayList<Media>();
    in.readTypedList(media, Media.CREATOR);
    grandparentKey = in.readString();
  }

  public String getCacheKey(String which) {
    return String.format("%s%s", server.machineIdentifier, which);
  }

  public String getImageKey(IMAGE_KEY imageKey) {
    if(server == null)
      return null;
    else {
      return String.format("%s/%s/%s", server.machineIdentifier, ratingKey, imageKey);
    }
  }

}

@Root(strict=false)
class Media implements Parcelable {
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

@Root(strict=false)
class Part implements Parcelable {
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
