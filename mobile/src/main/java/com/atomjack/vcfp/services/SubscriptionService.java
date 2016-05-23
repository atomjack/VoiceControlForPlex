package com.atomjack.vcfp.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v7.media.MediaRouter;

import com.atomjack.shared.NewLogger;
import com.atomjack.shared.PlayerState;
import com.atomjack.shared.Preferences;
import com.atomjack.shared.model.Timeline;
import com.atomjack.vcfp.BuildConfig;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.QueryString;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VCFPCastConsumer;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.exceptions.UnauthorizedException;
import com.atomjack.vcfp.interfaces.ActiveConnectionHandler;
import com.atomjack.vcfp.interfaces.PlexSubscriptionListener;
import com.atomjack.vcfp.model.Capabilities;
import com.atomjack.vcfp.model.Connection;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexResponse;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.model.PlexVideo;
import com.atomjack.vcfp.model.Stream;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpMediaContainerHandler;
import com.atomjack.vcfp.net.PlexHttpResponseHandler;
import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.libraries.cast.companionlibrary.cast.CastConfiguration;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit.Call;
import retrofit.Callback;
import retrofit.Response;
import retrofit.Retrofit;

public class SubscriptionService extends Service {
  public static final String CLIENT = "com.atomjack.vcfp.client";
  public static final String MEDIA = "com.atomjack.vcfp.media";
  public static final String PLAYLIST = "com.atomjack.vcfp.playlist";

  // Common variables
  private NewLogger logger;
  private boolean subscribed = false;
  private boolean subscribing = false;
  public PlexClient client;
  private PlexMedia nowPlayingMedia;
  private ArrayList<? extends PlexMedia> nowPlayingPlaylist;
  private PlexSubscriptionListener plexSubscriptionListener;
  private final IBinder subBind = new SubscriptionBinder();
  private PlayerState currentState = PlayerState.STOPPED;
  int position = 0;

  // Plex client specific variables
  private static final int SUBSCRIBE_INTERVAL = 30000; // Send subscribe message every 30 seconds to keep us alive
  private ServerSocket serverSocket;
  Thread serverThread = null;
  Handler updateConversationHandler;
  private int subscriptionPort = 59409;
  private static Serializer serial = new Persister();
  private int commandId = 1;
  private int failedHeartbeats = 0;
  private final int failedHeartbeatMax = 5;
  public Date timeLastHeardFromClient;
  private Calendar lastHeartbeatResponded;
  private Handler mHandler = new Handler();
  private Timeline currentTimeline;

  // Chromecast specific variables
  private static VideoCastManager castManager = null;
  private VCFPCastConsumer castConsumer;
  private String mSessionId;
  private String plexSessionId;
  private double volume = 1.0;
  private String transientToken;

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
    public static final String ACTIVE_CONNECTIONS = "active_connections";

    public static final String PLAYBACK_LIMITED = "playback_limited"; // Whether or not playback should stop after 1 minute

  };

  public static final class RECEIVER_EVENTS {
    public static final String PLAYLIST_ADVANCE = "playlistAdvance";
    public static final String PLAYER_STATUS_CHANGED = "playerStatusChanged";
    public static final String GET_PLAYBACK_STATE = "getPlaybackState";
    public static final String TIME_UPDATE = "timeUpdate";
    public static final String DEVICE_CAPABILITIES = "deviceCapabilities";
    public static final String SHUTDOWN = "shutdown";
  }
  /*
  PlexSubscriptionListener interface:
  void onSubscribed(PlexClient client, boolean showFeedback);
  void onUnsubscribed();
  void onTimeUpdate(PlayerState state, int seconds);
  void onMediaChanged(PlexMedia media, PlayerState state);
  void onStateChanged(PlexMedia media, PlayerState state);
  void onPlayStarted(PlexMedia media, ArrayList<? extends PlexMedia> playlist, PlayerState state);
  void onSubscribeError(String message);
   */

  public SubscriptionService() {
    logger = new NewLogger(this);
    plexSessionId = VoiceControlForPlexApplication.generateRandomString();
    setCastConsumer();
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return subBind;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    logger.d("onStartCommand: %s", intent != null ? intent.getAction() : "no intent");
    if(intent == null) {
      // Service was restarted after being destroyed by the system
    } else {
      String action = intent.getAction();
      if(action != null) {
        if (intent.getAction().equals(com.atomjack.shared.Intent.ACTION_PLAY)) {
          play();
        } else if (intent.getAction().equals(com.atomjack.shared.Intent.ACTION_PAUSE)) {
          pause();
        } else if (intent.getAction().equals(com.atomjack.shared.Intent.ACTION_STOP)) {
          stop();
        } else if (intent.getAction().equals(com.atomjack.shared.Intent.ACTION_REWIND)) {
          rewind();
        } else if (intent.getAction().equals(com.atomjack.shared.Intent.ACTION_PREVIOUS)) {
          previous();
        } else if (intent.getAction().equals(com.atomjack.shared.Intent.ACTION_FORWARD)) {
          forward();
        } else if (intent.getAction().equals(com.atomjack.shared.Intent.ACTION_NEXT)) {
          next();
        } else if (intent.getAction().equals(com.atomjack.shared.Intent.ACTION_DISCONNECT)) {
          unsubscribe();
        }
      }
    }



    return Service.START_STICKY;
  }

  public class SubscriptionBinder extends Binder {
    public SubscriptionService getService() {
      return SubscriptionService.this;
    }

  }

  public void setListener(PlexSubscriptionListener listener) {
    plexSubscriptionListener = listener;
  }

  public boolean isSubscribed() {
    return subscribed;
  }

  public boolean isSubscribing() { return subscribing; }

  /*
  public void subscribe(PlexClient client) {
    if(client.isCastClient) {
      subscribe(client, null, true);
    } else if(client.isLocalClient) {

    } else {
      startSubscription(client, true);
    }
  }
  */

  public PlexClient getClient() {
    return client;
  }

  public PlayerState getCurrentState() {
    return currentState;
  }

  public PlexMedia getNowPlayingMedia() {
    return nowPlayingMedia;
  }

  public int getPosition() {
    return position;
  }

  // Playback methods
  public void play() {
    if(client.isCastClient) {
      sendMessage(PARAMS.ACTION_PLAY);
    } else {
      client.play();
    }
  }

  public void pause() {
    if(client.isCastClient) {
      sendMessage(PARAMS.ACTION_PAUSE);
    } else {
      client.pause();
    }
  }

  public void stop() {
    if (client.isCastClient) {
      sendMessage(PARAMS.ACTION_STOP);
    } else {
      client.stop();
    }
  }

  public void rewind() {
    if (client.isCastClient) {
      // null
    } else {
      client.seekTo((position*1000) - 15000);
    }
  }

  public void forward() {
    if (client.isCastClient) {
      // null
    } else {
      client.seekTo((position*1000) + 30000);
    }
  }

  public void previous() {
    if (client.isCastClient) {
      sendMessage(PARAMS.ACTION_PREV);
    } else {
      client.previous(null);
    }
  }

  public void next() {
    if (client.isCastClient) {
      sendMessage(PARAMS.ACTION_NEXT);
    } else {
      client.next(null);
    }
  }

  public void seekTo(int seconds) {
    if (client.isCastClient) {
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
            plexSubscriptionListener.onTimeUpdate(currentState, seconds);
            position = seconds;
          } catch (Exception ex) {
          }
        }

        @Override
        public void onFailure(int statusCode) {
          // TODO: Handle failure
        }
      });
    } else {
      client.seekTo((seconds*1000) + 30000);
    }
  }

  public void setStream(Stream stream) {
    if(client.isCastClient) {
      setActiveStream(stream);
    } else if(!client.isLocalClient) {
      PlexHttpClient.PlexHttpService service = PlexHttpClient.getService(String.format("http://%s:%s", client.address, client.port));
      HashMap<String, String> qs = new HashMap<>();
      if (stream.streamType == Stream.AUDIO) {
        qs.put("audioStreamID", stream.id);
      } else if (stream.streamType == Stream.SUBTITLE) {
        qs.put("subtitleStreamID", stream.id);
      }
      Call<PlexResponse> call = service.setStreams(qs, "0", VoiceControlForPlexApplication.getInstance().getUUID());
      call.enqueue(new Callback<PlexResponse>() {
        @Override
        public void onResponse(Response<PlexResponse> response, Retrofit retrofit) {
          if(response.body() != null)
            logger.d("setStream response: %s", response.body().status);
        }

        @Override
        public void onFailure(Throwable t) {

        }
      });

    }
  }

  public void cycleStreams(int streamType) {
    Stream newStream = nowPlayingMedia.getNextStream(streamType);
    setStream(newStream);
    nowPlayingMedia.setActiveStream(newStream);
  }

  public void subtitlesOn() {
    setStream(nowPlayingMedia.getStreams(Stream.SUBTITLE).get(1));
    nowPlayingMedia.setActiveStream(nowPlayingMedia.getStreams(Stream.SUBTITLE).get(1));
  }

  public void subtitlesOff() {
    setStream(nowPlayingMedia.getStreams(Stream.SUBTITLE).get(0));
    nowPlayingMedia.setActiveStream(nowPlayingMedia.getStreams(Stream.SUBTITLE).get(0));
  }
  // end Playback methods

  public void unsubscribe() {
    unsubscribe(null);
  }

  public void unsubscribe(final Runnable onFinish) {
    unsubscribe(true, onFinish);
  }

  public void unsubscribe(final boolean notify, final Runnable onFinish) {
    if(client == null) {
      onUnsubscribed();
      return;
    }
    if(client.isLocalClient) {
      subscribed = false;
      onUnsubscribed();
    } else if(client.isCastClient) {
      try {
        logger.d("is connected: %s", castManager.isConnected());
        if(castManager.isConnected()) {
          castManager.disconnect();
        }
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      subscribed = false;
      client = null;
      if(plexSubscriptionListener != null)
        plexSubscriptionListener.onUnsubscribed();
    } else {
      PlexHttpClient.unsubscribe(client, commandId, VoiceControlForPlexApplication.getInstance().prefs.getUUID(), VoiceControlForPlexApplication.getInstance().getString(R.string.app_name), new PlexHttpResponseHandler() {
        @Override
        public void onSuccess(PlexResponse response) {
          logger.d("[PlexSubscription] Unsubscribed");
          subscribed = false;
          VoiceControlForPlexApplication.getInstance().prefs.remove(Preferences.SUBSCRIBED_CLIENT);
          commandId++;
          client = null;
          mHandler.removeCallbacks(subscriptionHeartbeat);

          try {
            serverSocket.close();
            serverSocket = null;
          } catch (Exception ex) {
            logger.d("Exception attempting to close socket.");
            ex.printStackTrace();
          }
          if (notify)
            onUnsubscribed();
          if (onFinish != null)
            onFinish.run();
        }

        @Override
        public void onFailure(Throwable error) {
          logger.d("failure unsubscribing");
          subscribed = false;
          mHandler.removeCallbacks(subscriptionHeartbeat);

          try {
            serverSocket.close();
            serverSocket = null;
          } catch (Exception ex) {
            logger.d("Exception attempting to close socket due to failed unsubscribe.");
            ex.printStackTrace();
          }

          onUnsubscribed();
        }
      });
    }

  }

  // Plex client specific methods

  public void checkLastHeartbeat() {
    if(subscribed) {
      Calendar cal = Calendar.getInstance();
      SimpleDateFormat format = new SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm:ss a");
      cal.add(Calendar.SECOND, -60);
      if (lastHeartbeatResponded != null && lastHeartbeatResponded.before(cal)) {
        logger.d("It's been more than 60 seconds since last heartbeat responded. now: %s, last heartbeat responded: %s", format.format(Calendar.getInstance().getTime()), format.format(lastHeartbeatResponded.getTime()));
        if (client != null)
          subscribe(client, true);
      }
    }
  }

  public void subscribe(final PlexClient client, boolean showFeedback) {
    subscribing = true;
    if(client.isLocalClient) {
      subscribed = true;
      subscribing = false;
      this.client = client;
      onSubscribed(showFeedback);
    } else if(client.isCastClient) {
      subscribeToChromecast(client, null, showFeedback);
    } else {
      logger.d("PlexSubscription subscribe: %s, handler is null: %s", client, updateConversationHandler == null);
      if (updateConversationHandler == null)
        startSubscription(client, showFeedback);
      else
        subscribe(client, false, showFeedback);
    }
  }

  public void subscribe(PlexClient client, final boolean isHeartbeat, final boolean showFeedback) {
    if(client == null)
      return;
    this.client = client;

    PlexHttpClient.subscribe(client, subscriptionPort, commandId, VoiceControlForPlexApplication.getInstance().getUUID(), VoiceControlForPlexApplication.getInstance().getString(R.string.app_name), new PlexHttpResponseHandler() {
      @Override
      public void onSuccess(PlexResponse response) {
        subscribing = false;
        failedHeartbeats = 0;
        if(!isHeartbeat)
          logger.d("PlexSubscription: Subscribed: %s, Code: %d", response != null ? response.status : "", response.code);
        else
          logger.d("PlexSubscription: Heartbeat: %s, Code: %d", response != null ? response.status : "", response.code);

        if(response.code != 200) {
          this.onFailure(new Throwable(response.status));
          // Close the server socket so it's no longer listening on the subscriptionPort
          try {
            serverSocket.close();
            serverSocket = null;
          } catch (Exception e) {}
        } else {
          timeLastHeardFromClient = new Date();

          commandId++;
          subscribed = true;

          if (!isHeartbeat) {
            // Start the heartbeat subscription (so the plex client knows we're still here)
            mHandler.removeCallbacks(subscriptionHeartbeat);
            mHandler.postDelayed(subscriptionHeartbeat, SUBSCRIBE_INTERVAL);
            onSubscribed(showFeedback);
          } else {
            lastHeartbeatResponded = Calendar.getInstance();
          }
        }
      }

      @Override
      public void onFailure(final Throwable error) {
        error.printStackTrace();
        subscribing = false;
        if(isHeartbeat) {
          failedHeartbeats++;
          logger.d("%d failed heartbeats", failedHeartbeats);
          if(failedHeartbeats >= failedHeartbeatMax) {
            logger.d("Unsubscribing due to failed heartbeats");
            // Since several heartbeats in a row failed, set ourselves as unsubscribed and notify any listeners that we're no longer subscribed. Don't
            // bother trying to actually unsubscribe since we probably can't rely on the client to respond at this point
            //
            subscribed = false;
            onUnsubscribed();
            mHandler.removeCallbacks(subscriptionHeartbeat);

            mHandler.post(() -> {
              VoiceControlForPlexApplication.getInstance().cancelNotification();
              if(plexSubscriptionListener != null) {
                plexSubscriptionListener.onSubscribeError(String.format(VoiceControlForPlexApplication.getInstance().getString(R.string.client_lost_connection), client.name));
              }
            });
          }
        } else {
          mHandler.post(() -> {
            if(plexSubscriptionListener != null) {
              plexSubscriptionListener.onSubscribeError(error.getMessage());
            }
            VoiceControlForPlexApplication.getInstance().cancelNotification();
          });
        }
      }
    });

  }

  private void onSubscribed(final boolean showFeedback) {
    mHandler.post(() -> {
      logger.d("PlexSubscription onSubscribed, client: %s", client);

      if(plexSubscriptionListener != null)
        plexSubscriptionListener.onSubscribed(client, showFeedback);
    });
  }

  private void onUnsubscribed() {
    VoiceControlForPlexApplication.getInstance().cancelNotification();
    nowPlayingMedia = null;
    mHandler.post(() -> {
      if (plexSubscriptionListener != null)
        plexSubscriptionListener.onUnsubscribed();
    });
  }

  private Runnable subscriptionHeartbeat = new Runnable() {
    @Override
    public void run() {
      if(subscribed) {
        if(failedHeartbeats == 0) {
          subscribe(client, true, false);
          mHandler.postDelayed(subscriptionHeartbeat, SUBSCRIBE_INTERVAL);
        }
      } else {
        logger.d("stopping subscription heartbeat because we are not subscribed anymore");
      }
    }
  };

  public synchronized void startSubscription(final PlexClient client, final boolean showFeedback) {
    logger.d("startSubscription: %s", updateConversationHandler);
    if(updateConversationHandler == null) {
      updateConversationHandler = new Handler();
    }
    ServerThread thread = new ServerThread();
    thread.onReady(() -> {
      logger.d("subscribing");
      subscribe(client, showFeedback);
    });

    serverThread = new Thread(thread);
    serverThread.start();
  }

  class ServerThread implements Runnable {
    Runnable onReady;

    private void onReady(Runnable runme) {
      onReady = runme;
    }

    private Runnable onSocketReady = new Runnable() {
      @Override
      public void run() {
        boolean closed = serverSocket.isClosed();

        if(!closed) {
          onReady.run();
        } else {
          new Handler().postDelayed(onSocketReady, 1000);
        }
      }
    };

    public void run() {
      logger.d("starting serverthread");
      Socket socket = null;
      try {
        if(serverSocket != null) {
          serverSocket.close();
        }
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(subscriptionPort));
//        subscriptionPort = serverSocket.getLocalPort();
      } catch (IOException e) {

        e.printStackTrace();
      }

      logger.d("running");



      onSocketReady.run();

      while (!Thread.currentThread().isInterrupted()) {
//      while(true) {
        try {
          if (serverSocket == null)
            return;
          socket = serverSocket.accept();

          Map<String, String> headers = new HashMap<String, String>();
          String line;
          Pattern p = Pattern.compile("^([^:]+): (.+)$");
          Matcher matcher;
          BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
          while ((line = reader.readLine()) != null) {
            matcher = p.matcher(line);
            if (matcher.find()) {
              headers.put(matcher.group(1), matcher.group(2));
            }
            if (line.equals("")) {
              break; // and don't get the next line!
            }
          }
          int contentLength = Integer.parseInt(headers.get("Content-Length"));

          StringBuilder requestContent = new StringBuilder();
          for (int i = 0; i < contentLength; i++) {
            requestContent.append((char) reader.read());
          }

					/*
					    <Timeline address="x.x.x.x" audioStreamID="158"
					    containerKey="/library/metadata/14"
					    controllable="playPause,stop,shuffle,repeat,volume,stepBack,stepForward,seekTo,subtitleStream,audioStream"
					    duration="9266976" guid="com.plexapp.agents.imdb://tt0090605?lang=en"
					    key="/library/metadata/14" location="fullScreenVideo"
					    machineIdentifier="xxxxxx" mute="0" playQueueItemID="14"
					    port="32400" protocol="http" ratingKey="14" repeat="0" seekRange="0-9266976" shuffle="0"
					    state="playing" subtitleStreamID="-1" time="4087" type="video" volume="1" />
					 */

          String xml = requestContent.toString();
//          logger.d("xml: %s", xml);

          MediaContainer mediaContainer = new MediaContainer();

          try {
            mediaContainer = serial.read(MediaContainer.class, xml);
          } catch (Resources.NotFoundException e) {
            e.printStackTrace();
          } catch (Exception e) {
            e.printStackTrace();
          }

          onMessage(mediaContainer);


          // Send a response
          String response = "Failure: 200 OK";
          PrintStream output = new PrintStream(socket.getOutputStream());
          output.flush();
          output.println("HTTP/1.1 200 OK");
          output.println("Content-Type: text/plain; charset=UTF-8");
          output.println("Access-Control-Allow-Origin: *");
          output.println("Access-Control-Max-Age: 1209600");
          output.println("");
          output.println(response);

          output.close();
          reader.close();
        } catch (SocketException se) {
          if(se.getMessage().equals("Socket closed")) {
            updateConversationHandler = null;
          }
//          se.printStackTrace();
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          try {
            if(socket != null) {
              socket.close();
            }
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        }
      }
    }
  }

  private void onMessage(final MediaContainer mediaContainer) {
    mHandler.post(() -> {
      List<Timeline> timelines = mediaContainer.timelines;
      if(timelines != null) {
        for (final Timeline timeline : timelines) {
          if (timeline.key != null) {
            currentTimeline = timeline;
            if(timeline.state == null)
              timeline.state = "stopped";

            PlexServer server = null;
            for(PlexServer s : VoiceControlForPlexApplication.servers.values()) {
              if(s.machineIdentifier.equals(timeline.machineIdentifier)) {
                server = s;
                break;
              }
            }

            if(server == null) {
              unsubscribe(() -> {
                if(plexSubscriptionListener != null)
                  plexSubscriptionListener.onSubscribeError(null);
              });
            }
            final String serverName = server.name;

            PlayerState oldState = currentState;
            currentState = PlayerState.getState(timeline);
            position = timeline.time/1000;

            // If we don't currently have now playing media, or the media has changed, update nowPlayingMedia and call the appropriate listener method (onMediaChanged, or onPlayStarted)
            if((nowPlayingMedia == null || (nowPlayingMedia != null && !nowPlayingMedia.key.equals(timeline.key))) && currentState != PlayerState.STOPPED) {
              PlexHttpClient.get(server, timeline.containerKey, new PlexHttpMediaContainerHandler() {
                @Override
                public void onSuccess(MediaContainer playlistMediaContainer) {
                  PlexMedia media = null;
                  if(playlistMediaContainer.tracks.size() > 0) {
                    nowPlayingPlaylist = playlistMediaContainer.tracks;
                    for(PlexTrack t : playlistMediaContainer.tracks) {
                      if(t.key.equals(timeline.key)) {
                        media = t;
                        break;
                      }
                    }
                  } else if(playlistMediaContainer.videos.size() > 0) {
                    nowPlayingPlaylist = playlistMediaContainer.videos;
                    for(PlexVideo m : playlistMediaContainer.videos) {
                      if(m.key.equals(timeline.key)) {
                        media = m;
                        if(m.isClip())
                          m.setClipDuration();
                        break;
                      }
                    }
                  }
                  if(media != null) {
                    if(plexSubscriptionListener != null) {
                      if (nowPlayingMedia != null) // if we're already playing media, this new media we found is different, so notify the listener
                        plexSubscriptionListener.onMediaChanged(media, PlayerState.getState(timeline));
                      else if (currentState != PlayerState.STOPPED) { // edge case where we receive a new timeline with a state of stopped after this one, but before this one has finished processing
                        plexSubscriptionListener.onPlayStarted(media, nowPlayingPlaylist, PlayerState.getState(timeline));
                      }
                    }
                  } else {
                    // TODO: Handle not finding any media?
                  }
                  nowPlayingMedia = media;
                  if(currentState != PlayerState.STOPPED)
                    VoiceControlForPlexApplication.getInstance().setNotification(client, currentState, nowPlayingMedia, nowPlayingPlaylist);
                }

                @Override
                public void onFailure(Throwable error) {
                  unsubscribe(() -> {
                    if(plexSubscriptionListener != null)
                      plexSubscriptionListener.onSubscribeError(error instanceof UnauthorizedException ? String.format(getString(R.string.server_unauthorized), serverName) : null);
                  });
                }
              });
            } else {
              if(oldState != currentState) {
                // State has changed
                if(plexSubscriptionListener != null)
                  plexSubscriptionListener.onStateChanged(nowPlayingMedia, currentState);
                if(currentState == PlayerState.STOPPED) {
                  nowPlayingMedia = null;
                  VoiceControlForPlexApplication.getInstance().cancelNotification();
                } else {
                  VoiceControlForPlexApplication.getInstance().setNotification(client, currentState, nowPlayingMedia, nowPlayingPlaylist);
                }
              } else {
                // State has not changed, so alert listener of the current timecode
                if(plexSubscriptionListener != null && currentState != PlayerState.STOPPED)
                  plexSubscriptionListener.onTimeUpdate(currentState, timeline.time/1000); // timecode in Timeline is in ms
              }
            }
          }
        }
      }
    });
  }


  // Chromecast client specific methods

  public void subscribeToChromecast(PlexClient _client, Runnable onFinished, boolean showFeedback) {
    if(castManager == null) {
      logger.d("creating castManager");
      castManager = getCastManager(this);
      castManager.addVideoCastConsumer(castConsumer);
      castManager.incrementUiCounter();
    }
    if(castManager.isConnected()) {
      castManager.disconnect();
    }
    logger.d("selecting device: %s", _client.castDevice);
    castManager.onDeviceSelected(_client.castDevice, null);
    logger.d("device selected");
    castConsumer.setOnConnected(() -> {
      client = _client;

//        currentState = castManager.getPlaybackStatus();
      logger.d("castConsumer connected to %s", client.name);
      sendMessage(PARAMS.ACTION_GET_PLAYBACK_STATE);

      //
      JSONObject obj = new JSONObject();
      try {
        obj.put(PARAMS.ACTION, PARAMS.RECEIVE_SERVERS);
        if(!VoiceControlForPlexApplication.getInstance().hasChromecast())
          obj.put(PARAMS.PLAYBACK_LIMITED, true);
        Type serverType = new TypeToken<ConcurrentHashMap<String, PlexServer>>(){}.getType();
        PlexServer server = VoiceControlForPlexApplication.gsonRead.fromJson(VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.SERVER, ""), PlexServer.class);
        if(server.name.equals(getString(R.string.scan_all)))
          obj.put(PARAMS.SERVERS, VoiceControlForPlexApplication.gsonWrite.toJson(VoiceControlForPlexApplication.getInstance().servers, serverType));
        else {
          ConcurrentHashMap<String, PlexServer> map = new ConcurrentHashMap<>();
          map.put(server.name, server);
          obj.put(PARAMS.SERVERS, VoiceControlForPlexApplication.gsonWrite.toJson(map, serverType));
        }

        // Send all the active connections
        Type conType = new TypeToken<HashMap<String, Connection>>() {}.getType();
        obj.put(PARAMS.ACTIVE_CONNECTIONS, VoiceControlForPlexApplication.gsonWrite.toJson(VoiceControlForPlexApplication.getInstance().getActiveConnectionList(), conType));

        sendMessage(obj);
      } catch (Exception ex) {}


      subscribing = false;
      subscribed = true;
      if(plexSubscriptionListener != null)
        plexSubscriptionListener.onSubscribed(_client, showFeedback);
      else
        logger.d("listener is null");

      if(onFinished != null)
        onFinished.run();
    });
  }

  private static VideoCastManager getCastManager(Context context) {
    if (null == castManager) {
      CastConfiguration options = new CastConfiguration.Builder(BuildConfig.CHROMECAST_APP_ID)
              .addNamespace("urn:x-cast:com.atomjack.vcfp")
              .build();
      VideoCastManager.initialize(context, options);
    }
//    castManager.setContext(context);
    castManager = VideoCastManager.getInstance();
    castManager.setStopOnDisconnect(false);
    return castManager;
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

  private void setCastConsumer() {
    castConsumer = new VCFPCastConsumer() {
      private Runnable onConnectedRunnable;

      @Override
      public void onApplicationDisconnected(int errorCode) {
        logger.d("onApplicationDisconnected: %d", errorCode);
//        super.onApplicationDisconnected(errorCode);
      }

      @Override
      public void onDataMessageReceived(String message) {
//        logger.d("DATA MESSAGE RECEIVED: %s", message);

        try {
          JSONObject obj = new JSONObject(message);
          if(obj.has("event") && obj.has("status")) {
            if(obj.getString("event").equals(RECEIVER_EVENTS.PLAYER_STATUS_CHANGED)) {
              logger.d("playerStatusChanged: %s", obj.getString("status"));
              PlayerState oldState = currentState;
              currentState = PlayerState.getState(obj.getString("status"));
              logger.d("current state: %s", currentState);

              if(plexSubscriptionListener != null && oldState != currentState)
                plexSubscriptionListener.onStateChanged(nowPlayingMedia, currentState);
              if(currentState != PlayerState.STOPPED) {
                VoiceControlForPlexApplication.getInstance().setNotification(client, currentState, nowPlayingMedia, nowPlayingPlaylist);
              }
            }
          } else if(obj.has("event") && obj.getString("event").equals(RECEIVER_EVENTS.TIME_UPDATE) && obj.has("currentTime")) {
            position = obj.getInt("currentTime");
            if(obj.has("currentState"))
              currentState = PlayerState.getState(obj.getString("currentState"));
            if(plexSubscriptionListener != null)
              plexSubscriptionListener.onTimeUpdate(currentState, position);
          } else if(obj.has("event") && obj.getString("event").equals(RECEIVER_EVENTS.PLAYLIST_ADVANCE) && obj.has("media") && obj.has("type")) {
            logger.d("playlistAdvance");
            if(obj.getString("type").equals(PARAMS.MEDIA_TYPE_VIDEO))
              nowPlayingMedia = VoiceControlForPlexApplication.gsonRead.fromJson(obj.getString("media"), PlexVideo.class);
            else
              nowPlayingMedia = VoiceControlForPlexApplication.gsonRead.fromJson(obj.getString("media"), PlexTrack.class);
            if(plexSubscriptionListener != null)
              plexSubscriptionListener.onMediaChanged(nowPlayingMedia, PlayerState.PLAYING);
          } else if(obj.has("event") && obj.getString("event").equals(RECEIVER_EVENTS.GET_PLAYBACK_STATE) && obj.has("state")) {
            PlayerState oldState = currentState;
            currentState = PlayerState.getState(obj.getString("state"));
            if(obj.has("media") && obj.has("type") && obj.has("client")) {
              if(obj.getString("type").equals(PARAMS.MEDIA_TYPE_VIDEO)) {
                nowPlayingMedia = VoiceControlForPlexApplication.gsonRead.fromJson(obj.getString("media"), PlexVideo.class);
                if(obj.has("playlist")) {
                  Type type = new TypeToken<ArrayList<PlexVideo>>() {}.getType();
                  nowPlayingPlaylist = VoiceControlForPlexApplication.gsonRead.fromJson(obj.getString("playlist"), type);
                }
              } else {
                if(obj.has("playlist")) {
                  Type type = new TypeToken<ArrayList<PlexTrack>>() {}.getType();
                  nowPlayingPlaylist = VoiceControlForPlexApplication.gsonRead.fromJson(obj.getString("playlist"), type);
                }
                nowPlayingMedia = VoiceControlForPlexApplication.gsonRead.fromJson(obj.getString("media"), PlexTrack.class);
              }

              client = VoiceControlForPlexApplication.gsonRead.fromJson(obj.getString("client"), PlexClient.class);
            }
            if(plexSubscriptionListener != null && oldState != currentState) {
              if(oldState == PlayerState.STOPPED)
                plexSubscriptionListener.onPlayStarted(nowPlayingMedia, nowPlayingPlaylist, currentState);
              else
                plexSubscriptionListener.onStateChanged(nowPlayingMedia, PlayerState.getState(obj.getString("state")));
            }
            if(currentState != PlayerState.STOPPED) {
              VoiceControlForPlexApplication.getInstance().setNotification(client, currentState, nowPlayingMedia, nowPlayingPlaylist);
            } else
              VoiceControlForPlexApplication.getInstance().cancelNotification();
          } else if(obj.has("event") && obj.getString("event").equals(RECEIVER_EVENTS.DEVICE_CAPABILITIES) && obj.has("capabilities")) {
            Capabilities capabilities = VoiceControlForPlexApplication.gsonRead.fromJson(obj.getString("capabilities"), Capabilities.class);
            client.isAudioOnly = !capabilities.displaySupported;

            // TODO: Implement this
//            if(listener != null)
//              listener.onGetDeviceCapabilities(capabilities);
          } else if(obj.has("event") && obj.getString("event").equals(RECEIVER_EVENTS.SHUTDOWN)) {
            if(plexSubscriptionListener != null)
              plexSubscriptionListener.onUnsubscribed();
            subscribed = false;
            client = null;
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
        logger.d("castConsumer failed: %d", statusCode);
      }

      @Override
      public void onConnectionSuspended(int cause) {
        logger.d("onConnectionSuspended() was called with cause: " + cause);
//					com.google.sample.cast.refplayer.utils.Utils.
//									showToast(VideoBrowserActivity.this, R.string.connection_temp_lost);
      }

      @Override
      public void onApplicationConnected(ApplicationMetadata appMetadata,
                                         String sessionId, boolean wasLaunched) {
        super.onApplicationConnected(appMetadata, sessionId, wasLaunched);
        logger.d("onApplicationConnected()");
        logger.d("metadata: %s", appMetadata);
        logger.d("sessionid: %s", sessionId);
        logger.d("was launched: %s", wasLaunched);
        mSessionId = sessionId;
        if(onConnectedRunnable != null)
          onConnectedRunnable.run();
      }

      @Override
      public void onConnectivityRecovered() {

      }

      @Override
      public void onApplicationStatusChanged(String appStatus) {
        logger.d("onApplicationStatusChanged: %s", appStatus);
      }

      @Override
      public void onApplicationConnectionFailed(int errorCode) {
        logger.d("onApplicationConnectionFailed: %d", errorCode);
        // TODO: handle error properly
        if(plexSubscriptionListener != null)
          plexSubscriptionListener.onSubscribeError("");
      }

      @Override
      public void onVolumeChanged(double value, boolean isMute) {
        super.onVolumeChanged(value, isMute);
        logger.d("Volume is now %s", Double.toString(value));
        volume = value;
      }

      @Override
      public void onCastDeviceDetected(final MediaRouter.RouteInfo info) {
        logger.d("onCastDeviceDetected: %s", info);
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
    logger.d("getTranscodeUrl, offset: %d", offset);
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
    qs.add("X-Plex-Username", VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.PLEX_USERNAME, ""));

    qs.add("protocol", "http");
    qs.add("offset", Integer.toString(offset));
    qs.add("fastSeek", "1");
//    String[] videoQuality = VoiceControlForPlexApplication.chromecastVideoQualityOptions.get(VoiceControlForPlexApplication.getInstance().prefs.getString(connection.local ? Preferences.CHROMECAST_VIDEO_QUALITY_LOCAL : Preferences.CHROMECAST_VIDEO_QUALITY_REMOTE));
//    qs.add("directPlay", videoQuality.length == 3 && videoQuality[2] == "1" ? "1" : "0");
    qs.add("directPlay", "0");
    qs.add("directStream", "1");
    qs.add("videoQuality", "60");
    qs.add("maxVideoBitrate", VoiceControlForPlexApplication.chromecastVideoQualityOptions.get(VoiceControlForPlexApplication.getInstance().prefs.getString(connection.local ? Preferences.CHROMECAST_VIDEO_QUALITY_LOCAL : Preferences.CHROMECAST_VIDEO_QUALITY_REMOTE))[0]);
    qs.add("videoResolution", VoiceControlForPlexApplication.chromecastVideoQualityOptions.get(VoiceControlForPlexApplication.getInstance().prefs.getString(connection.local ? Preferences.CHROMECAST_VIDEO_QUALITY_LOCAL : Preferences.CHROMECAST_VIDEO_QUALITY_REMOTE))[1]);
    qs.add("audioBoost", "100");
    qs.add("session", plexSessionId);
    qs.add(PlexHeaders.XPlexClientIdentifier, VoiceControlForPlexApplication.getInstance().prefs.getUUID());
    qs.add(PlexHeaders.XPlexProduct, String.format("%s Chromecast", VoiceControlForPlexApplication.getInstance().getString(R.string.app_name)));
    qs.add(PlexHeaders.XPlexDevice, client.castDevice.getModelName());
    qs.add(PlexHeaders.XPlexDeviceName, client.castDevice.getModelName());
    qs.add(PlexHeaders.XPlexPlatform, client.castDevice.getModelName());
    if(transientToken != null)
      qs.add(PlexHeaders.XPlexToken, transientToken);
    qs.add(PlexHeaders.XPlexPlatformVersion, "1.0");
    try {
      qs.add(PlexHeaders.XPlexVersion, getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    if(VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.PLEX_USERNAME) != null)
      qs.add(PlexHeaders.XPlexUsername, VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.PLEX_USERNAME));
    return url + qs.toString();
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

  // This will send a message to the cast device to load the passed in media
  public void loadMedia(PlexMedia media, ArrayList<? extends PlexMedia> album, final int offset) {
    logger.d("Loading media: %s", album);
    nowPlayingMedia = media;
    nowPlayingPlaylist = album;
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

  public JSONObject buildMedia(Connection connection, int offset) {
    JSONObject data = new JSONObject();
    try {
      data.put(PARAMS.ACTION, PARAMS.ACTION_LOAD);
      if(VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.PLEX_USERNAME) != null) {
        data.put(PARAMS.PLEX_USERNAME, VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.PLEX_USERNAME));
      }
      data.put(PARAMS.MEDIA_TYPE, nowPlayingMedia instanceof PlexVideo ? PARAMS.MEDIA_TYPE_VIDEO : PARAMS.MEDIA_TYPE_AUDIO);
      data.put(PARAMS.RESUME, VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.RESUME, false));
      data.put(PARAMS.CLIENT, VoiceControlForPlexApplication.gsonWrite.toJson(client));
      data.put(PARAMS.SESSION_ID, plexSessionId);
      logger.d("setting src to %s", getTranscodeUrl(nowPlayingMedia, connection, offset));
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
    return VoiceControlForPlexApplication.gsonWrite.toJson(nowPlayingPlaylist);
  }

  // End Chromecast client specific methods

  // Local client specific methods
  public void setMedia(PlexMedia m) {
    if(client.isLocalClient)
      nowPlayingMedia = m;
  }
  // End local client specific methods
}
