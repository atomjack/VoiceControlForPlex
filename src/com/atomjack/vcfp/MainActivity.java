package com.atomjack.vcfp;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import us.nineworlds.serenity.GDMReceiver;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ListView;

import com.atomjack.vcfp.MainListAdapter.SettingHolder;
import com.atomjack.vcfp.model.MainSetting;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpMediaContainerHandler;
import com.google.gson.Gson;
import com.bugsense.trace.BugSenseHandler;

public class MainActivity extends Activity {
	public final static String PREFS = "VoiceControlForPlexPrefs";

	public final static int FEEDBACK_VOICE = 0;
	public final static int FEEDBACK_TOAST = 1;

  public final static String BUGSENSE_APIKEY = "879458d0";

	private BroadcastReceiver gdmReceiver = new GDMReceiver();

	private Dialog searchDialog = null;
	private Dialog serverSelectDialog = null;
	
	private PlexServer server = null;
	private PlexClient client = null;

  private Map<String, PlexClient> m_clients = new HashMap<String, PlexClient>();

	private SharedPreferences mPrefs;
	private SharedPreferences.Editor mPrefsEditor;
	
	private int serversScanned = 0;
	AlertDialog.Builder helpDialog;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

    BugSenseHandler.initAndStartSession(MainActivity.this, BUGSENSE_APIKEY);
		
		
		mPrefs = getSharedPreferences(PREFS, MODE_PRIVATE);
		mPrefsEditor = mPrefs.edit();
		Gson gson = new Gson();
		
		setContentView(R.layout.main);
		this.server = gson.fromJson(mPrefs.getString("Server", ""), PlexServer.class);
		this.client = gson.fromJson(mPrefs.getString("Client", ""), PlexClient.class);
		
		initMainWithServer();
	}
	
	public void resumeChecked(View v) {
		mPrefsEditor.putBoolean("resume", ((CheckBox)v).isChecked());
		mPrefsEditor.commit();
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_about:
			return showAbout();
		case R.id.menu_donate:
			Intent intent = new Intent(Intent.ACTION_VIEW, 
					Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=UJF9QY9QELERG"));
			startActivity(intent);
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private boolean showAbout() {
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this)
		.setTitle(R.string.app_name)
		.setMessage(R.string.about_text);

		alertDialog.show();

		return true;
	}

	private void initMainWithServer() {
		setContentView(R.layout.main);
		
		MainSetting setting_data[] = new MainSetting[] {
			new MainSetting("server", getResources().getString(R.string.stream_video_from_server), this.server != null ? this.server.getName() : getResources().getString(R.string.scan_all)),
			new MainSetting("client", getResources().getString(R.string.to_the_client), this.client != null ? this.client.getName() : getResources().getString(R.string.not_set)),
//			new MainSetting("feedback", getResources().getString(R.string.feedback), mPrefs.getInt("feedback", 0) == FEEDBACK_VOICE ? getResources().getString(R.string.voice) : getResources().getString(R.string.toast))
		};
		
		MainListAdapter adapter = new MainListAdapter(this, R.layout.main_setting_item_row, setting_data);
		
		ListView listView1 = (ListView)findViewById(R.id.listView1);
		listView1.setFooterDividersEnabled(true);
		listView1.addFooterView(new View(listView1.getContext()));
		listView1.setAdapter(adapter);
		listView1.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapter, View view, int position,
					long arg3) {
				SettingHolder holder = (SettingHolder)view.getTag();
				Logger.d("Clicked %s", holder.tag);
				if(holder.tag.equals("server")) {
					searchForPlexServers();
				} else if(holder.tag.equals("client")) {
					getClients();
//				} else if(holder.tag.equals("feedback")) {
//					selectFeedback();
				}
			}
		});
		
		CheckBox resumeCheckbox = (CheckBox)findViewById(R.id.resumeCheckbox);
    Logger.d("Checkbox: %s", resumeCheckbox);
		resumeCheckbox.setChecked(mPrefs.getBoolean("resume", false));
	}
	
	public void settingRowHelpButtonClicked(View v) {
		String helpButtonClicked = v.getTag().toString();
		if(helpDialog == null) {
			helpDialog = new AlertDialog.Builder(MainActivity.this);
		}
		helpDialog.setTitle(R.string.app_name);

		if(helpButtonClicked.equals("server")) {
			helpDialog.setMessage(R.string.help_server);
		} else if(helpButtonClicked.equals("client")) {
			helpDialog.setMessage(R.string.help_client);
		} else if(helpButtonClicked.equals("feedback")) {
			helpDialog.setMessage(R.string.help_feedback);
		}
		helpDialog.show();
	}
	
	private void selectFeedback() {
		AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
		builder.setTitle("Feedback");
		builder.setCancelable(false)
			.setPositiveButton(R.string.feedback_voice, new DialogInterface.OnClickListener() {
		        public void onClick(DialogInterface dialog, int id) {
		        	mPrefsEditor.putInt("feedback", FEEDBACK_VOICE);
		        	mPrefsEditor.commit();
		        	initMainWithServer();
		        }
			}).setNegativeButton(R.string.feedback_toast, new DialogInterface.OnClickListener() {
		        public void onClick(DialogInterface dialog, int id) {
		        	mPrefsEditor.putInt("feedback", FEEDBACK_TOAST);
		        	mPrefsEditor.commit();
		        	initMainWithServer();
		        }
			});
		AlertDialog d = builder.create();
		d.show();
	}
	
	public void showUsageExamples(View v) {
		AlertDialog.Builder usageDialog = new AlertDialog.Builder(MainActivity.this);
		usageDialog.setTitle("Usage Examples");
		usageDialog.setMessage(R.string.help_usage);
		usageDialog.setPositiveButton("Got it", new DialogInterface.OnClickListener() {
	        public void onClick(DialogInterface dialog, int id) {
	        	dialog.dismiss();
	        }
		});
		usageDialog.show();
	}
	
	private void searchForPlexServers() {
		searchDialog = new Dialog(this);
		
		searchDialog.setContentView(R.layout.search_popup);
		searchDialog.setTitle("Searching for Plex Servers");
		
		searchDialog.show();
		
		Intent mServiceIntent = new Intent(this, GDMService.class);
		mServiceIntent.putExtra("ORIGIN", "MainActivity");
		startService(mServiceIntent);
	}
	
	@Override
	public void onNewIntent(Intent intent) {
		Logger.d("ON NEW INTENT IN MAINACTIVITY");
		String from = intent.getStringExtra("FROM");
		Logger.d("From: %s", from);
		if(from == null) {
		} else if(from.equals("GDMReceiver")) {
			Logger.d("Origin: " + intent.getStringExtra("ORIGIN"));
			String origin = intent.getStringExtra("ORIGIN") == null ? "" : intent.getStringExtra("ORIGIN");
			if(origin.equals("MainActivity")) {
				Logger.d("Got " + VoiceControlForPlexApplication.getPlexMediaServers().size() + " servers");
				if(VoiceControlForPlexApplication.getPlexMediaServers().size() > 0) {
					showPlexServers();
				} else {
					searchDialog.hide();
					AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
	    			builder.setTitle("No Plex Servers Found");
	    			builder.setCancelable(false)
	    				.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
	    			        public void onClick(DialogInterface dialog, int id) {
	    			            dialog.cancel();
	    			        }
	    				});
	    			AlertDialog d = builder.create();
	    			d.show();
				}
			} else if(origin.equals("ScanForClients")) {
				// No default server specified, so we need to search all servers for all clients
				scanServersForClients();
			}
		}
	}
	
	private void scanServersForClients() {
		ConcurrentHashMap<String, PlexServer> servers = VoiceControlForPlexApplication.getPlexMediaServers();
		Logger.d("ScanServersForClients, number of servers = " + servers.size());
		serversScanned = 0;
		for(PlexServer thisServer : servers.values()) {
			Logger.d("ScanServersForClients server: %s", thisServer.getName());
      PlexHttpClient.get(thisServer.getClientsURL(), null, new PlexHttpMediaContainerHandler()
      {
        @Override
        public void onSuccess(MediaContainer clientMC)
        {
          serversScanned++;
          // Exclude non-Plex Home Theater clients (pre 1.0.7)
          Logger.d("clientMC size: %d", clientMC.clients.size());
          for(int i=0;i<clientMC.clients.size();i++) {
            float version = clientMC.clients.get(i).getNumericVersion();
            Logger.d("Version: %f", version);
            if((version >= 1.07 || !clientMC.clients.get(i).getProduct().equals("Plex Home Theater")) && !m_clients.containsKey(clientMC.clients.get(i).getName())) {
              m_clients.put(clientMC.clients.get(i).getName(), clientMC.clients.get(i));
            }
          }

          if(serversScanned == VoiceControlForPlexApplication.getPlexMediaServers().size()) {
            searchDialog.dismiss();
            if(m_clients.size() == 0) {
              AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
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
      });
		}
	}

	private void showPlexServers() {
		Logger.d("servers: " + VoiceControlForPlexApplication.getPlexMediaServers().size());
		searchDialog.dismiss();
		if(serverSelectDialog == null) {
			serverSelectDialog = new Dialog(this);
		}
		serverSelectDialog.setContentView(R.layout.server_select);
		serverSelectDialog.setTitle("Select a Plex Server");
		serverSelectDialog.show();
		
		final ListView serverListView = (ListView)serverSelectDialog.findViewById(R.id.serverListView);
		ConcurrentHashMap<String, PlexServer> servers = new ConcurrentHashMap<String, PlexServer>(VoiceControlForPlexApplication.getPlexMediaServers());
		final PlexListAdapter adapter = new PlexListAdapter(this, PlexListAdapter.TYPE_SERVER);
    adapter.setServers(servers);
		serverListView.setAdapter(adapter);
		serverListView.setOnItemClickListener(new ListView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parentAdapter, View view, int position, long id) {
				Logger.d("Clicked position %d", position);
				PlexServer s = (PlexServer)parentAdapter.getItemAtPosition(position);
				serverSelectDialog.dismiss();
				setServer(s);
			}

		});
	}
	
	private void setServer(PlexServer server) {
		Logger.d("Setting Server %s", server.getName());
		if(server.getName().equals("")) {
			this.server = null;
			saveSettings();
			initMainWithServer();
			return;
		}
		this.server = server;
		
		if(this.client == null) {
      PlexHttpClient.get(server.getBaseURL(), null, new PlexHttpMediaContainerHandler()
      {
        @Override
        public void onSuccess(MediaContainer mediaContainer)
        {
          Logger.d("Machine id: " + mediaContainer.getMachineIdentifier());
          getClients(mediaContainer);
        }
      });
		} else {
			this.server = server;
			this.saveSettings();
			initMainWithServer();
		}
		
	}
	
	private void getClients() {
		if(server == null || server.getName().equals(getResources().getString(R.string.scan_all))) {
			scanForClients();
		} else {
			getClients(null);
		}
	}
	
	private void scanForClients() {
		if(searchDialog == null) {
			searchDialog = new Dialog(this);
		}
		
		searchDialog.setContentView(R.layout.search_popup);
		searchDialog.setTitle("Searching for Plex Clients");
		
		searchDialog.show();
		Intent mServiceIntent = new Intent(this, GDMService.class);
		mServiceIntent.putExtra("ORIGIN", "ScanForClients");
		startService(mServiceIntent);
	}

	private void getClients(MediaContainer mc) {
		if(mc != null) {
			this.server.setMachineIdentifier(mc.getMachineIdentifier());
			saveSettings();
		}
		if(searchDialog == null) {
			searchDialog = new Dialog(this);
		}
		
		searchDialog.setContentView(R.layout.search_popup);
		searchDialog.setTitle("Searching for Plex Clients");
		
		searchDialog.show();
    PlexHttpClient.get(server.getClientsURL(), null, new PlexHttpMediaContainerHandler()
    {
      @Override
      public void onSuccess(MediaContainer clientMC)
      {
        // Exclude non-Plex Home Theater clients (pre 1.0.7)
        Map<String, PlexClient> clients = new HashMap<String, PlexClient>();
        for (int i = 0; i < clientMC.clients.size(); i++)
        {
          float version = clientMC.clients.get(i).getNumericVersion();
          Logger.d("Version: %f", version);
          if (version >= 1.07)
          {
            clients.put(clientMC.clients.get(i).getName(), clientMC.clients.get(i));
          }
        }

        searchDialog.dismiss();
        if (clients.size() == 0)
        {
          AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
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
        } else
        {
          Logger.d("Clients: " + clients.size());

          showPlexClients(clients);
        }
      }
    });
	}

	private void showPlexClients(Map<String, PlexClient> clients) {
		if(serverSelectDialog == null) {
			serverSelectDialog = new Dialog(this);
		}
		serverSelectDialog.setContentView(R.layout.server_select);
		serverSelectDialog.setTitle("Select a Plex Client");
		serverSelectDialog.show();
		
		final ListView serverListView = (ListView)serverSelectDialog.findViewById(R.id.serverListView);
		final PlexListAdapter adapter = new PlexListAdapter(this, PlexListAdapter.TYPE_CLIENT);
    adapter.setClients(clients);
		serverListView.setAdapter(adapter);
		serverListView.setOnItemClickListener(new ListView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parentAdapter, View view, int position,
					long id) {
				PlexClient s = (PlexClient)parentAdapter.getItemAtPosition(position);
				serverSelectDialog.dismiss();
				setClient(s);
			}
			
		});
	}

	private void setClient(PlexClient client) {
		this.client = client;
		Logger.d("Selected client: " + client.getName());
		saveSettings();
		initMainWithServer();
	}
	
	private void saveSettings() {
		Gson gson = new Gson();
		mPrefsEditor.putString("Server", gson.toJson(this.server));
		mPrefsEditor.putString("Client", gson.toJson(this.client));
		mPrefsEditor.putBoolean("resume", mPrefs.getBoolean("resume", false));
		mPrefsEditor.commit();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
    protected void onDestroy() {
        super.onDestroy();
        if(gdmReceiver != null) {
        	LocalBroadcastManager.getInstance(this).unregisterReceiver(gdmReceiver);
        }
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


