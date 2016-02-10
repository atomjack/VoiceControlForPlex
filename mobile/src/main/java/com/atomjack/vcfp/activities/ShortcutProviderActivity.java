package com.atomjack.vcfp.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.atomjack.shared.Logger;
import com.atomjack.shared.Preferences;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.interfaces.ScanHandler;
import com.atomjack.shared.UriDeserializer;
import com.atomjack.shared.UriSerializer;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexDevice;
import com.atomjack.vcfp.model.PlexServer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class ShortcutProviderActivity extends VCFPActivity {
	private Gson gsonWrite = new GsonBuilder()
					.registerTypeAdapter(Uri.class, new UriSerializer())
					.create();
	private Gson gsonRead = new GsonBuilder()
					.registerTypeAdapter(Uri.class, new UriDeserializer())
					.create();

	private PlexServer server;
	private PlexClient client;
	ConcurrentHashMap<String, PlexServer> servers;
	HashMap<String, PlexClient> clients;

	private boolean resume = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Logger.d("ShortcutProviderActivity onCreate");

		Type serverType = new TypeToken<ConcurrentHashMap<String, PlexServer>>(){}.getType();
		Type clientType = new TypeToken<HashMap<String, PlexClient>>(){}.getType();
		servers = gsonRead.fromJson(VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.SAVED_SERVERS, ""), serverType);
		clients = gsonRead.fromJson(VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.SAVED_CLIENTS, ""), clientType);

		Logger.d("servers: %s", servers);
		boolean didScan = false;
		if(servers != null && servers.size() > 0 && VoiceControlForPlexApplication.getAllClients().size() > 0)
			didScan = true;

		AlertDialog.Builder chooserDialog = new AlertDialog.Builder(ShortcutProviderActivity.this);
		chooserDialog.setTitle(R.string.create_shortcut);
		chooserDialog.setMessage(didScan ? R.string.create_shortcut_blurb : R.string.create_shortcut_blurb_no_servers);
		if(didScan) {
			chooserDialog.setPositiveButton(R.string.specify_now, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.dismiss();
          showPlexServers(servers, new ScanHandler() {
            @Override
            public void onDeviceSelected(PlexDevice device, boolean unused) {
              // Set the server that was selected
              server = (PlexServer) device;
              showPlexClients(true, new ScanHandler() {
                @Override
                public void onDeviceSelected(PlexDevice device, boolean _resume) {
                  // Set the client that was selected, and whether or not to resume
                  client = (PlexClient)device;
                  Logger.d("resume is set to %s", _resume);
                  resume = _resume;
                  createShortcut(false);
                }
              });
            }
          });
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
			launchIntent.putExtra(com.atomjack.shared.Intent.EXTRA_SERVER, gsonWrite.toJson(server));
			launchIntent.putExtra(com.atomjack.shared.Intent.EXTRA_CLIENT, gsonWrite.toJson(client));
			launchIntent.putExtra(com.atomjack.shared.Intent.EXTRA_RESUME, resume);
			String label = server.name.equals(client.name) ? server.name : (server.owned ? server.name : server.sourceTitle) + "/" + client.name;
			sendIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, label);
		} else {
      launchIntent.putExtra(com.atomjack.shared.Intent.USE_CURRENT, true);
      sendIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getResources().getString(R.string.app_name));
    }

		sendIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent);

		sendIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, icon);

		setResult(RESULT_OK, sendIntent);

		finish();
	}

	@Override
	protected void onDestroy() {
//		feedback.destroy();
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
	}
}
