package com.atomjack.vcfp;

import android.content.Context;
import android.support.v7.media.MediaRouter;

import com.atomjack.shared.Logger;
import com.atomjack.shared.PlayerState;
import com.atomjack.shared.Preferences;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.model.PlexVideo;
import com.google.android.gms.cast.ApplicationMetadata;
import com.google.sample.castcompanionlibrary.cast.VideoCastManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class CastPlayerManager {
  private PlexMedia nowPlayingMedia;
  private List<PlexMedia> nowPlayingAlbum;

  private static VideoCastManager castManager = null;
  private VCFPCastConsumer castConsumer;

  private String mSessionId;

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
    public static final String RESUME="resume";

    public static final String SRC = "src";
    public static final String ART = "art";

    public static final String ARTIST = "artist";
    public static final String ALBUM = "album";
    public static final String TRACK = "track";

    public static final String CLIENT = "client";

    public static final String PLAYLIST = "playlist";

    public static final String ACTION = "action";
    public static final String ACTION_LOAD = "load";
    public static final String ACTION_PLAY = "play";
    public static final String ACTION_PAUSE = "pause";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_SEEK = "seek";
    public static final String ACTION_GET_PLAYBACK_STATE = "getPlaybackState";
    public static final String ACTION_NEXT = "next";
    public static final String ACTION_PREV = "prev";

    public static final String PLEX_USERNAME = "plexUsername";

  };

  private Context mContext;

  private CastListener listener;

  private boolean subscribed = false;

  public PlexClient mClient;

  private PlayerState currentState = PlayerState.STOPPED;

  private String transientToken;

  public CastPlayerManager(Context context) {
    mContext = context;
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
//        currentState = castManager.getPlaybackStatus();
        Logger.d("castConsumer connected to %s", mClient.name);
        getPlaybackState();
        subscribed = true;
        if(listener != null)
          listener.onCastConnected(_client);
        else
          Logger.d("[CastPlayerManager] listener is null");
      }
    });
  }

  public boolean isSubscribed() {
    return subscribed && mClient != null;
  }

  public void unsubscribe() {
    try {
      Logger.d("is connected: %s", castManager.isConnected());
      if(castManager.isConnected())
        castManager.stopApplication();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    subscribed = false;
    if(listener != null)
      listener.onCastDisconnected();
  }

  public void setListener(CastListener _listener) {
    listener = _listener;
//    if(_listener != null)
//      notificationListener = _listener;
  }

  public CastListener getListener() {
    return listener;
  }

  public interface CastListener {
    void onCastConnected(PlexClient client);
    void onCastDisconnected();
    void onCastPlayerStateChanged(PlayerState state);
    void onCastPlayerTimeUpdate(int seconds);
    void onCastPlayerPlaylistAdvance(PlexMedia media);
    void onCastPlayerState(PlayerState state, PlexMedia media);
    PlexMedia getNowPlayingMedia();
  };

  public VideoCastManager getCastManager() {
    return castManager;
  }

  // This will send a message to the cast device to load the passed in media
  public void loadMedia(PlexMedia media, List<PlexMedia> album, int offset) {
    nowPlayingMedia = media;
    nowPlayingAlbum = album;
    sendMessage(buildMedia(offset));
  }

  public void getPlaybackState() {
    sendMessage(PARAMS.ACTION_GET_PLAYBACK_STATE);
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
      obj.put(PARAMS.ACTION, PARAMS.ACTION_SEEK);
      obj.put(PARAMS.OFFSET, seconds);
      if(nowPlayingMedia instanceof PlexVideo)
        obj.put(PARAMS.SRC, getTranscodeUrl(nowPlayingMedia, seconds));
      obj.put(PARAMS.RESUME, VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.RESUME, false));
      sendMessage(obj);
    } catch (Exception ex) {}

  }

  public void doNext() {
    JSONObject obj = new JSONObject();
    try {
      obj.put(PARAMS.ACTION, PARAMS.ACTION_NEXT);
      sendMessage(obj);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  public void doPrevious() {
    JSONObject obj = new JSONObject();
    try {
      obj.put(PARAMS.ACTION, PARAMS.ACTION_PREV);
      sendMessage(obj);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
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

  private void setCastConsumer() {
    castConsumer = new VCFPCastConsumer() {
      private Runnable onConnectedRunnable;

      @Override
      public void onApplicationDisconnected(int errorCode) {
//        super.onApplicationDisconnected(errorCode);
      }

      @Override
      public void onDataMessageReceived(String message) {
//        Logger.d("DATA MESSAGE RECEIVED: %s", message);
        try {
          JSONObject obj = new JSONObject(message);
          if(obj.has("event") && obj.has("status")) {
            if(obj.getString("event").equals("playerStatusChanged")) {
              Logger.d("playerStatusChanged: %s", obj.getString("status"));
              currentState = PlayerState.getState(obj.getString("status"));
              listener.onCastPlayerStateChanged(currentState);
            }
          } else if(obj.has("event") && obj.getString("event").equals("timeUpdate") && obj.has("currentTime")) {
            listener.onCastPlayerTimeUpdate(obj.getInt("currentTime"));
          } else if(obj.has("event") && obj.getString("event").equals("playlistAdvance") && obj.has("media")) {
            Logger.d("[CastPlayerManager] playlistAdvance");
            nowPlayingMedia = VoiceControlForPlexApplication.gsonRead.fromJson(obj.getString("media"), PlexTrack.class);
            listener.onCastPlayerPlaylistAdvance(nowPlayingMedia);
          } else if(obj.has("event") && obj.getString("event").equals("getPlaybackState") && obj.has("state")) {
            currentState = PlayerState.getState(obj.getString("state"));
            PlexMedia media = null;
            if(obj.has("media") && obj.has("type") && obj.has("client")) {
              if(obj.getString("type").equals(PARAMS.MEDIA_TYPE_VIDEO))
                media = VoiceControlForPlexApplication.gsonRead.fromJson(obj.getString("media"), PlexVideo.class);
              else
                media = VoiceControlForPlexApplication.gsonRead.fromJson(obj.getString("media"), PlexTrack.class);
              mClient = VoiceControlForPlexApplication.gsonRead.fromJson(obj.getString("client"), PlexClient.class);
            }
            listener.onCastPlayerState(PlayerState.getState(obj.getString("state")), media);
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
        mSessionId = sessionId;
//        if(!launched) {
//          launched = true;
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
    url += String.format("/%s/:/transcode/universal/start?", media instanceof PlexVideo ? "video" : "audio");
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
    qs.add("session", mSessionId);
    qs.add(PlexHeaders.XPlexClientIdentifier, VoiceControlForPlexApplication.getInstance().prefs.getUUID());
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
    if(VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.PLEX_USERNAME) != null)
      qs.add(PlexHeaders.XPlexUsername, VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.PLEX_USERNAME));
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
      data.put(PARAMS.ACTION, PARAMS.ACTION_LOAD);
      if(VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.PLEX_USERNAME) != null) {
        data.put(PARAMS.PLEX_USERNAME, VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.PLEX_USERNAME));
      }
      data.put(PARAMS.MEDIA_TYPE, nowPlayingMedia instanceof PlexVideo ? PARAMS.MEDIA_TYPE_VIDEO : PARAMS.MEDIA_TYPE_AUDIO);
      data.put(PARAMS.RESUME, VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.RESUME, false));
      data.put(PARAMS.CLIENT, VoiceControlForPlexApplication.gsonWrite.toJson(mClient));
      data.put(PARAMS.SRC, getTranscodeUrl(nowPlayingMedia, offset));
      data.put(PARAMS.PLAYLIST, getPlaylistJson());
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return data;
  }

  private String getPlaylistJson() {
    String json = "{}";
    if(nowPlayingMedia instanceof PlexTrack) {
      json = VoiceControlForPlexApplication.gsonWrite.toJson(nowPlayingAlbum);
    } else {
      ArrayList<PlexMedia> playlist = new ArrayList<PlexMedia>();
      playlist.add(nowPlayingMedia);
      json = VoiceControlForPlexApplication.gsonWrite.toJson(playlist);
    }
    return json;
  }

  public PlayerState getCurrentState() {
    return currentState;
  }

  public PlexMedia getNowPlayingMedia() {
    return nowPlayingMedia;
  }

  public String getSessionId() {
    return mSessionId;
  }
}
