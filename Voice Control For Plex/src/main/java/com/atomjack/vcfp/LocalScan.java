package com.atomjack.vcfp;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

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
	private LocalScanHandler scanHandler;
	private SharedPreferences mPrefs;
	private Gson gson = new Gson();
	private Feedback feedback;
	private int serversScanned = 0;
	private Map<String, PlexClient> m_clients = new HashMap<String, PlexClient>();

	public LocalScan(Context ctx, Class cls, SharedPreferences prefs, LocalScanHandler handler) {
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
		mServiceIntent.putExtra("ORIGIN", theClass.getSimpleName());
		mServiceIntent.putExtra("class", theClass);
		context.startService(mServiceIntent);
	}

	public void showPlexServers() {
		Logger.d("servers: " + VoiceControlForPlexApplication.getPlexMediaServers().size());
		if(searchDialog != null)
			searchDialog.dismiss();
		if(serverSelectDialog == null) {
			serverSelectDialog = new Dialog(context);
		}
		serverSelectDialog.setContentView(R.layout.server_select);
		serverSelectDialog.setTitle("Select a Plex Server");
		serverSelectDialog.show();

		final ListView serverListView = (ListView)serverSelectDialog.findViewById(R.id.serverListView);
		ConcurrentHashMap<String, PlexServer> servers = new ConcurrentHashMap<String, PlexServer>(VoiceControlForPlexApplication.getPlexMediaServers());
		final PlexListAdapter adapter = new PlexListAdapter(context, PlexListAdapter.TYPE_SERVER);
		adapter.setServers(servers);
		serverListView.setAdapter(adapter);
		serverListView.setOnItemClickListener(new ListView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parentAdapter, View view, int position, long id) {
				Logger.d("Clicked position %d", position);
				PlexServer s = (PlexServer)parentAdapter.getItemAtPosition(position);
				serverSelectDialog.dismiss();
				scanHandler.onDeviceSelected(s);
			}
		});
	}

	public void getClients() {
		if(!VoiceControlForPlexApplication.isWifiConnected(context)) {
			VoiceControlForPlexApplication.showNoWifiDialog(context);
			return;
		}
		PlexServer server = gson.fromJson(mPrefs.getString("Server", ""), PlexServer.class);
		if(server == null || server.name.equals(context.getResources().getString(R.string.scan_all))) {
			scanForClients();
		} else {
			getClients(null);
		}
	}

	public void getClients(MediaContainer mc) {
		PlexServer server = gson.fromJson(mPrefs.getString("Server", ""), PlexServer.class);
		if(mc != null) {
			server.machineIdentifier = mc.machineIdentifier;
			SharedPreferences.Editor mPrefsEditor = mPrefs.edit();
			mPrefsEditor.putString("Server", gson.toJson(server));
			mPrefsEditor.commit();
		}
		if(searchDialog == null) {
			searchDialog = new Dialog(context);
		}

		searchDialog.setContentView(R.layout.search_popup);
		searchDialog.setTitle("Searching for Plex Clients");

		searchDialog.show();
		PlexHttpClient.get(server.getClientsURL(), null, new PlexHttpMediaContainerHandler() {
			@Override
			public void onSuccess(MediaContainer clientMC) {
				// Exclude non-Plex Home Theater clients (pre 1.0.7)
				Map<String, PlexClient> clients = new HashMap<String, PlexClient>();
				for (int i = 0; i < clientMC.clients.size(); i++) {
					if (!VoiceControlForPlexApplication.isVersionLessThan(clientMC.clients.get(i).version, VoiceControlForPlexApplication.MINIMUM_PHT_VERSION)) {
						clients.put(clientMC.clients.get(i).name, clientMC.clients.get(i));
					}
				}

				searchDialog.dismiss();
				if (clients.size() == 0) {
					AlertDialog.Builder builder = new AlertDialog.Builder(context);
					builder.setTitle("No Plex Clients Found");
					builder.setCancelable(false)
									.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
										public void onClick(DialogInterface dialog, int id) {
											dialog.cancel();
										}
									});
					AlertDialog d = builder.create();
					d.show();
				} else {
					Logger.d("Clients: " + clients.size());
					showPlexClients(clients);
				}
			}

			@Override
			public void onFailure(Throwable error) {
				searchDialog.dismiss();
				feedback.e(context.getResources().getString(R.string.got_error), error.getMessage());
			}
		});
	}

	public void scanForClients() {
		if(searchDialog == null) {
			searchDialog = new Dialog(context);
		}

		searchDialog.setContentView(R.layout.search_popup);
		searchDialog.setTitle("Searching for Plex Clients");

		searchDialog.show();
		Intent mServiceIntent = new Intent(context, GDMService.class);
		mServiceIntent.putExtra("ORIGIN", "ScanForClients");
		mServiceIntent.putExtra("class", theClass);
		context.startService(mServiceIntent);
	}

	public void showPlexClients(Map<String, PlexClient> clients) {
		if(serverSelectDialog == null) {
			serverSelectDialog = new Dialog(context);
		}
		serverSelectDialog.setContentView(R.layout.server_select);
		serverSelectDialog.setTitle("Select a Plex Client");
		serverSelectDialog.show();

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
				scanHandler.onDeviceSelected(s);
			}

		});
	}

	public void scanServersForClients() {
		ConcurrentHashMap<String, PlexServer> servers = VoiceControlForPlexApplication.getPlexMediaServers();
		Logger.d("ScanServersForClients, number of servers = " + servers.size());
		serversScanned = 0;
		for(PlexServer thisServer : servers.values()) {
			Logger.d("ScanServersForClients server: %s", thisServer.name);
			PlexHttpClient.get(thisServer.getClientsURL(), null, new PlexHttpMediaContainerHandler()
			{
				@Override
				public void onSuccess(MediaContainer clientMC)
				{
					serversScanned++;
					// Exclude non-Plex Home Theater clients (pre 1.0.7)
					Logger.d("clientMC size: %d", clientMC.clients.size());
					for(int i=0;i<clientMC.clients.size();i++) {
						if((!VoiceControlForPlexApplication.isVersionLessThan(clientMC.clients.get(i).version, VoiceControlForPlexApplication.MINIMUM_PHT_VERSION) || !clientMC.clients.get(i).product.equals("Plex Home Theater")) && !m_clients.containsKey(clientMC.clients.get(i).name)) {
							m_clients.put(clientMC.clients.get(i).name, clientMC.clients.get(i));
						}
					}

					if(serversScanned == VoiceControlForPlexApplication.getPlexMediaServers().size()) {
						searchDialog.dismiss();
						if(m_clients.size() == 0) {
							AlertDialog.Builder builder = new AlertDialog.Builder(context);
							builder.setTitle("No Plex Clients Found");
							builder.setCancelable(false)
											.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener()
											{
												public void onClick(DialogInterface dialog, int id)
												{
													dialog.cancel();
												}
											});
							AlertDialog d = builder.create();
							d.show();
						} else {
							Logger.d("Clients: " + m_clients.size());
							showPlexClients(m_clients);
						}
					}
				}

				@Override
				public void onFailure(Throwable error) {
					searchDialog.dismiss();
					feedback.e(context.getResources().getString(R.string.got_error), error.getMessage());
				}
			});
		}
	}
}
