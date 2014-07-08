package com.atomjack.vcfp;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import android.app.AlertDialog;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources.NotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;

import com.atomjack.vcfp.activities.NowPlayingActivity;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.model.PlexStream;
import com.atomjack.vcfp.services.PlexControlService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

public class VoiceControlForPlexApplication extends Application
{
	public final static String MINIMUM_PHT_VERSION = "1.0.7";

	private static boolean isApplicationVisible;

  private static int nowPlayingNotificationId = 0;

	public static Gson gsonRead = new GsonBuilder()
					.registerTypeAdapter(Uri.class, new UriDeserializer())
					.create();

	public static Gson gsonWrite = new GsonBuilder()
					.registerTypeAdapter(Uri.class, new UriSerializer())
					.create();

	public final static class Intent {
			public final static String GDMRECEIVE = "com.atomjack.vcfp.intent.gdmreceive";

			public final static String EXTRA_SERVER = "com.atomjack.vcfp.intent.extra_server";
			public final static String EXTRA_CLIENT = "com.atomjack.vcfp.intent.extra_client";
			public final static String EXTRA_RESUME = "com.atomjack.vcfp.intent.extra_resume";
			public final static String EXTRA_SILENT = "com.atomjack.vcfp.intent.extra_silent";


      public final static String SCAN_TYPE = "com.atomjack.vcfp.intent.scan_type";
      public final static String SCAN_TYPE_CLIENT = "com.atomjack.vcfp.intent.scan_type_client";
      public final static String SCAN_TYPE_SERVER = "com.atomjack.vcfp.intent.scan_type_server";
			public final static String EXTRA_SERVERS = "com.atomjack.vcfp.intent.extra_servers";
			public final static String EXTRA_CLIENTS = "com.atomjack.vcfp.intent.extra_clients";
			public final static String ARGUMENTS = "com.atomjack.vcfp.intent.ARGUMENTS";

			public final static String SHOWRESOURCE = "com.atomjack.vcfp.intent.SHOWRESOURCE";

			public final static String CAST_MEDIA = "com.atomjack.vcfp.intent.CAST_MEDIA";
			public final static String EXTRA_MEDIA = "com.atomjack.vcfp.intent.EXTRA_MEDIA";
      public final static String EXTRA_CLASS = "com.atomjack.vcfp.intent.EXTRA_CLASS";
      public final static String SUBSCRIBED = "com.atomjack.vcfp.intent.SUBSCRIBED";

      public final static String EXTRA_QUERYTEXT = "com.atomjack.vcfp.intent.EXTRA_QUERYTEXT";
	};

	public static ConcurrentHashMap<String, PlexServer> servers = new ConcurrentHashMap<String, PlexServer>();
	public static Map<String, PlexClient> clients = new HashMap<String, PlexClient>();

	public static Map<String, PlexClient> castClients = new HashMap<String, PlexClient>();

	private static Serializer serial = new Persister();

	public static Locale getVoiceLocale(String loc) {
		String[] voice = loc.split("-");

		Locale l = null;
		if(voice.length == 1)
			l = new Locale(voice[0]);
		else if(voice.length == 2)
			l = new Locale(voice[0], voice[1]);
		else if(voice.length == 3)
			l = new Locale(voice[0], voice[1], voice[2]);

		return l;
	}

  @Override
  public void onCreate() {
    super.onCreate();
    initSingletons();
  }

  protected void initSingletons()
  {
    // Initialize the instance of MySingleton
//    VCFPSingleton.initInstance();
  }

  public static void addPlexServer(final PlexServer server) {
		addPlexServer(server, null);
	}

	public static void addPlexServer(final PlexServer server, final Runnable onFinish) {
		Logger.d("ADDING PLEX SERVER: %s, %s", server.name, server.address);
		if(server.name.equals("") || server.address.equals("")) {
			return;
		}
    try {
      server.findServerConnection(new ServerFindHandler() {
        @Override
        public void onSuccess() {
          Logger.d("active connection: %s", server.activeConnection);
          String url = String.format("http://%s:%s/library/sections/", server.activeConnection.address, server.activeConnection.port);
          if(server.accessToken != null)
            url += String.format("?%s=%s", PlexHeaders.XPlexToken, server.accessToken);
          AsyncHttpClient httpClient = new AsyncHttpClient();
          Logger.d("Fetching %s", url);
          httpClient.get(url, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, org.apache.http.Header[] headers, byte[] responseBody) {
            MediaContainer mc = new MediaContainer();
            try {
              mc = serial.read(MediaContainer.class, new String(responseBody, "UTF-8"));
            } catch (NotFoundException e) {
              e.printStackTrace();
            } catch (Exception e) {
              e.printStackTrace();
            }
            server.movieSections = new ArrayList<String>();
            server.tvSections = new ArrayList<String>();
            server.musicSections = new ArrayList<String>();
            for(int i=0;i<mc.directories.size();i++) {
              if(mc.directories.get(i).type.equals("movie")) {
                server.addMovieSection(mc.directories.get(i).key);
              }
              if(mc.directories.get(i).type.equals("show")) {
                server.addTvSection(mc.directories.get(i).key);
              }
              if(mc.directories.get(i).type.equals("artist")) {
                server.addMusicSection(mc.directories.get(i).key);
              }
            }
            Logger.d("%s has %d directories.", server.name, mc.directories != null ? mc.directories.size() : 0);
            if(!server.name.equals("")) {
              servers.put(server.name, server);

              // Finally, if this server is the current default server, save it in preferences so the access token gets transferred
              PlexServer defaultServer = gsonRead.fromJson(Preferences.get(Preferences.SERVER, ""), PlexServer.class);
              if(defaultServer != null && server.machineIdentifier.equals(defaultServer.machineIdentifier)) {
                Preferences.put(Preferences.SERVER, gsonWrite.toJson(server));
              }

              Logger.d("Added %s.", server.name);
            }
            if(onFinish != null)
              onFinish.run();
            }
          });
        }

        @Override
        public void onFailure(int statusCode) {
          Logger.d("Failed to find connection for %s: %d", server.name, statusCode);
            if(onFinish != null)
              onFinish.run();
        }
      });


    } catch (Exception e) {
      Logger.e("Exception getting clients: %s", e.toString());
      if(onFinish != null)
        onFinish.run();
    }
	}

	public static boolean isVersionLessThan(String v1, String v2) {
		VersionComparator cmp = new VersionComparator();
		return cmp.compare(v1, v2) < 0;
	}

	public static boolean isWifiConnected(Context context) {
		ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		return mWifi.isConnected();
	}

	public static void showNoWifiDialog(Context context) {
		AlertDialog.Builder usageDialog = new AlertDialog.Builder(context);
		usageDialog.setTitle(R.string.no_wifi_connection);
		usageDialog.setMessage(R.string.no_wifi_connection_message);
		usageDialog.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
			}
		});
		usageDialog.show();
	}

	public static boolean isApplicationVisible() {
		return isApplicationVisible;
	}

	public static void applicationResumed() {
		isApplicationVisible = true;
	}

	public static void applicationPaused() {
		isApplicationVisible = false;
	}

	public static String secondsToTimecode(double _seconds) {
		ArrayList<String> timecode = new ArrayList<String>();
		double hours, minutes, seconds = 0;
		hours = Math.floor(_seconds/3600);
		minutes = Math.floor((_seconds - (hours * 3600)) / 60);
		seconds = Math.floor(_seconds - (hours * 3600) - (minutes * 60));
		if(hours > 0)
			timecode.add(twoDigitsInt((int)hours));
		timecode.add(twoDigitsInt((int)minutes));
		timecode.add(twoDigitsInt((int) seconds));
		return TextUtils.join(":", timecode);
	}

	private static String twoDigitsInt( int pValue )
	{
		if ( pValue == 0 )
			return "00";
		if ( pValue < 10 )
			return "0" + pValue;

		return String.valueOf( pValue );
	}

  public static String generateRandomString() {
    SecureRandom random = new SecureRandom();
    return new BigInteger(130, random).toString(32).substring(0, 12);
  }

  public static void setNotification(Context context, PlexClient client, PlayerState currentState, PlexMedia media) {
    Logger.d("Setting notification, client: %s", client);
    if(client != null) {
      NotificationManager mNotifyMgr = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
      android.content.Intent rewindIntent = new android.content.Intent(context, PlexControlService.class);
      rewindIntent.setAction(PlexControlService.ACTION_REWIND);
      rewindIntent.putExtra(PlexControlService.CLIENT, client);
      rewindIntent.putExtra(PlexControlService.MEDIA, media);
      PendingIntent piRewind = PendingIntent.getService(context, 0, rewindIntent, PendingIntent.FLAG_UPDATE_CURRENT);

      android.content.Intent playPauseIntent = new android.content.Intent(context, PlexControlService.class);
      int playPauseButton;
      String playPauseAction;
      if (currentState == PlayerState.PLAYING) {
        playPauseButton = R.drawable.button_pause;
        playPauseAction = PlexControlService.ACTION_PAUSE;
      } else {
        playPauseButton = R.drawable.button_play;
        playPauseAction = PlexControlService.ACTION_PLAY;
      }
      playPauseIntent.setAction(playPauseAction);
      playPauseIntent.putExtra(PlexControlService.CLIENT, client);
      playPauseIntent.putExtra(PlexControlService.MEDIA, media);
      PendingIntent piPlayPause = PendingIntent.getService(context, 0, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);

      android.content.Intent nowPlayingIntent = new android.content.Intent(context, NowPlayingActivity.class);
      nowPlayingIntent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK |
              android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
      nowPlayingIntent.putExtra(Intent.EXTRA_MEDIA, media);
      nowPlayingIntent.putExtra(Intent.EXTRA_CLIENT, client);
      PendingIntent piNowPlaying = PendingIntent.getActivity(context, 0, nowPlayingIntent, PendingIntent.FLAG_UPDATE_CURRENT);

      try {
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(context)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setAutoCancel(false)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .setContentTitle(media.title)
                        .setContentText(String.format(context.getString(R.string.playing_on), client.name))
                        .addAction(R.drawable.button_rewind, context.getString(R.string.rewind), piRewind)
                        .addAction(playPauseButton, context.getString(currentState != PlayerState.PAUSED ? R.string.pause : R.string.play), piPlayPause)
                        .setContentIntent(piNowPlaying)
                        .setDefaults(Notification.DEFAULT_ALL);
        Notification n = mBuilder.build();
        // Disable notification sound
        n.defaults = 0;
        mNotifyMgr.notify(nowPlayingNotificationId, n);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
  }
}
