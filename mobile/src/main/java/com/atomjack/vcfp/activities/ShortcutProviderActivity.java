package com.atomjack.vcfp.activities;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import com.atomjack.shared.Logger;
import com.atomjack.shared.Preferences;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.adapters.PlexListAdapter;
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

public class ShortcutProviderActivity extends AppCompatActivity implements DialogInterface.OnCancelListener {
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

  private  boolean cancelScan = false;
  private Dialog deviceSelectDialog;
  private boolean clientScanCanceled = false;

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
    chooserDialog.setOnCancelListener(this);
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

  public void showPlexServers(ConcurrentHashMap<String, PlexServer> servers, final ScanHandler scanHandler) {
    if(cancelScan) {
      cancelScan = false;
      return;
    }

    deviceSelectDialog = getDeviceSelectDialog(true, getResources().getString(R.string.select_plex_server));
    deviceSelectDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        finish();
      }
    });
    deviceSelectDialog.show();

    final ListView serverListView = (ListView) deviceSelectDialog.findViewById(R.id.serverListView);
    if(servers == null)
      servers = new ConcurrentHashMap<>(VoiceControlForPlexApplication.servers);
    final PlexListAdapter adapter = new PlexListAdapter(this, PlexListAdapter.TYPE_SERVER);
    adapter.setServers(servers);
    serverListView.setAdapter(adapter);
    serverListView.setOnItemClickListener(new ListView.OnItemClickListener() {

      @Override
      public void onItemClick(AdapterView<?> parentAdapter, View view, int position, long id) {
        Logger.d("Clicked position %d", position);
        PlexServer s = (PlexServer)parentAdapter.getItemAtPosition(position);
        deviceSelectDialog.dismiss();
        scanHandler.onDeviceSelected(s, false);
      }
    });
  }

  public Dialog getDeviceSelectDialog(boolean isServer, String title) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    LayoutInflater inflater = getLayoutInflater();
    View layout = inflater.inflate(R.layout.device_select, null);
    if(VoiceControlForPlexApplication.getInstance().unauthorizedLocalServersFound.size() > 0 && isServer) {
      layout.findViewById(R.id.unauthorizedLocalServerFoundFrameView).setVisibility(View.VISIBLE);
      if(VoiceControlForPlexApplication.getInstance().isLoggedIn()) {
        layout.findViewById(R.id.unauthorizedLocalServerFoundTextViewLoggedIn).setVisibility(View.VISIBLE);
        layout.findViewById(R.id.unauthorizedLocalServerFoundTextViewLoggedOut).setVisibility(View.INVISIBLE);
      } else {
        layout.findViewById(R.id.unauthorizedLocalServerFoundTextViewLoggedOut).setVisibility(View.VISIBLE);
        layout.findViewById(R.id.unauthorizedLocalServerFoundTextViewLoggedIn).setVisibility(View.INVISIBLE);
      }
    } else {
      layout.findViewById(R.id.unauthorizedLocalServerFoundFrameView).setVisibility(View.GONE);
    }
    builder.setView(layout);
    final TextView headerView = (TextView)layout.findViewById(R.id.deviceListHeader);
    headerView.setText(title);
    return builder.create();
  }

  public void showPlexClients(boolean showResume, final ScanHandler onFinish) {
    if(cancelScan) {
      cancelScan = false;
      return;
    }
    deviceSelectDialog = getDeviceSelectDialog(false, getString(R.string.select_plex_client));


    deviceSelectDialog.setOnCancelListener(this);
    deviceSelectDialog.show();
    if(deviceSelectDialog.findViewById(R.id.unauthorizedLocalServerFoundFrameView) != null)
      deviceSelectDialog.findViewById(R.id.unauthorizedLocalServerFoundFrameView).setVisibility(View.GONE);

    if (showResume) {
      CheckBox resumeCheckbox = (CheckBox) deviceSelectDialog.findViewById(R.id.serverListResume);
      resumeCheckbox.setVisibility(View.VISIBLE);
    }

    final ListView clientListView = (ListView) deviceSelectDialog.findViewById(R.id.serverListView);
    final PlexListAdapter adapter = new PlexListAdapter(this, PlexListAdapter.TYPE_CLIENT);
    adapter.setClients(VoiceControlForPlexApplication.getAllClients());
    clientListView.setAdapter(adapter);
    clientListView.setOnItemClickListener(new ListView.OnItemClickListener() {

      @Override
      public void onItemClick(AdapterView<?> parentAdapter, View view, int position,
                              long id) {
        PlexClient s = (PlexClient) parentAdapter.getItemAtPosition(position);
        deviceSelectDialog.dismiss();
        CheckBox resumeCheckbox = (CheckBox) deviceSelectDialog.findViewById(R.id.serverListResume);
        if (onFinish != null)
          onFinish.onDeviceSelected(s, resumeCheckbox.isChecked());
      }

    });
  }

  @Override
  public void onCancel(DialogInterface dialog) {
    finish();
  }
}
