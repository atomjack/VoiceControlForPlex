package com.atomjack.vcfp;

import android.content.Context;
import android.net.Uri;
import android.support.v7.media.MediaRouter;

import com.atomjack.vcfp.activities.MainActivity;
import com.atomjack.vcfp.activities.VCFPActivity;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexVideo;
import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.common.images.WebImage;
import com.google.sample.castcompanionlibrary.cast.VideoCastManager;
import com.google.sample.castcompanionlibrary.widgets.MiniController;

import org.json.JSONObject;

import java.util.Timer;

public class CastPlayerManager {
  private PlexMedia nowPlayingMedia;

  private static VideoCastManager castManager = null;
  private VCFPCastConsumer castConsumer;
  private MiniController miniController;

  protected MediaInfo remoteMediaInformation;

  public static final class PARAMS {
    public static final String MEDIA_TYPE = "media_type";
    public static final String MEDIA_TYPE_VIDEO = "media_type_video";
    public static final String MEDIA_TYPE_AUDIO = "media_type_audio";

    public static final String TITLE = "title";
    public static final String PLOT = "plot";
    public static final String RUNTIME = "runtime";
    public static final String KEY = "key";
    public static final String THUMB = "thumb";
    public static final String OFFSET = "offset";
    public static final String SRC = "src";


    public static final String ACTION = "action";
    public static final String ACTION_LOAD = "load";
    public static final String ACTION_PLAY = "play";
    public static final String ACTION_PAUSE = "pause";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_UPDATE_SRC = "updateSrc";

  };

  private Context mContext;

  private Timer durationTimer;

  private VCFPActivity listener;
  private VCFPActivity notificationListener;

  private boolean subscribed = false;

  public PlexClient mClient;

  private int currentState = MediaStatus.PLAYER_STATE_UNKNOWN;

  private String transientToken;

  public CastPlayerManager(Context context) {
    mContext = context;
    Preferences.setContext(mContext);
    setCastConsumer();
  }

  public void setContext(Context context) {
    mContext = context;
  }

  public void subscribe(final PlexClient _client) {
    if(castManager == null) {
      castManager = getCastManager(mContext);
      castManager.addVideoCastConsumer(castConsumer);
      castManager.incrementUiCounter();
    }
    if(castManager.isConnected()) {
      castManager.disconnect();
    }
    castManager.setDevice(_client.castDevice, false);
    castConsumer.setOnConnected(new Runnable() {
      @Override
      public void run() {
        mClient = _client;
        Logger.d("castConsumer connected");
        subscribed = true;
        listener.onCastConnected(_client);
      }
    });
  }

  public boolean isSubscribed() {
    return subscribed && mClient != null;
  }

  public void unsubscribe() {
    try {
      Logger.d("is connected: %s", castManager.isConnected());
      castManager.stopApplication();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    subscribed = false;
    listener.onCastDisconnected();
  }

  public void setListener(VCFPActivity _listener) {
    listener = _listener;
    if(_listener != null)
      notificationListener = _listener;
  }

  public interface Listener {
    void onCastConnected(PlexClient client);
    void onCastDisconnected();
    void onCastPlayerStateChanged(int status);
    void onCastPlayerTimeUpdate(int seconds);
  };

  public VideoCastManager getCastManager() {
    return castManager;
  }

  // This will send a message to the cast device to load the passed in media
  public void loadMedia(PlexMedia media, int offset) {
    nowPlayingMedia = media;
    sendMessage(buildMedia(offset));
  }

  public void play() {
    sendMessage(PARAMS.ACTION_PLAY);
  }

  public void pause() {
    sendMessage(PARAMS.ACTION_PAUSE);
  }

  public void stop() {
    sendMessage(PARAMS.ACTION_STOP);
  }

  public void seekTo(int seconds) {
    JSONObject obj = new JSONObject();
    try {
      obj.put(PARAMS.ACTION, PARAMS.ACTION_UPDATE_SRC);
      obj.put(PARAMS.SRC, getTranscodeUrl(nowPlayingMedia, seconds));
      obj.put(PARAMS.OFFSET, seconds);
      sendMessage(obj);
    } catch (Exception ex) {}

  }

  private void sendMessage(String action) {
    JSONObject message = new JSONObject();
    try {
      message.put(PARAMS.ACTION, action);
    } catch (Exception ex) {}
    sendMessage(message);
  }

  private void sendMessage(JSONObject obj) {
    try {
      castManager.sendDataMessage(obj.toString());
//      castManager.
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private static VideoCastManager getCastManager(Context context) {
    if (null == castManager) {
      castManager = VideoCastManager.initialize(context, BuildConfig.CHROMECAST_APP_ID,
              null, "urn:x-cast:com.atomjack.vcfp");
      castManager.enableFeatures(
              VideoCastManager.FEATURE_NOTIFICATION |
                      VideoCastManager.FEATURE_LOCKSCREEN |
                      VideoCastManager.FEATURE_DEBUGGING);

    }
    castManager.setContext(context);
    castManager.setStopOnDisconnect(false);
    return castManager;
  }

  private static MediaInfo buildMediaInfo(String title, String summary, String episodeName,
                                          String url, String imgUrl, String bigImageUrl, int duration, int offset) {
    MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);

//		movieMetadata.putString(MediaMetadata.KEY_SUBTITLE, summary);
//		movieMetadata.putString(MediaMetadata.KEY_TITLE, title);
//		movieMetadata.putString(MediaMetadata.KEY_STUDIO, episodeName);
    movieMetadata.addImage(new WebImage(Uri.parse(imgUrl)));
    movieMetadata.addImage(new WebImage(Uri.parse(bigImageUrl)));

    JSONObject customData = new JSONObject();
    try {
      customData.put("duration", duration/1000);
      customData.put("position", offset);

      customData.put("title", title);
      customData.put("summary", summary);
      customData.put("episodeName", episodeName);

    } catch(Exception ex) {}
    return new MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setStreamDuration((long)duration)
            .setCustomData(customData)
            .setContentType("video/mp4")
            .setMetadata(movieMetadata)
            .build();
  }

  private void setCastConsumer() {
    castConsumer = new VCFPCastConsumer() {
      private boolean launched = false;
      private Runnable onConnectedRunnable;

      @Override
      public void onDataMessageReceived(String message) {
        Logger.d("DATA MESSAGE RECEIVED: %s", message);
        try {
          JSONObject obj = new JSONObject(message);
          if(obj.has("event") && obj.has("status")) {
            Logger.d("has event and status");
            if(obj.getString("event").equals("playerStatusChanged")) {
              Logger.d("playerStatusChanged");
              if(obj.getString("status").equals("playing"))
                listener.onCastPlayerStateChanged(MediaStatus.PLAYER_STATE_PLAYING);
              else if(obj.getString("status").equals("paused"))
                listener.onCastPlayerStateChanged(MediaStatus.PLAYER_STATE_PAUSED);
              else if(obj.getString("status").equals("stopped"))
                listener.onCastPlayerStateChanged(MediaStatus.PLAYER_STATE_IDLE);
            }
          } else if(obj.has("event") && obj.getString("event").equals("timeUpdate") && obj.has("currentTime")) {
            listener.onCastPlayerTimeUpdate(obj.getInt("currentTime"));
          }
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      }

      @Override
      public void onRemoteMediaPlayerMetadataUpdated() {
        super.onRemoteMediaPlayerMetadataUpdated();

      }

      @Override
      public void onRemoteMediaPlayerStatusUpdated() {
        super.onRemoteMediaPlayerStatusUpdated();
        Logger.d("onRemoteMediaPlayerStatusUpdated");
        try {
          remoteMediaInformation = castManager.getRemoteMediaInformation();
          MediaMetadata metadata = remoteMediaInformation.getMetadata();
          int lastState = currentState;
          currentState = castManager.getPlaybackStatus();

          Logger.d("currentState: %d", currentState);

          listener.onCastPlayerStateChanged(currentState);
          if(currentState == MediaStatus.PLAYER_STATE_IDLE) {
            Logger.d("idle reason: %d", castManager.getIdleReason());

            /*
            // If we stopped because a seek was done, resume playback at the new offset.
            if(seekDone) {
              seekDone = false;
              Logger.d("resuming playback with an offset of %s", nowPlayingMedia.viewOffset);
              beginPlayback();
            } else {
              if (durationTimer != null)
                stopDurationTimer();
              finish();
            }
            */
          } else if(currentState == MediaStatus.PLAYER_STATE_PAUSED) {
//            setState(MediaStatus.PLAYER_STATE_PAUSED);
//            stopDurationTimer();
          } else if(currentState == MediaStatus.PLAYER_STATE_PLAYING) {
//            setState(MediaStatus.PLAYER_STATE_PLAYING);
//            startDurationTimer();
          }
//					Logger.d("metadata: %s", metadata);
        } catch (Exception ex) {
          // silent
          ex.printStackTrace();
        }
      }

      @Override
      public void setOnConnected(Runnable runnable) {
        onConnectedRunnable = runnable;
      }

      @Override
      public void onFailed(int resourceId, int statusCode) {
        Logger.d("castConsumer failed: %d", statusCode);
      }

      @Override
      public void onConnectionSuspended(int cause) {
        Logger.d("onConnectionSuspended() was called with cause: " + cause);
//					com.google.sample.cast.refplayer.utils.Utils.
//									showToast(VideoBrowserActivity.this, R.string.connection_temp_lost);
      }

      @Override
      public void onApplicationConnected(ApplicationMetadata appMetadata,
                                         String sessionId, boolean wasLaunched) {
        super.onApplicationConnected(appMetadata, sessionId, wasLaunched);
        Logger.d("onApplicationConnected()");
        Logger.d("metadata: %s", appMetadata);
        Logger.d("sessionid: %s", sessionId);
        Logger.d("was launched: %s", wasLaunched);
//        if(!launched) {
          launched = true;
          if(onConnectedRunnable != null)
            onConnectedRunnable.run();
//        }

      }

      @Override
      public void onConnectivityRecovered() {
//					com.google.sample.cast.refplayer.utils.Utils.
//									showToast(VideoBrowserActivity.this, R.string.connection_recovered);
      }

      @Override
      public void onApplicationStatusChanged(String appStatus) {
        Logger.d("CastPlayerManager onApplicationStatusChanged: %s", appStatus);
      }

      @Override
      public void onVolumeChanged(double value, boolean isMute) {
        super.onVolumeChanged(value, isMute);
        Logger.d("Volume is now %s", Double.toString(value));
      }

      @Override
      public void onCastDeviceDetected(final MediaRouter.RouteInfo info) {
        Logger.d("onCastDeviceDetected: %s", info);
      }
    };
  }

  public String getTranscodeUrl(PlexMedia media) {
    return getTranscodeUrl(media, 0);
  }

  public String getTranscodeUrl(PlexMedia media, int offset) {
    String url = media.server.activeConnection.uri;
    url += "/video/:/transcode/universal/start?";
    QueryString qs = new QueryString("path", String.format("http://127.0.0.1:32400%s", media.key));
    qs.add("mediaIndex", "0");
    qs.add("partIndex", "0");
    qs.add("protocol", "http");
    qs.add("offset", Integer.toString(offset));
    qs.add("fastSeek", "1");
    qs.add("directPlay", "0");
    qs.add("directStream", "1");
    qs.add("videoQuality", "60");
    qs.add("videoResolution", "1024x768");
    qs.add("maxVideoBitrate", "2000");
    qs.add("subtitleSize", "100");
    qs.add("audioBoost", "100");
    qs.add("session", VoiceControlForPlexApplication.generateRandomString());
    qs.add(PlexHeaders.XPlexClientIdentifier, Preferences.getUUID());
    qs.add(PlexHeaders.XPlexProduct, String.format("%s Chromecast", mContext.getString(R.string.app_name)));
    qs.add(PlexHeaders.XPlexDevice, mClient.castDevice.getModelName());
    qs.add(PlexHeaders.XPlexDeviceName, mClient.castDevice.getModelName());
    qs.add(PlexHeaders.XPlexPlatform, mClient.castDevice.getModelName());
    if(transientToken != null)
      qs.add(PlexHeaders.XPlexToken, transientToken);
    qs.add(PlexHeaders.XPlexPlatformVersion, "1.0");
    try {
      qs.add(PlexHeaders.XPlexVersion, mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionName);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    // TODO: Fix this
    if(Preferences.getString(Preferences.PLEX_USERNAME) != null)
      qs.add(PlexHeaders.XPlexUsername, Preferences.getString(Preferences.PLEX_USERNAME));
    return url + qs.toString();
  }

  public void setTransientToken(String transientToken) {
    this.transientToken = transientToken;
  }

  public JSONObject buildMedia() {
    return buildMedia(0);
  }

  public JSONObject buildMedia(int offset) {
    JSONObject data = new JSONObject();
    try {
      data.put(CastPlayerManager.PARAMS.ACTION, CastPlayerManager.PARAMS.ACTION_LOAD);
      data.put(CastPlayerManager.PARAMS.MEDIA_TYPE, nowPlayingMedia instanceof PlexVideo ? CastPlayerManager.PARAMS.MEDIA_TYPE_VIDEO : CastPlayerManager.PARAMS.MEDIA_TYPE_AUDIO);
      String title = nowPlayingMedia.title;
      if(nowPlayingMedia instanceof PlexVideo) {
        PlexVideo video = (PlexVideo)nowPlayingMedia;
        data.put(CastPlayerManager.PARAMS.PLOT, video.summary);
        if(video.isMovie())
          title = String.format("%s (%s)", video.title, video.year);
        else
          title = String.format("%s - %s (%s)", video.grandparentTitle, video.title, video.year);
      }

      data.put(CastPlayerManager.PARAMS.TITLE, title);
      data.put(CastPlayerManager.PARAMS.RUNTIME, nowPlayingMedia.duration / 1000);
      data.put(CastPlayerManager.PARAMS.KEY, nowPlayingMedia.key);
      data.put(CastPlayerManager.PARAMS.THUMB, nowPlayingMedia.getThumbUri(200, 300));
      data.put(CastPlayerManager.PARAMS.OFFSET, offset);
      data.put(CastPlayerManager.PARAMS.SRC, getTranscodeUrl(nowPlayingMedia, offset));
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return data;
  }
}
