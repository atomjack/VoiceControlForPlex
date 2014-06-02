package com.atomjack.vcfp;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources.NotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexServer;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

public class VoiceControlForPlexApplication
{
	public final static class Pref {
		public final static String FEEDBACK_VOICE = "pref.feedback.voice";
		public final static String ERRORS_VOICE = "pref.errors.voice";
		public final static String SAVED_SERVERS = "pref.saved_servers";
		public final static String SAVED_CLIENTS = "pref.saved_clients";

	};

	public final static String MINIMUM_PHT_VERSION = "1.0.7";

	public final static class Intent {
			public final static String GDMRECEIVE = "com.atomjack.vcfp.intent.gdmreceive";
			public final static String SERVER = "com.atomjack.vcfp.intent.server";
			public final static String CLIENT = "com.atomjack.vcfp.intent.client";

			public final static String EXTRA_SERVER = "com.atomjack.vcfp.intent.extra_server";
			public final static String EXTRA_CLIENT = "com.atomjack.vcfp.intent.extra_client";


	};

	private static ConcurrentHashMap<String, PlexServer> plexmediaServers = new ConcurrentHashMap<String, PlexServer>();
    
	public static ConcurrentHashMap<String, PlexServer> getPlexMediaServers() {
		return plexmediaServers;
	}

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

	public static void addPlexServer(final PlexServer server) {
		Logger.d("ADDING PLEX SERVER: %s", server.name);
		if(server.name.equals("") || server.address.equals("")) {
			return;
		}
		if (!plexmediaServers.containsKey(server.name)) {
			try {
				String url = "http://" + server.address + ":" + server.port + "/library/sections/";
				AsyncHttpClient httpClient = new AsyncHttpClient();
				httpClient.get(url, new AsyncHttpResponseHandler() {
						@Override
						public void onSuccess(int statusCode, org.apache.http.Header[] headers, byte[] responseBody) {
//    		            Logger.d("HTTP REQUEST: %s", response);
								MediaContainer mc = new MediaContainer();
								try {
									mc = serial.read(MediaContainer.class, new String(responseBody, "UTF-8"));
								} catch (NotFoundException e) {
										e.printStackTrace();
								} catch (Exception e) {
										e.printStackTrace();
								}
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
								if(mc.directories != null)
									Logger.d("Directories: %d", mc.directories.size());
								else
									Logger.d("No directories found!");
								if(!server.name.equals("") && !server.address.equals("")) {
									plexmediaServers.putIfAbsent(server.name, server);
								}
						}
				});

			} catch (Exception e) {
				Logger.e("Exception getting clients: %s", e.toString());
			}
			Logger.d("Adding %s", server.name);
		} else {
			Logger.d("%s already added.", server.name);
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
}
