package com.atomjack.vcfp;

import android.content.Context;
import android.support.v7.media.MediaRouter;

import com.atomjack.shared.Logger;
import com.atomjack.shared.PlayerState;
import com.atomjack.shared.Preferences;
import com.atomjack.vcfp.interfaces.ActiveConnectionHandler;
import com.atomjack.vcfp.interfaces.PlexSubscriptionListener;
import com.atomjack.vcfp.model.Capabilities;
import com.atomjack.vcfp.model.Connection;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.model.PlexVideo;
import com.atomjack.vcfp.model.Stream;
import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.libraries.cast.companionlibrary.cast.CastConfiguration;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class CastPlayerManager {
  private PlexMedia nowPlayingMedia;
  private List<? extends PlexMedia> nowPlayingAlbum;

  private static VideoCastManager castManager = null;
  private VCFPCastConsumer castConsumer;

  private PlexSubscriptionListener listener;

  private String mSessionId;
  private String plexSessionId;

  private double volume = 1.0;

  private int position = 0; // current position in seconds of the playing media

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
    public static final String RESUME = "resume";
    public static final String VOLUME = "volume";

    public static final String SRC = "src";
    public static final String SUBTITLE_SRC = "subtitle_src";
    public static final String AUDIO_STREAMS = "audio_streams";
    public static final String SUBTITLE_STREAMS = "subtitle_streams";
    public static final String ACTIVE_SUBTITLE = "active_subtitle";
    public static final String STREAM_TYPE = "stream_type";
    public static final String STREAM_ID = "stream_id";

    public static final String SESSION_ID = "session_id";

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
    public static final String ACTION_SET_STREAM = "setStream";
    public static final String ACTION_CYCLE_STREAMS = "cycleStreams";
    public static final String ACTION_SET_VOLUME = "setVolume";

    public static final String PLEX_USERNAME = "plexUsername";
    public static final String ACCESS_TOKEN = "accessToken";

    public static final String RECEIVE_SERVERS = "receiveServers";
    public static final String SERVERS = "servers";

  };

  public static final class RECEIVER_EVENTS {
    public static final String PLAYLIST_ADVANCE = "playlistAdvance";
    public static final String PLAYER_STATUS_CHANGED = "playerStatusChanged";
    public static final String GET_PLAYBACK_STATE = "getPlaybackState";
    public static final String TIME_UPDATE = "timeUpdate";
    public static final String DEVICE_CAPABILITIES = "deviceCapabilities";
    public static final String SHUTDOWN = "shutdown";
  }

  private Context mContext;

//  private CastListener listener;

  private boolean subscribed = false;

  public PlexClient mClient;

  private PlayerState currentState = PlayerState.STOPPED;

  private String transientToken;

  public CastPlayerManager(Context context) {
    mContext = context;
    plexSessionId = VoiceControlForPlexApplication.generateRandomString();
    setCastConsumer();
  }

  public void setContext(Context context) {
    mContext = context;
  }



  public void subscribe(final PlexClient _client) {
    subscribe(_client, null);
  }

  public void subscribe(final PlexClient _client, final Runnable onFinished) {
    if(castManager == null) {
      Logger.d("creating castManager");
      castManager = getCastManager(mContext);
      castManager.addVideoCastConsumer(castConsumer);
      castManager.incrementUiCounter();
    }
    if(castManager.isConnected()) {
      castManager.disconnect();
    }
    Logger.d("selecting device: %s", _client.castDevice);
    castManager.onDeviceSelected(_client.castDevice, null);
    Logger.d("device selected");
    castConsumer.setOnConnected(new Runnable() {
      @Override
      public void run() {
        mClient = _client;
//        currentState = castManager.getPlaybackStatus();
        Logger.d("castConsumer connected to %s", mClient.name);
        getPlaybackState();

        //
        JSONObject obj = new JSONObject();
        try {
          obj.put(PARAMS.ACTION, PARAMS.RECEIVE_SERVERS);

          Type serverType = new TypeToken<ConcurrentHashMap<String, PlexServer>>(){}.getType();
          PlexServer server = VoiceControlForPlexApplication.gsonRead.fromJson(VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.SERVER, ""), PlexServer.class);
          if(server.name.equals(mContext.getString(R.string.scan_all)))
            obj.put(PARAMS.SERVERS, VoiceControlForPlexApplication.gsonWrite.toJson(VoiceControlForPlexApplication.getInstance().servers, serverType));
          else {
            ConcurrentHashMap<String, PlexServer> map = new ConcurrentHashMap<String, PlexServer>();
            map.put(server.name, server);
            obj.put(PARAMS.SERVERS, VoiceControlForPlexApplication.gsonWrite.toJson(map, serverType));
          }
          sendMessage(obj);
        } catch (Exception ex) {}



        subscribed = true;
        if(listener != null)
          listener.onSubscribed(_client);
        else
          Logger.d("[CastPlayerManager] listener is null");

        if(onFinished != null)
          onFinished.run();
      }
    });
  }

  public boolean isSubscribed() {
//    Logger.d("[CastPlayerManager] subscribed: %s, client: %s", subscribed, mClient);
    return subscribed && mClient != null;
  }

  public void unsubscribe() {
    try {
      Logger.d("is connected: %s", castManager.isConnected());
      if(castManager.isConnected()) {
        castManager.disconnect();
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    subscribed = false;
    mClient = null;
    if(listener != null)
      listener.onUnsubscribed();
  }

  public void setListener(PlexSubscriptionListener _listener) {
    listener = _listener;
//    if(_listener != null)
//      notificationListener = _listener;
  }

  public PlexSubscriptionListener getListener() {
    return listener;
  }

  public VideoCastManager getCastManager() {
    return castManager;
  }

  // This will send a message to the cast device to load the passed in media
  public void loadMedia(PlexMedia media, List<? extends PlexMedia> album, final int offset) {
    Logger.d("Loading media: %s", album);
    nowPlayingMedia = media;
    nowPlayingAlbum = album;
    nowPlayingMedia.server.findServerConnection(new ActiveConnectionHandler() {
      @Override
      public void onSuccess(Connection connection) {
        sendMessage(buildMedia(connection, offset));
      }

      @Override
      public void onFailure(int statusCode) {

      }
    });
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

  public void seekTo(final int seconds) {
    nowPlayingMedia.server.findServerConnection(new ActiveConnectionHandler() {
      @Override
      public void onSuccess(Connection connection) {
        JSONObject obj = new JSONObject();
        try {
          obj.put(PARAMS.ACTION, PARAMS.ACTION_SEEK);
          obj.put(PARAMS.OFFSET, seconds);
          if (nowPlayingMedia instanceof PlexVideo)
            obj.put(PARAMS.SRC, getTranscodeUrl(nowPlayingMedia, connection, seconds));
          obj.put(PARAMS.RESUME, VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.RESUME, false));
          sendMessage(obj);
          listener.onTimeUpdate(currentState, seconds);
          position = seconds;
        } catch (Exception ex) {
        }
      }

      @Override
      public void onFailure(int statusCode) {
        // TODO: Handle failure
      }
    });
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
      CastConfiguration options = new CastConfiguration.Builder(BuildConfig.CHROMECAST_APP_ID)
              .addNamespace("urn:x-cast:com.atomjack.vcfp")
              .build();
      VideoCastManager.initialize(context, options);

/*
      VideoCastManager.initialize(context, BuildConfig.CHROMECAST_APP_ID,
              null, "urn:x-cast:com.atomjack.vcfp")
          .enableFeatures(
                  VideoCastManager.FEATURE_NOTIFICATION |
                          VideoCastManager.FEATURE_LOCKSCREEN |
                          VideoCastManager.FEATURE_DEBUGGING);
                          */

    }
//    castManager.setContext(context);
    castManager = VideoCastManager.getInstance();
    castManager.setStopOnDisconnect(false);
    return castManager;
  }

  private void setCastConsumer() {
    castConsumer = new VCFPCastConsumer() {
      private Runnable onConnectedRunnable;

      @Override
      public void onApplicationDisconnected(int errorCode) {
        Logger.d("[CastPlayerManager] onApplicationDisconnected: %d", errorCode);
//        super.onApplicationDisconnected(errorCode);
      }

      @Override
      public void onDataMessageReceived(String message) {
//        Logger.d("DATA MESSAGE RECEIVED: %s", message);

        try {
          JSONObject obj = new JSONObject(message);
          if(obj.has("event") && obj.has("status")) {
            if(obj.getString("event").equals(RECEIVER_EVENTS.PLAYER_STATUS_CHANGED)) {
              Logger.d("playerStatusChanged: %s", obj.getString("status"));
              PlayerState oldState = currentState;
              currentState = PlayerState.getState(obj.getString("status"));
              Logger.d("current state: %s", currentState);

              if(listener != null && oldState != currentState)
                listener.onStateChanged(nowPlayingMedia, currentState);
              if(currentState != PlayerState.STOPPED) {
                VoiceControlForPlexApplication.getInstance().setNotification(mClient, currentState, nowPlayingMedia);
              }
            }
          } else if(obj.has("event") && obj.getString("event").equals(RECEIVER_EVENTS.TIME_UPDATE) && obj.has("currentTime")) {
            position = obj.getInt("currentTime");
            if(listener != null)
              listener.onTimeUpdate(currentState, obj.getInt("currentTime"));
          } else if(obj.has("event") && obj.getString("event").equals(RECEIVER_EVENTS.PLAYLIST_ADVANCE) && obj.has("media") && obj.has("type")) {
            Logger.d("[CastPlayerManager] playlistAdvance");
            if(obj.getString("type").equals(PARAMS.MEDIA_TYPE_VIDEO))
              nowPlayingMedia = VoiceControlForPlexApplication.gsonRead.fromJson(obj.getString("media"), PlexVideo.class);
            else
              nowPlayingMedia = VoiceControlForPlexApplication.gsonRead.fromJson(obj.getString("media"), PlexTrack.class);
            if(listener != null)
              listener.onMediaChanged(nowPlayingMedia, PlayerState.PLAYING);
          } else if(obj.has("event") && obj.getString("event").equals(RECEIVER_EVENTS.GET_PLAYBACK_STATE) && obj.has("state")) {
            Logger.d("Got playback state back: %s", obj.getString("state"));
            PlayerState oldState = currentState;
            currentState = PlayerState.getState(obj.getString("state"));
            if(obj.has("media") && obj.has("type") && obj.has("client")) {
              if(obj.getString("type").equals(PARAMS.MEDIA_TYPE_VIDEO))
                nowPlayingMedia = VoiceControlForPlexApplication.gsonRead.fromJson(obj.getString("media"), PlexVideo.class);
              else
                nowPlayingMedia = VoiceControlForPlexApplication.gsonRead.fromJson(obj.getString("media"), PlexTrack.class);
              mClient = VoiceControlForPlexApplication.gsonRead.fromJson(obj.getString("client"), PlexClient.class);
            }
            if(listener != null && oldState != currentState) {
              if(oldState == PlayerState.STOPPED)
                listener.onPlayStarted(nowPlayingMedia, currentState);
              else
                listener.onStateChanged(nowPlayingMedia, PlayerState.getState(obj.getString("state")));
            }
            if(currentState != PlayerState.STOPPED) {
              VoiceControlForPlexApplication.getInstance().setNotification(mClient, currentState, nowPlayingMedia);
            } else
              VoiceControlForPlexApplication.getInstance().cancelNotification();
          } else if(obj.has("event") && obj.getString("event").equals(RECEIVER_EVENTS.DEVICE_CAPABILITIES) && obj.has("capabilities")) {
            Capabilities capabilities = VoiceControlForPlexApplication.gsonRead.fromJson(obj.getString("capabilities"), Capabilities.class);
            mClient.isAudioOnly = !capabilities.displaySupported;

//            if(listener != null)
//              listener.onGetDeviceCapabilities(capabilities);
          } else if(obj.has("event") && obj.getString("event").equals(RECEIVER_EVENTS.SHUTDOWN)) {
            if(listener != null)
              listener.onUnsubscribed();
            subscribed = false;
            mClient = null;
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
        if(onConnectedRunnable != null)
          onConnectedRunnable.run();
      }

      @Override
      public void onConnectivityRecovered() {

      }

      @Override
      public void onApplicationStatusChanged(String appStatus) {
        Logger.d("CastPlayerManager onApplicationStatusChanged: %s", appStatus);
      }

      @Override
      public void onApplicationConnectionFailed(int errorCode) {
        Logger.d("[CastPlayerManager] onApplicationConnectionFailed: %d", errorCode);
        // TODO: handle error properly
        if(listener != null)
          listener.onSubscribeError("");
      }

      @Override
      public void onVolumeChanged(double value, boolean isMute) {
        super.onVolumeChanged(value, isMute);
        Logger.d("Volume is now %s", Double.toString(value));
        volume = value;
      }

      @Override
      public void onCastDeviceDetected(final MediaRouter.RouteInfo info) {
        Logger.d("onCastDeviceDetected: %s", info);
      }
    };
  }

  public double getVolume() {
    return volume;
  }

  public void setVolume(double v) {
    JSONObject obj = new JSONObject();
    try {
      obj.put(PARAMS.ACTION, PARAMS.ACTION_SET_VOLUME);
      obj.put(PARAMS.VOLUME, v);
      sendMessage(obj);
      volume = v;
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  public String getTranscodeUrl(PlexMedia media, Connection connection, int offset) {
    return getTranscodeUrl(media, connection, offset, false);
  }

  public String getTranscodeUrl(PlexMedia media, Connection connection, int offset, boolean subtitles) {
    Logger.d("getTranscodeUrl, offset: %d", offset);
    String url = connection.uri;
    url += String.format("/%s/:/transcode/universal/%s?", (media instanceof PlexVideo ? "video" : "audio"), (subtitles ? "subtitles" : "start"));
    QueryString qs = new QueryString("path", String.format("http://127.0.0.1:32400%s", media.key));
    qs.add("mediaIndex", "0");
    qs.add("partIndex", "0");

    qs.add("subtitles", "auto");
    qs.add("copyts", "1");
    qs.add("subtitleSize", "100");
    qs.add("Accept-Language", "en");
    qs.add("X-Plex-Client-Profile-Extra", "");
    qs.add("X-Plex-Chunked", "1");
    qs.add("X-Plex-Username", "atomjack");

    qs.add("protocol", "http");
    qs.add("offset", Integer.toString(offset));
    qs.add("fastSeek", "1");
//    String[] videoQuality = VoiceControlForPlexApplication.chromecastVideoOptions.get(VoiceControlForPlexApplication.getInstance().prefs.getString(connection.local ? Preferences.CHROMECAST_VIDEO_QUALITY_LOCAL : Preferences.CHROMECAST_VIDEO_QUALITY_REMOTE));
//    qs.add("directPlay", videoQuality.length == 3 && videoQuality[2] == "1" ? "1" : "0");
    qs.add("directPlay", "0");
    qs.add("directStream", "1");
    qs.add("videoQuality", "60");
    qs.add("maxVideoBitrate", VoiceControlForPlexApplication.chromecastVideoOptions.get(VoiceControlForPlexApplication.getInstance().prefs.getString(connection.local ? Preferences.CHROMECAST_VIDEO_QUALITY_LOCAL : Preferences.CHROMECAST_VIDEO_QUALITY_REMOTE))[0]);
    qs.add("videoResolution", VoiceControlForPlexApplication.chromecastVideoOptions.get(VoiceControlForPlexApplication.getInstance().prefs.getString(connection.local ? Preferences.CHROMECAST_VIDEO_QUALITY_LOCAL : Preferences.CHROMECAST_VIDEO_QUALITY_REMOTE))[1]);
    qs.add("audioBoost", "100");
    qs.add("session", plexSessionId);
    qs.add(PlexHeaders.XPlexClientIdentifier, VoiceControlForPlexApplication.getInstance().prefs.getUUID());
    qs.add(PlexHeaders.XPlexProduct, String.format("%s Chromecast", VoiceControlForPlexApplication.getInstance().getString(R.string.app_name)));
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

  public JSONObject buildMedia(Connection connection) {
    return buildMedia(connection, 0);
  }

  public JSONObject buildMedia(Connection connection, int offset) {
    JSONObject data = new JSONObject();
    try {
      data.put(PARAMS.ACTION, PARAMS.ACTION_LOAD);
      if(VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.PLEX_USERNAME) != null) {
        data.put(PARAMS.PLEX_USERNAME, VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.PLEX_USERNAME));
      }
      data.put(PARAMS.MEDIA_TYPE, nowPlayingMedia instanceof PlexVideo ? PARAMS.MEDIA_TYPE_VIDEO : PARAMS.MEDIA_TYPE_AUDIO);
      data.put(PARAMS.RESUME, VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.RESUME, false));
      data.put(PARAMS.CLIENT, VoiceControlForPlexApplication.gsonWrite.toJson(mClient));
      data.put(PARAMS.SESSION_ID, plexSessionId);
      Logger.d("[CastPlayerManager] setting src to %s", getTranscodeUrl(nowPlayingMedia, connection, offset));
      data.put(PARAMS.SRC, getTranscodeUrl(nowPlayingMedia, connection, offset));
      if(nowPlayingMedia instanceof PlexVideo) {
        data.put(PARAMS.SUBTITLE_SRC, getTranscodeUrl(nowPlayingMedia, connection, offset, true));
        data.put(PARAMS.AUDIO_STREAMS, VoiceControlForPlexApplication.gsonWrite.toJson(nowPlayingMedia.getStreams(Stream.AUDIO)));
        data.put(PARAMS.SUBTITLE_STREAMS, VoiceControlForPlexApplication.gsonWrite.toJson(nowPlayingMedia.getStreams(Stream.SUBTITLE)));
        if(nowPlayingMedia.getActiveStream(Stream.SUBTITLE) != null)
          data.put(PARAMS.ACTIVE_SUBTITLE, nowPlayingMedia.getActiveStream(Stream.SUBTITLE).id);
      }
      data.put(PARAMS.ACCESS_TOKEN, nowPlayingMedia.server.accessToken);
      data.put(PARAMS.PLAYLIST, getPlaylistJson());
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return data;
  }

  private String getPlaylistJson() {
    return VoiceControlForPlexApplication.gsonWrite.toJson(nowPlayingAlbum);
  }

  public PlayerState getCurrentState() {
    return currentState;
  }

  public void setNowPlayingMedia(PlexMedia nowPlayingMedia) {
    this.nowPlayingMedia = nowPlayingMedia;
  }

  public PlexMedia getNowPlayingMedia() {
    return nowPlayingMedia;
  }

  public List<? extends PlexMedia> getNowPlayingAlbum() {
    return nowPlayingAlbum;
  }

  public String getSessionId() {
    return mSessionId;
  }

  public void setActiveStream(final Stream stream) {
    nowPlayingMedia.server.findServerConnection(new ActiveConnectionHandler() {
      @Override
      public void onSuccess(Connection connection) {
        JSONObject obj = new JSONObject();
        try {
          obj.put(PARAMS.ACTION, PARAMS.ACTION_SET_STREAM);
          obj.put(PARAMS.STREAM_TYPE, stream.streamType);
          obj.put(PARAMS.STREAM_ID, stream.id);
          plexSessionId = VoiceControlForPlexApplication.generateRandomString();
          obj.put(PARAMS.SESSION_ID, plexSessionId);
          obj.put(PARAMS.SRC, getTranscodeUrl(nowPlayingMedia, connection, Integer.parseInt(nowPlayingMedia.viewOffset) / 1000));
          obj.put(PARAMS.SUBTITLE_SRC, getTranscodeUrl(nowPlayingMedia, connection, Integer.parseInt(nowPlayingMedia.viewOffset) / 1000, true));
          sendMessage(obj);
        } catch (Exception ex) {
        }
      }

      @Override
      public void onFailure(int statusCode) {

      }
    });

  }

  public void cycleStreams(int streamType) {
    Stream newStream = nowPlayingMedia.getNextStream(streamType);
    mClient.setStream(newStream);
    nowPlayingMedia.setActiveStream(newStream);
  }

  public void subtitlesOn() {
    mClient.setStream(nowPlayingMedia.getStreams(Stream.SUBTITLE).get(1));
    nowPlayingMedia.setActiveStream(nowPlayingMedia.getStreams(Stream.SUBTITLE).get(1));
  }

  public void subtitlesOff() {
    mClient.setStream(nowPlayingMedia.getStreams(Stream.SUBTITLE).get(0));
    nowPlayingMedia.setActiveStream(nowPlayingMedia.getStreams(Stream.SUBTITLE).get(0));
  }

  public int getPosition() {
    return position;
  }
}
