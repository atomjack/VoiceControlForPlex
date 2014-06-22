package com.atomjack.vcfp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources.NotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v7.media.MediaRouter;

import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexServer;
import com.google.android.gms.cast.CastDevice;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

public class VoiceControlForPlexApplication extends Application
{
	public final static String MINIMUM_PHT_VERSION = "1.0.7";

	private static boolean isApplicationVisible;

	public final static class Intent {
			public final static String GDMRECEIVE = "com.atomjack.vcfp.intent.gdmreceive";

			public final static String EXTRA_SERVER = "com.atomjack.vcfp.intent.extra_server";
			public final static String EXTRA_CLIENT = "com.atomjack.vcfp.intent.extra_client";
			public final static String EXTRA_RESUME = "com.atomjack.vcfp.intent.extra_resume";
			public final static String EXTRA_SILENT = "com.atomjack.vcfp.intent.extra_silent";


			public final static String SCAN_TYPE = "com.atomjack.vcfp.intent.scan_type";
			public final static String EXTRA_SERVERS = "com.atomjack.vcfp.intent.extra_servers";
			public final static String EXTRA_CLIENTS = "com.atomjack.vcfp.intent.extra_clients";
			public final static String ARGUMENTS = "com.atomjack.vcfp.intent.ARGUMENTS";

			public final static String SHOWRESOURCE = "com.atomjack.vcfp.intent.SHOWRESOURCE";

			public final static String CAST_MEDIA = "com.atomjack.vcfp.intent.CAST_MEDIA";
			public final static String EXTRA_MEDIA = "com.atomjack.vcfp.intent.EXTRA_MEDIA";
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

	public static String getUUID(SharedPreferences mPrefs) {
		String uuid = mPrefs.getString(Preferences.UUID, null);
		if(uuid == null) {
			uuid = UUID.randomUUID().toString();
			mPrefs.edit().putString(Preferences.UUID, uuid);
			mPrefs.edit().commit();
		}
		return uuid;
	}

	public static void addPlexServer(final PlexServer server) {
		addPlexServer(server, null);
	}

	public static void addPlexServer(final PlexServer server, final Runnable onFinish) {
		Logger.d("ADDING PLEX SERVER: %s, %s", server.name, server.address);
		if(server.name.equals("") || server.address.equals("")) {
			return;
		}
		if (!servers.containsKey(server.name)) {
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
									Logger.d("Added %s.", server.name);
								}
								if(onFinish != null)
									onFinish.run();
							}
						});
					}

					@Override
					public void onFailure(int statusCode) {
						// TODO: Handle failure here
						Logger.d("Failed to find connection for %s: %d", server.name, statusCode);
					}
				});


			} catch (Exception e) {
				Logger.e("Exception getting clients: %s", e.toString());
			}
		} else {
			// Copy over the sections from the locally found server. We'll want to include the Connections that belong to the server that come from plex.tv
			PlexServer thatServer = servers.get(server.name);
			server.musicSections = thatServer.musicSections;
			server.movieSections = thatServer.movieSections;
			server.tvSections = thatServer.tvSections;
			servers.put(thatServer.name, thatServer);
			Logger.d("%s already added.", thatServer.name);
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
}
