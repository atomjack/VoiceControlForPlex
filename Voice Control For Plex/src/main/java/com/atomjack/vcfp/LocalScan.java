package com.atomjack.vcfp;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ListView;

import com.atomjack.vcfp.adapters.PlexListAdapter;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpMediaContainerHandler;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocalScan {
	private Context context;
	private Class theClass;
	private Dialog searchDialog;
	private Dialog serverSelectDialog = null;
	private ScanHandler scanHandler;
	private SharedPreferences mPrefs;
	private Gson gson = new Gson();
	private Feedback feedback;
	private int serversScanned = 0;
	private Map<String, PlexClient> m_clients = new HashMap<String, PlexClient>();
	private PlexServer server;

	public LocalScan(Context ctx, Class cls, SharedPreferences prefs, ScanHandler handler) {
		context = ctx;
		theClass = cls;
		scanHandler = handler;
		mPrefs = prefs;
		feedback = new Feedback(mPrefs, context);
	}

	public void searchForPlexServers() {
		Logger.d("searchForPlexServers()");
		if(!VoiceControlForPlexApplication.isWifiConnected(context)) {
			VoiceControlForPlexApplication.showNoWifiDialog(context);
			return;
		}

		searchDialog = new Dialog(context);

		searchDialog.setContentView(R.layout.search_popup);
		searchDialog.setTitle(context.getResources().getString(R.string.searching_for_plex_servers));

		searchDialog.show();

		Intent mServiceIntent = new Intent(context, GDMService.class);
		mServiceIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		mServiceIntent.putExtra("ORIGIN", theClass.getSimpleName());
		mServiceIntent.putExtra("class", theClass);
		mServiceIntent.putExtra(VoiceControlForPlexApplication.Intent.SCAN_TYPE, "server");
		context.startService(mServiceIntent);
	}

	public void showPlexServers() {
		showPlexServers(null);
	}

	public void showPlexServers(ConcurrentHashMap<String, PlexServer> servers) {
		if(searchDialog != null)
			searchDialog.dismiss();
		if(serverSelectDialog == null) {
			serverSelectDialog = new Dialog(context);
		}
		serverSelectDialog.setContentView(R.layout.server_select);
		serverSelectDialog.setTitle("Select a Plex Server");
		serverSelectDialog.show();

		final ListView serverListView = (ListView)serverSelectDialog.findViewById(R.id.serverListView);
		if(servers == null)
			servers = new ConcurrentHashMap<String, PlexServer>(VoiceControlForPlexApplication.servers);
		final PlexListAdapter adapter = new PlexListAdapter(context, PlexListAdapter.TYPE_SERVER);
		adapter.setServers(servers);
		serverListView.setAdapter(adapter);
		serverListView.setOnItemClickListener(new ListView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parentAdapter, View view, int position, long id) {
				Logger.d("Clicked position %d", position);
				PlexServer s = (PlexServer)parentAdapter.getItemAtPosition(position);
				serverSelectDialog.dismiss();
				scanHandler.onDeviceSelected(s, false);
			}
		});
	}

	public void searchForPlexClients() {
		Logger.d("searchForPlexClients()");
		if(!VoiceControlForPlexApplication.isWifiConnected(context)) {
			VoiceControlForPlexApplication.showNoWifiDialog(context);
			return;
		}

		searchDialog = new Dialog(context);

		searchDialog.setContentView(R.layout.search_popup);
		searchDialog.setTitle(context.getResources().getString(R.string.searching_for_plex_clients));

		searchDialog.show();

		Intent mServiceIntent = new Intent(context, GDMService.class);
		mServiceIntent.putExtra("port", 32412); // Port for clients
		mServiceIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		mServiceIntent.putExtra("ORIGIN", theClass.getSimpleName());
		mServiceIntent.putExtra("class", theClass);
		mServiceIntent.putExtra(VoiceControlForPlexApplication.Intent.SCAN_TYPE, "client");
		context.startService(mServiceIntent);
	}

	public void hideSearchDialog() {
		if(searchDialog != null)
			searchDialog.dismiss();
	}

	public void showPlexClients(Map<String, PlexClient> clients) {
		showPlexClients(clients, false);
	}

	public void showPlexClients(Map<String, PlexClient> clients, boolean showResume) {
		if(searchDialog != null)
			searchDialog.dismiss();
		if(serverSelectDialog == null) {
			serverSelectDialog = new Dialog(context);
		}
		serverSelectDialog.setContentView(R.layout.server_select);
		serverSelectDialog.setTitle(R.string.select_plex_client);
		serverSelectDialog.show();

		if(showResume) {
			CheckBox resumeCheckbox = (CheckBox)serverSelectDialog.findViewById(R.id.serverListResume);
			resumeCheckbox.setVisibility(View.VISIBLE);
		}

		final ListView serverListView = (ListView)serverSelectDialog.findViewById(R.id.serverListView);
		final PlexListAdapter adapter = new PlexListAdapter(context, PlexListAdapter.TYPE_CLIENT);
		adapter.setClients(clients);
		serverListView.setAdapter(adapter);
		serverListView.setOnItemClickListener(new ListView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parentAdapter, View view, int position,
															long id) {
				PlexClient s = (PlexClient)parentAdapter.getItemAtPosition(position);
				serverSelectDialog.dismiss();
				CheckBox resumeCheckbox = (CheckBox)serverSelectDialog.findViewById(R.id.serverListResume);
				scanHandler.onDeviceSelected(s, resumeCheckbox.isChecked());
			}

		});
	}
}
