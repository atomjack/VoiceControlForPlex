package com.atomjack.vcfp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.content.LocalBroadcastManager;

import com.atomjack.vcfp.activities.VCFPActivity;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexResponse;
import com.atomjack.vcfp.model.Timeline;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpResponseHandler;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlexSubscription {
  public static final String ACTION_SUBSCRIBE = "com.atomjack.vcfp.action_subscribe";
  public static final String ACTION_SUBSCRIBED = "com.atomjack.vcfp.action_subscribed";
  public static final String ACTION_UNSUBSCRIBE = "com.atomjack.vcfp.action_unsubscribe";

  // Boolean indicating whether or not to notify an activity of an action done
  public static final String EXTRA_NOTIFY = "com.atomjack.vcfp.extra_unsubscribe_notify";

  public static final String ACTION_UNSUBSCRIBED = "com.atomjack.vcfp.action_unsubscribed";
  public static final String ACTION_BROADCAST = "com.atomjack.vcfp.action_broadcast";
  public static final String ACTION_MESSAGE = "com.atomjack.vcfp.action_message";

  public static final String EXTRA_CLASS = "com.atomjack.vcfp.extra_class";
  public static final String EXTRA_CLIENT = "com.atomjack.vcfp.extra_client";
  public static final String EXTRA_TIMELINES = "com.atomjack.vcfp.extra_timelines";

  private static final int SUBSCRIBE_INTERVAL = 30000; // Send subscribe message every 30 seconds to keep us alive

  private static Serializer serial = new Persister();

  public PlexClient mClient; // the client we are subscribing to

  private VCFPActivity listener;
  private VCFPActivity notificationListener; // This will be the listener but will not be reset, and will be used for changing the notification

  private int commandId = 0;
  private int subscriptionPort = 59409;
  private boolean subscribed = false;
  private boolean subscriptionHasStarted = false;
  private ServerSocket serverSocket;
  Thread serverThread = null;
  Handler updateConversationHandler;

  private Handler handler = new Handler();

  public PlexSubscription() {
  }

  public void setListener(VCFPActivity _listener) {
    if(_listener != null)
      Logger.d("Setting listener to %s", _listener.getClass().getSimpleName());
    listener = _listener;
    if(_listener != null)
      notificationListener = _listener;
  }

  public VCFPActivity getListener() {
    return listener;
  }

  public boolean isSubscribed() {
    return subscribed;
  }

  class ServerThread implements Runnable {
    Runnable onReady;

    private void onReady(Runnable runme) {
      onReady = runme;
    }

    public void run() {
      Logger.d("starting serverthread");
      Socket socket = null;
      try {
        serverSocket = new ServerSocket(subscriptionPort);
      } catch (IOException e) {
        e.printStackTrace();
      }

      Logger.d("running");
      if(onReady != null)
        onReady.run();

//			while (!Thread.currentThread().isInterrupted()) {
      while(true) {
        try {
          if(serverSocket == null)
            return;
          socket = serverSocket.accept();

          Map<String, String> headers = new HashMap<String, String>();
          String line;
          Pattern p = Pattern.compile("^([^:]+): (.+)$");
          Matcher matcher;
          BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
          while ((line = reader.readLine()) != null)
          {
            matcher = p.matcher(line);
            if(matcher.find()) {
              headers.put(matcher.group(1), matcher.group(2));
            }
            if(line.equals(""))
            {
              break; // and don't get the next line!
            }
          }
          int contentLength = Integer.parseInt(headers.get("Content-Length"));

          StringBuilder requestContent = new StringBuilder();
          for (int i = 0; i < contentLength; i++)
          {
            requestContent.append((char) reader.read());
          }

					/*
					    <Timeline address="192.168.1.101" audioStreamID="158"
					    containerKey="/library/metadata/14"
					    controllable="playPause,stop,shuffle,repeat,volume,stepBack,stepForward,seekTo,subtitleStream,audioStream"
					    duration="9266976" guid="com.plexapp.agents.imdb://tt0090605?lang=en"
					    key="/library/metadata/14" location="fullScreenVideo"
					    machineIdentifier="xxxxxx" mute="0" playQueueItemID="14"
					    port="32400" protocol="http" ratingKey="14" repeat="0" seekRange="0-9266976" shuffle="0"
					    state="playing" subtitleStreamID="-1" time="4087" type="video" volume="1" />
					 */

          String xml = requestContent.toString();
          MediaContainer mediaContainer = new MediaContainer();

//					Logger.d("xml: %s", xml);
          try {
            mediaContainer = serial.read(MediaContainer.class, xml);
          } catch (Resources.NotFoundException e) {
            e.printStackTrace();
          } catch (Exception e) {
            e.printStackTrace();
          }

          // TODO: Do something with mediaContainer
          onMessage(headers, mediaContainer);


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

  public void subscribe(PlexClient client) {
    subscribe(client, false);
  }

  public void subscribe(PlexClient client, final boolean isHeartbeat) {
    if(listener == null) {
      Logger.d("listener is null");
      return;
    }
    mClient = client;
    QueryString qs = new QueryString("port", String.valueOf(subscriptionPort));
    qs.add("commandID", String.valueOf(commandId));
    qs.add("protocol", "http");

    Header[] headers = {
      new BasicHeader(PlexHeaders.XPlexClientIdentifier, Preferences.getUUID()),
      new BasicHeader(PlexHeaders.XPlexDeviceName, listener.getString(R.string.app_name))
    };
    PlexHttpClient.get(listener, String.format("http://%s:%s/player/timeline/subscribe?%s", mClient.address, mClient.port, qs), headers, new PlexHttpResponseHandler() {
      @Override
      public void onSuccess(PlexResponse response) {
        Logger.d("PlexSubscription: Subscribed");
        commandId++;
        subscribed = true;

        if (!isHeartbeat) {
          // Start the heartbeat subscription (so the server knows we're still here)
          handler.postDelayed(subscriptionHeartbeat, SUBSCRIBE_INTERVAL);
          onSubscribed();
        }
      }

      @Override
      public void onFailure(Throwable error) {
        error.printStackTrace();
      }
    });
  }

  private void onSubscribed() {
    handler.post(new Runnable() {
      @Override
      public void run() {
        if(listener != null)
          listener.onSubscribed();
      }
    });
  }

  private Runnable subscriptionHeartbeat = new Runnable() {
    @Override
    public void run() {
      if(subscribed) {
        Logger.d("Sending heartbeat");
        subscribe(mClient, true);
        handler.postDelayed(subscriptionHeartbeat, SUBSCRIBE_INTERVAL);
      } else {
        Logger.d("stopping subscription heartbeat because we are not subscribed anymore");
      }
    }
  };

  public void unsubscribe() {
    if(listener == null)
      return;

    //if(mSubscribers.size() == 0) {
      QueryString qs = new QueryString("commandID", String.valueOf(commandId));
      Logger.d("mClient: %s", mClient);
      Header[] headers = {
        new BasicHeader(PlexHeaders.XPlexClientIdentifier, Preferences.getUUID()),
        new BasicHeader(PlexHeaders.XPlexDeviceName, listener.getString(R.string.app_name)),
        new BasicHeader(PlexHeaders.XPlexTargetClientIdentifier, mClient.machineIdentifier)
      };
      PlexHttpClient.get(listener, String.format("http://%s:%s/player/timeline/unsubscribe?%s", mClient.address, mClient.port, qs), headers, new PlexHttpResponseHandler() {
        @Override
        public void onSuccess(PlexResponse response) {
          Logger.d("Unsubscribed");
          subscribed = false;
          commandId++;
          mClient = null;

          try {
            serverSocket.close();
            serverSocket = null;
          } catch (Exception ex) {
//            ex.printStackTrace();
          }
          onUnsubscribed();
//          if (onFinish != null)
//            onFinish.run();
        }

        @Override
        public void onFailure(Throwable error) {
          // TODO: Handle failure here?
          Logger.d("failure unsubscribing");
        }
      });
//      if(onFinish != null)
//        onFinish.run();
//    } else if(onFinish != null) {
//      onFinish.run();
//    }
  }

  private void onMessage(final Map<String, String> headers, final MediaContainer mc) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        if(listener != null) {
//          Logger.d("sending message from PlexSubscription");
          listener.onMessageReceived(mc);
        } else {
          notificationListener.onMessageReceived(mc);
        }
      }
    });
  }

  private void onUnsubscribed() {
    handler.post(new Runnable() {
      @Override
      public void run() {
        if(listener != null)
          listener.onUnsubscribed();
      }
    });
  }

  public interface Listener {
    void onSubscribed();
    void onUnsubscribed();
    void onMessageReceived(MediaContainer mc);
  }
}
