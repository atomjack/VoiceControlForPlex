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
    NOTIFICATION_THUMB_MUSIC_BIG,
    WEAR_BACKGROUND,
    LOCAL_VIDEO_BACKGROUND,
    LOCAL_VIDEO_THUMB,
    MUSIC_THUMB,
    MOVIE_THUMB,
    SHOW_THUMB
  }

  public final static Map<IMAGE_KEY, int[]> IMAGE_SIZES = new HashMap<IMAGE_KEY, int[]>() {
    {
      put(IMAGE_KEY.NOTIFICATION_THUMB, new int[] {114*2, 64*2});
      put(IMAGE_KEY.NOTIFICATION_THUMB_BIG, new int[] {87*2, 128*2});
      put(IMAGE_KEY.NOTIFICATION_THUMB_MUSIC, new int[] {128, 128});
      put(IMAGE_KEY.NOTIFICATION_THUMB_MUSIC_BIG, new int[] {256, 256});
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

  public abstract boolean isShow();

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

  public InputStream getNotificationThumb(IMAGE_KEY key, Connection connection) {
    int width;
    width = IMAGE_SIZES.get(key)[0];
    int height;
    height = IMAGE_SIZES.get(key)[1];
    String whichThumb = null;
    if(isMovie()) {
      if(width > height)
        whichThumb = art;
      else
        whichThumb = thumb;

    } else if(isShow()) {
      if(width > height)
        whichThumb = art;
      else
        whichThumb = grandparentThumb;
    } else if(isMusic()) {
      whichThumb = thumb != null ? thumb : grandparentThumb;
    }
    Logger.d("whichThumb: %s, width: %d, height: %d, key: %s", whichThumb, width, height, key);
    return getThumb(width, height, whichThumb, connection);
  }

  // Provides the appropriate notification thumb(image) for the supplied image key
  public String getNotificationThumb(IMAGE_KEY key) {
    int width = IMAGE_SIZES.get(key)[0];
    int height = IMAGE_SIZES.get(key)[1];
    String whichThumb = null;
    if(isMovie()) {
      if(width > height)
        whichThumb = art;
      else
        whichThumb = thumb;

    } else if(isShow()) {
      if(width > height)
        whichThumb = grandparentArt;
      else
        whichThumb = grandparentThumb;
    } else if(isMusic()) {
      whichThumb = thumb != null ? thumb : grandparentThumb;
    }
    return whichThumb;
  }

  public InputStream getThumb(int width, int height, String whichThumb, Connection connection) {
    if(whichThumb == null)
      whichThumb = thumb != null ? thumb :  grandparentThumb;
    String path = String.format("/photo/:/transcode?width=%d&height=%d&url=%s", width, height, Uri.encode(String.format("http://127.0.0.1:32400%s", whichThumb)));
    String url = server.buildURL(connection, path);
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
      none.partId = p.id;
      for (int i = 0; i < p.streams.size(); i++) {
        if (p.streams.get(i).streamType == Stream.SUBTITLE && !ss.contains(none)) {
          ss.add(none);
        }
        p.streams.get(i).partId = p.id;
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

    return null;
  }

  public void setActiveStream(Stream s) {
    for(Stream ss : streams) {
      if(ss.streamType == s.streamType) {
        ss.setActive(s.id.equals(ss.id));
      }
    }
  }

  public Stream getNextStream(int streamType) {
    List<Stream> tempStreams = getStreams(streamType);
    if (tempStreams.size() == 0) {
      return null;
    } else {
      int activeIndex = 0;
      for (int i = 0; i < tempStreams.size(); i++) {
        if (tempStreams.get(i).isActive())
          activeIndex = i;
      }
      Logger.d("Active %s stream: %d", (streamType == Stream.SUBTITLE ? "subtitle" : "audio"), activeIndex);
      int newI = activeIndex + 1 >= tempStreams.size() ? 0 : activeIndex + 1;
      Stream newStream = tempStreams.get(newI);
      return newStream;
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

  @Override
  public boolean equals(Object o) {
    return key.equals(((PlexMedia)o).key);
  }

  @Override
  public int hashCode() {
    final int prime = 37;
    int result = 1;
    result = prime * result + ((key == null) ? 0 : key.hashCode());
    return result;
  }
}

