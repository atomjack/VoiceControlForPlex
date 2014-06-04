package com.atomjack.vcfp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexDevice;
import com.atomjack.vcfp.model.PlexServer;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import us.nineworlds.serenity.GDMReceiver;

public class ShortcutProviderActivity extends Activity {
	private LocalScan localScan;

	private Gson gson = new Gson();

	private BroadcastReceiver gdmReceiver = new GDMReceiver();

	private PlexServer server;
	private PlexClient client;
	ConcurrentHashMap<String, PlexServer> servers;
	HashMap<String, PlexClient> clients;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Logger.d("ShortcutProviderActivity onCreate");

		String from = getIntent().getStringExtra("FROM");
		Logger.d("from: %s", from);

		localScan = new LocalScan(this, ShortcutProviderActivity.class, null, new ScanHandler() {
			@Override
			public void onDeviceSelected(PlexDevice device) {
				Logger.d("chose %s", device.name);
				if(device instanceof PlexServer) {
					server = (PlexServer)device;
					localScan.showPlexClients(clients);
				} else if(device instanceof PlexClient) {
					client = (PlexClient)device;
					createShortcut(false);
				}
			}
		});

		SharedPreferences mPrefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);

		Type serverType = new TypeToken<ConcurrentHashMap<String, PlexServer>>(){}.getType();
		Type clientType = new TypeToken<HashMap<String, PlexClient>>(){}.getType();
		servers = gson.fromJson(mPrefs.getString(VoiceControlForPlexApplication.Pref.SAVED_SERVERS, ""), serverType);
		clients = gson.fromJson(mPrefs.getString(VoiceControlForPlexApplication.Pref.SAVED_CLIENTS, ""), clientType);

		Logger.d("server: %s", servers);
		boolean didScan = false;
		if(servers != null && servers.size() > 0 && clients != null && clients.size() > 0)
			didScan = true;

		AlertDialog.Builder chooserDialog = new AlertDialog.Builder(ShortcutProviderActivity.this);
		chooserDialog.setTitle(R.string.create_shortcut);
		chooserDialog.setMessage(didScan ? R.string.create_shortcut_blurb : R.string.create_shortcut_blurb_no_servers);
		if(didScan) {
			chooserDialog.setPositiveButton(R.string.specify_now, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.dismiss();
					localScan.showPlexServers(servers);
//					selectServer();
				}
			});
		}
		chooserDialog.setNeutralButton(R.string.use_current, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
				createShortcut(true);
			}
		});
		chooserDialog.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
				finish();
			}
		});
		chooserDialog.show();
	}

	private void createShortcut(boolean use_current) {
		Logger.d("Creating shortcut.");
		Intent.ShortcutIconResource icon = Intent.ShortcutIconResource.fromContext(this, R.drawable.ic_launcher);

		Intent sendIntent = new Intent();

		Intent launchIntent = new Intent(this, ShortcutActivity.class);
		if(!use_current) {
			Logger.d("setting client to %s", client.name);
			launchIntent.putExtra(VoiceControlForPlexApplication.Intent.EXTRA_SERVER, gson.toJson(server));
			launchIntent.putExtra(VoiceControlForPlexApplication.Intent.EXTRA_CLIENT, gson.toJson(client));
			String label = server.name.equals(client.name) ? server.name : (server.owned ? server.name : server.sourceTitle) + "/" + client.name;
			sendIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, label);
		} else
			sendIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getResources().getString(R.string.app_name));

		sendIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent);

		sendIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, icon);

		setResult(RESULT_OK, sendIntent);

		finish();
	}
	/*
	@Override
	public void onNewIntent(Intent intent) {
		Logger.d("on new intent in ShortcutProvider");
		Logger.d("Got " + VoiceControlForPlexApplication.getPlexMediaServers().size() + " servers");

		Intent.ShortcutIconResource icon = Intent.ShortcutIconResource.fromContext(this, R.drawable.ic_launcher);

		Intent sendIntent = new Intent();

		Intent launchIntent = new Intent(this, ShortcutActivity.class);

		sendIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent);
		sendIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getResources().getString(R.string.app_name));
		sendIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, icon);

		setResult(RESULT_OK, sendIntent);

		finish();
	}
*/

	@Override
	protected void onDestroy() {
		if(gdmReceiver != null) {
			LocalBroadcastManager.getInstance(this).unregisterReceiver(gdmReceiver);
		}
//		feedback.destroy();
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if(gdmReceiver != null) {
			LocalBroadcastManager.getInstance(this).unregisterReceiver(gdmReceiver);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if(gdmReceiver != null) {
			IntentFilter filters = new IntentFilter();
			filters.addAction(GDMService.MSG_RECEIVED);
			filters.addAction(GDMService.SOCKET_CLOSED);
			LocalBroadcastManager.getInstance(this).registerReceiver(gdmReceiver,
							filters);
		}
	}
}
