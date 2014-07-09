package com.atomjack.vcfp;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;

import com.atomjack.vcfp.activities.VCFPActivity;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexResponse;
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
import java.net.SocketException;
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

  public PlexClient mClient; // the mClient we are subscribing to

  private String uuid;
  private String appName;

  private VCFPActivity listener;
  private VCFPActivity notificationListener; // This will be the listener but will not be reset, and will be used for changing the notification

  private int commandId = 0;
  private int subscriptionPort = 59409;
  private boolean subscribed = false;
  private ServerSocket serverSocket;
  Thread serverThread = null;
  Handler updateConversationHandler;

  private Handler mHandler;

  public PlexSubscription() {
    mHandler = new Handler();
    uuid = Preferences.getUUID();
  }

  public void setListener(VCFPActivity _listener) {
    if(_listener != null)
      Logger.d("Setting listener to %s", _listener.getClass().getSimpleName());
    listener = _listener;
    if(_listener != null) {
      appName = _listener.getString(R.string.app_name);
      notificationListener = _listener;
    }
  }

  public void removeListener(VCFPActivity _listener) {
    if(listener == _listener) {
      Logger.d("removing listener");
      listener = null;
    }
  }

  public VCFPActivity getListener() {
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
        serverSocket = new ServerSocket(subscriptionPort);
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

  public void subscribe(PlexClient client) {
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
    Logger.d("subscribe, client now %s", mClient);
    QueryString qs = new QueryString("port", String.valueOf(subscriptionPort));
    qs.add("commandID", String.valueOf(commandId));
    qs.add("protocol", "http");

    Header[] headers = {
      new BasicHeader(PlexHeaders.XPlexClientIdentifier, uuid),
      new BasicHeader(PlexHeaders.XPlexDeviceName, appName)
    };
    PlexHttpClient.get(String.format("http://%s:%s/player/timeline/subscribe?%s", mClient.address, mClient.port, qs), headers, new PlexHttpResponseHandler() {
      @Override
      public void onSuccess(PlexResponse response) {
        Logger.d("PlexSubscription: Subscribed: %s", response.status);
        commandId++;
        subscribed = true;

        if (!isHeartbeat) {
          // Start the heartbeat subscription (so the server knows we're still here)
          mHandler.removeCallbacks(subscriptionHeartbeat);
          mHandler.postDelayed(subscriptionHeartbeat, SUBSCRIBE_INTERVAL);
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
    mHandler.post(new Runnable() {
      @Override
      public void run() {
        Logger.d("PlexSubscription onSubscribed, client: %s, listener: %s", mClient, listener);
        if (listener != null && mClient != null) {
          listener.onSubscribed(mClient);
          Logger.d("Sending broadcast");
//          Intent subscribedBroadcast = new Intent(listener, listener.getClass());
//          subscribedBroadcast.setAction(ACTION_SUBSCRIBED);
//          subscribedBroadcast.putExtra(EXTRA_CLIENT, mClient);
//          listener.startActivity(subscribedBroadcast);
          Intent subscribedBroadcast = new Intent(ACTION_SUBSCRIBED);
          subscribedBroadcast.putExtra(EXTRA_CLIENT, mClient);
          LocalBroadcastManager.getInstance(listener).sendBroadcast(subscribedBroadcast);
        }
      }
    });
  }

  private Runnable subscriptionHeartbeat = new Runnable() {
    @Override
    public void run() {
      if(subscribed) {
        Logger.d("Sending heartbeat");
        subscribe(mClient, true);
        mHandler.postDelayed(subscriptionHeartbeat, SUBSCRIBE_INTERVAL);
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
        new BasicHeader(PlexHeaders.XPlexClientIdentifier, uuid),
        new BasicHeader(PlexHeaders.XPlexDeviceName, listener.getString(R.string.app_name)),
        new BasicHeader(PlexHeaders.XPlexTargetClientIdentifier, mClient.machineIdentifier)
      };
      PlexHttpClient.get(String.format("http://%s:%s/player/timeline/unsubscribe?%s", mClient.address, mClient.port, qs), headers, new PlexHttpResponseHandler() {
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
          onUnsubscribed();
        }
      });
//      if(onFinish != null)
//        onFinish.run();
//    } else if(onFinish != null) {
//      onFinish.run();
//    }
  }

  private void onMessage(final MediaContainer mc) {
    mHandler.post(new Runnable() {
      @Override
      public void run() {
        if (listener != null) {
          listener.onMessageReceived(mc);
        } else {
          notificationListener.onMessageReceived(mc);
        }
      }
    });
  }

  private void onUnsubscribed() {
    mHandler.post(new Runnable() {
      @Override
      public void run() {
        if (listener != null)
          listener.onUnsubscribed();
      }
    });
  }

  public interface Listener {
    void onSubscribed(PlexClient client);
    void onUnsubscribed();
    void onMessageReceived(MediaContainer mc);
  };
}
