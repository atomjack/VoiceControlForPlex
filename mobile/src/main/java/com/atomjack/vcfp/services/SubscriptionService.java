package com.atomjack.vcfp.services;

import android.app.Service;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.atomjack.shared.Logger;
import com.atomjack.shared.NewLogger;
import com.atomjack.shared.PlayerState;
import com.atomjack.shared.Preferences;
import com.atomjack.shared.model.Timeline;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.interfaces.PlexSubscriptionListener;
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

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  public void subscribe(PlexClient client) {
    if(client.isCastClient) {

    } else if(client.isLocalClient) {

    } else {

    }
  }

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

    } else {
      client.play();
    }
  }

  public void pause() {
    if(client.isCastClient) {

    } else {
      client.pause();
    }
  }

  public void stop() {
    if (client.isCastClient) {
//      castPlayerManager.stop();
    } else {
      client.stop();
    }
  }

  public void rewind() {
    if (client.isCastClient) {
//      castPlayerManager.seekTo(castPlayerManager.getPosition() - 15);
    } else {
      client.seekTo((position*1000) - 15000);
    }
  }

  public void forward() {
    if (client.isCastClient) {
//      castPlayerManager.seekTo(castPlayerManager.getPosition() + 30);
    } else {
      client.seekTo((position*1000) + 30000);
    }
  }

  public void previous() {
    if (client.isCastClient) {
//      castPlayerManager.doPrevious();
    } else {
      client.previous(null);
    }
  }

  public void next() {
    if (client.isCastClient) {
//      castPlayerManager.doNext();
    } else {
      client.next(null);
    }
  }

  public void seekTo(int seconds) {
    if (client.isCastClient) {

    } else {
      client.seekTo((seconds*1000) + 30000);
    }
  }

  public void cycleStreams(int streamType) {
    if(client.isCastClient) {

    } else {
      Stream newStream = nowPlayingMedia.getNextStream(streamType);
      client.setStream(newStream);
      nowPlayingMedia.setActiveStream(newStream);
    }
  }

  public void subtitlesOn() {
    if(client.isCastClient) {

    } else {
      client.setStream(nowPlayingMedia.getStreams(Stream.SUBTITLE).get(1));
      nowPlayingMedia.setActiveStream(nowPlayingMedia.getStreams(Stream.SUBTITLE).get(1));
    }
  }

  public void subtitlesOff() {
    if(client.isCastClient) {

    } else {
      client.setStream(nowPlayingMedia.getStreams(Stream.SUBTITLE).get(0));
      nowPlayingMedia.setActiveStream(nowPlayingMedia.getStreams(Stream.SUBTITLE).get(0));
    }
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

    } else if(client.isCastClient) {

    } else {
      PlexHttpClient.unsubscribe(client, commandId, VoiceControlForPlexApplication.getInstance().prefs.getUUID(), VoiceControlForPlexApplication.getInstance().getString(R.string.app_name), new PlexHttpResponseHandler() {
        @Override
        public void onSuccess(PlexResponse response) {
          Logger.d("[PlexSubscription] Unsubscribed");
          subscribed = false;
          VoiceControlForPlexApplication.getInstance().prefs.remove(Preferences.SUBSCRIBED_CLIENT);
          commandId++;
          client = null;

          try {
            serverSocket.close();
            serverSocket = null;
          } catch (Exception ex) {
            Logger.d("Exception attempting to close socket.");
            ex.printStackTrace();
          }
          if (notify)
            onUnsubscribed();
          if (onFinish != null)
            onFinish.run();
        }

        @Override
        public void onFailure(Throwable error) {
          Logger.d("failure unsubscribing");
          subscribed = false;
          mHandler.removeCallbacks(subscriptionHeartbeat);

          try {
            serverSocket.close();
            serverSocket = null;
          } catch (Exception ex) {
            Logger.d("Exception attempting to close socket due to failed unsubscribe.");
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
    Logger.d("PlexSubscription subscribe: %s, handler is null: %s", client, updateConversationHandler == null);
    subscribing = true;
    if(updateConversationHandler == null)
      startSubscription(client, showFeedback);
    else
      subscribe(client, false, showFeedback);
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
          Logger.d("PlexSubscription: Subscribed: %s, Code: %d", response != null ? response.status : "", response.code);
        else
          Logger.d("PlexSubscription: Heartbeat: %s, Code: %d", response != null ? response.status : "", response.code);

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
          Logger.d("%d failed heartbeats", failedHeartbeats);
          if(failedHeartbeats >= failedHeartbeatMax) {
            Logger.d("Unsubscribing due to failed heartbeats");
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
      Logger.d("PlexSubscription onSubscribed, client: %s", client);

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
        Logger.d("stopping subscription heartbeat because we are not subscribed anymore");
      }
    }
  };

  public synchronized void startSubscription(final PlexClient client, final boolean showFeedback) {
    Logger.d("startSubscription: %s", updateConversationHandler);
    if(updateConversationHandler == null) {
      updateConversationHandler = new Handler();
    }
    ServerThread thread = new ServerThread();
    thread.onReady(() -> {
      Logger.d("subscribing");
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
      Logger.d("starting serverthread");
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

      Logger.d("running");



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
//          Logger.d("xml: %s", xml);

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
}
