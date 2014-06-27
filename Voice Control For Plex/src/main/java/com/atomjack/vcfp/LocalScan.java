package com.atomjack.vcfp;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ListView;

import com.atomjack.vcfp.adapters.PlexListAdapter;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexServer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocalScan {
	private Context context;
	private Class theClass;
	private Dialog searchDialog;
	private Dialog serverSelectDialog = null;
	private ScanHandler scanHandler;

	public LocalScan(Context ctx, Class cls, ScanHandler handler) {
		context = ctx;
		theClass = cls;
		scanHandler = handler;
	}

	public void searchForPlexServers() {
		searchForPlexServers(false);
	}

	public void searchForPlexServers(boolean silent) {
		Logger.d("searchForPlexServers()");
		if(!VoiceControlForPlexApplication.isWifiConnected(context)) {
			VoiceControlForPlexApplication.showNoWifiDialog(context);
			return;
		}

		if(!silent) {
			searchDialog = new Dialog(context);

			searchDialog.setContentView(R.layout.search_popup);
			searchDialog.setTitle(context.getResources().getString(R.string.searching_for_plex_servers));

			searchDialog.show();
		}

		Intent mServiceIntent = new Intent(context, GDMService.class);
		mServiceIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		mServiceIntent.putExtra("ORIGIN", theClass.getSimpleName());
		mServiceIntent.putExtra(VoiceControlForPlexApplication.Intent.EXTRA_SILENT, silent);
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
		showPlexClients(clients, showResume, null);
	}

	public void showPlexClients(Map<String, PlexClient> clients, boolean showResume, final ScanHandler onFinish) {
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

		// Add any chromecasts we've found
		clients.putAll(VoiceControlForPlexApplication.castClients);


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
				if(onFinish == null)
					scanHandler.onDeviceSelected(s, resumeCheckbox.isChecked());
				else
					onFinish.onDeviceSelected(s, resumeCheckbox.isChecked());
			}

		});
	}
}
