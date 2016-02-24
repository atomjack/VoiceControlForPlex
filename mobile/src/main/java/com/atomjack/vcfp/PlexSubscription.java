package com.atomjack.vcfp;

import android.content.res.Resources;
import android.os.Handler;

import com.atomjack.shared.Logger;
import com.atomjack.shared.PlayerState;
import com.atomjack.shared.model.Timeline;
import com.atomjack.vcfp.interfaces.PlexMediaHandler;
import com.atomjack.vcfp.interfaces.PlexSubscriptionListener;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexResponse;
import com.atomjack.vcfp.model.PlexServer;
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlexSubscription {
  private static final int SUBSCRIBE_INTERVAL = 30000; // Send subscribe message every 30 seconds to keep us alive

  private static Serializer serial = new Persister();

  public PlexClient mClient; // the client we are subscribing to


  private PlexSubscriptionListener listener;
  private PlexSubscriptionListener notificationListener; // This will be the listener but will not be reset, and will be used for changing the notification

  private int commandId = 1;
  private int subscriptionPort = 59409;
  private boolean subscribed = false;
  private ServerSocket serverSocket;
  Thread serverThread = null;
  Handler updateConversationHandler;

  private PlexMedia nowPlayingMedia;

  private int failedHeartbeats = 0;
  private final int failedHeartbeatMax = 5;

  private Handler mHandler;

  private PlayerState currentState = PlayerState.STOPPED;
  private Timeline currentTimeline;

  public Date timeLastHeardFromClient;

  public PlexSubscription() {
    mHandler = new Handler();
  }

  public void setListener(PlexSubscriptionListener _listener) {
    if(_listener != null)
      Logger.d("Setting listener to %s", _listener.getClass().getSimpleName());
    listener = _listener;
    if(_listener != null) {
      notificationListener = _listener;
    }
  }

  public void removeListener(PlexSubscriptionListener _listener) {
    if(listener == _listener) {
      Logger.d("removing listener");
      listener = null;
    }
  }

//  public void setPlexSubscriptionListener(PlexSubscriptionListener plexSubscriptionListener) {
//    this.plexSubscriptionListener = plexSubscriptionListener;
//  }

  public PlexSubscriptionListener getListener() {
    return listener;
  }

  public boolean isSubscribed() {
    return subscribed && mClient != null;
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
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(subscriptionPort));
//        subscriptionPort = serverSocket.getLocalPort();
      } catch (IOException e) {

        e.printStackTrace();
      }

      Logger.d("running");



      onSocketReady.run();
      if(onReady != null) {
//        onReady.run();
      }

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

  public synchronized void startSubscription(final PlexClient client) {
    Logger.d("startSubscription: %s", updateConversationHandler);
    if(updateConversationHandler == null) {
      updateConversationHandler = new Handler();
    }
    ServerThread thread = new ServerThread();
    thread.onReady(new Runnable() {
      @Override
      public void run() {
      Logger.d("subscribing");
      subscribe(client);
      }
    });

    serverThread = new Thread(thread);
    serverThread.start();
  }

  public void subscribe(final PlexClient client) {
    Logger.d("PlexSubscription subscribe: %s, handler is null: %s", client, updateConversationHandler == null);
    if(updateConversationHandler == null)
      startSubscription(client);
    else
      subscribe(client, false);
  }

  public void subscribe(PlexClient client, final boolean isHeartbeat) {
    if(client == null)
      return;
    mClient = client;


    PlexHttpClient.subscribe(client, subscriptionPort, commandId, VoiceControlForPlexApplication.getInstance().getUUID(), VoiceControlForPlexApplication.getInstance().getString(R.string.app_name), new PlexHttpResponseHandler() {
      @Override
      public void onSuccess(PlexResponse response) {
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
            onSubscribed();
          }
        }
      }

      @Override
      public void onFailure(final Throwable error) {
        error.printStackTrace();
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
            if(listener != null)
              mHandler.post(new Runnable() {
                @Override
                public void run() {
                  // TODO: this
                  listener.onSubscribeError(String.format(VoiceControlForPlexApplication.getInstance().getString(R.string.client_lost_connection), mClient.name));
                  VoiceControlForPlexApplication.getInstance().cancelNotification();
                }
              });
          }
        } else {
          mHandler.post(new Runnable() {
            @Override
            public void run() {
              if(listener != null) {
                listener.onSubscribeError(error.getMessage());
                VoiceControlForPlexApplication.getInstance().cancelNotification();
              }
            }
          });
        }
      }
    });

  }

  private void onSubscribed() {
    mHandler.post(new Runnable() {
      @Override
      public void run() {
        Logger.d("PlexSubscription onSubscribed, client: %s, listener: %s", mClient, listener);

        if(listener != null)
          listener.onSubscribed(mClient);
      }
    });
  }

  private Runnable subscriptionHeartbeat = new Runnable() {
    @Override
    public void run() {
      if(subscribed) {
        if(failedHeartbeats == 0) {
          Logger.d("Sending heartbeat");
          subscribe(mClient, true);
          mHandler.postDelayed(subscriptionHeartbeat, SUBSCRIBE_INTERVAL);
        }
      } else {
        Logger.d("stopping subscription heartbeat because we are not subscribed anymore");
      }
    }
  };

  public void unsubscribe() {
    unsubscribe(null);
  }

  public void unsubscribe(final Runnable onFinish) {
    unsubscribe(true, onFinish);
  }

  public void unsubscribe(final boolean notify, final Runnable onFinish) {
//    if(listener == null)
//      return;

    PlexHttpClient.unsubscribe(mClient, commandId, VoiceControlForPlexApplication.getInstance().prefs.getUUID(), VoiceControlForPlexApplication.getInstance().getString(R.string.app_name), new PlexHttpResponseHandler() {
      @Override
      public void onSuccess(PlexResponse response) {
        Logger.d("[PlexSubscription] Unsubscribed");
        subscribed = false;
        commandId++;
        mClient = null;

        try {
          serverSocket.close();
          serverSocket = null;
        } catch (Exception ex) {
          Logger.d("Exception attempting to close socket.");
          ex.printStackTrace();
        }
        if(notify)
          onUnsubscribed();
        if (onFinish != null)
          onFinish.run();
      }

      @Override
      public void onFailure(Throwable error) {
        // TODO: Handle failure here?
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

  private void onMessage(final MediaContainer mediaContainer) {
//    Logger.d("[PlexSubscription] onMessage, listener: %s", listener);
    // Parse the media container here to figure out what message to send to the listeners
    mHandler.post(new Runnable() {
      @Override
      public void run() {
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

              // If we don't currently have now playing media, or the media has changed, update nowPlayingMedia and call this method back
              if((nowPlayingMedia == null || (nowPlayingMedia != null && !nowPlayingMedia.key.equals(timeline.key))) && currentState != PlayerState.STOPPED) {
                getPlayingMedia(server, timeline, new PlexMediaHandler() {
                  @Override
                  public void onFinish(PlexMedia media) {
                    Logger.d("media: %s, listener: %s, state: %s", media.getTitle(), listener, currentState);
                    if(listener != null && currentState != PlayerState.STOPPED)
                      if(nowPlayingMedia != null)
                        listener.onMediaChanged(media);
                      else
                        listener.onPlayStarted(media, PlayerState.getState(timeline));
                    nowPlayingMedia = media;
                    VoiceControlForPlexApplication.getInstance().setNotification(mClient, currentState, media);
                  }
                });
              } else {
                if(oldState != currentState) {
                  // State has changed
                  if(listener != null)
                    listener.onStateChanged(nowPlayingMedia, currentState);
                  if(currentState == PlayerState.STOPPED) {
                    nowPlayingMedia = null;
                    VoiceControlForPlexApplication.getInstance().cancelNotification();
                  } else {
                    VoiceControlForPlexApplication.getInstance().setNotification(mClient, currentState, nowPlayingMedia);
                  }
                } else {
                  // State has not changed, so alert listener of the current timecode
                  if(listener != null && currentState != PlayerState.STOPPED)
                    listener.onTimeUpdate(currentState, timeline.time/1000); // timecode in Timeline is in ms
                }
              }
            }
          }
        }
      }
    });
  }

  private void getPlayingMedia(final PlexServer server, final Timeline timeline, final PlexMediaHandler onFinish) {
    PlexHttpClient.get(server, timeline.key, new PlexHttpMediaContainerHandler() {
      @Override
      public void onSuccess(MediaContainer mediaContainer) {
        PlexMedia media = null;
        if (timeline.type.equals("video"))
          media = mediaContainer.videos.get(0);
        else if (timeline.type.equals("music"))
          media = mediaContainer.tracks.get(0);

        Logger.d("Got playing media: %s", media.getTitle());
        if(onFinish != null)
          onFinish.onFinish(media);
      }

      @Override
      public void onFailure(Throwable error) {
        error.printStackTrace();
      }
    });
  }

  private void onUnsubscribed() {
    VoiceControlForPlexApplication.getInstance().cancelNotification();
    nowPlayingMedia = null;
    mHandler.post(new Runnable() {
      @Override
      public void run() {
        if (listener != null)
          listener.onUnsubscribed();
      }
    });
  }

  // TODO: Get rid of this
  public interface PlexListener {
    void onSubscribed(PlexClient client);
    void onUnsubscribed();
    void onTimelineReceived(MediaContainer mc);
    void onSubscribeError(String errorMessage);
  };

  public PlexClient getClient() {
    return mClient;
  }

  public PlayerState getCurrentState() {
    return currentState;
  }

  public Timeline getCurrentTimeline() {
    return currentTimeline;
  }

  public int getCommandId() {
    return commandId;
  }
}
