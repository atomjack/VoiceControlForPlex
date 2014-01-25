package com.atomjack.vcfpht;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import us.nineworlds.serenity.GDMReceiver;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources.NotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ListView;

import com.atomjack.vcfpht.MainListAdapter.SettingHolder;
import com.atomjack.vcfpht.model.MainSetting;
import com.atomjack.vcfpht.model.MediaContainer;
import com.atomjack.vcfpht.model.PlexClient;
import com.atomjack.vcfpht.model.PlexServer;
import com.google.gson.Gson;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.bugsense.trace.BugSenseHandler;

public class MainActivity extends Activity {
	public final static String PREFS = "VoiceControlForPlexHomeTheaterPrefs";
	public final static String TAG = "VoiceControlForPlexHomeTheater";
	
	public final static int FEEDBACK_VOICE = 0;
	public final static int FEEDBACK_TOAST = 1;

  public final static String BUGSENSE_APIKEY = "879458d0";

	private BroadcastReceiver gdmReceiver = new GDMReceiver();
	private Activity mCurrentActivity = null;
    
	private Dialog searchDialog = null;
	private Dialog serverSelectDialog = null;
	
	private PlexServer server = null;
	private PlexClient client = null;
	
	private Serializer serial = new Persister();
	
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
		this.server = (PlexServer)gson.fromJson(mPrefs.getString("Server", ""), PlexServer.class);
		this.client = (PlexClient)gson.fromJson(mPrefs.getString("Client", ""), PlexClient.class);
		
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
			new MainSetting("feedback", getResources().getString(R.string.feedback), mPrefs.getInt("feedback", 0) == FEEDBACK_VOICE ? getResources().getString(R.string.voice) : getResources().getString(R.string.toast))
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
				Log.v(TAG, "Clicked " + holder.tag);
				if(holder.tag.equals("server")) {
					searchForPlexServers();
				} else if(holder.tag.equals("client")) {
					getClients();
				} else if(holder.tag.equals("feedback")) {
					selectFeedback();
				}
			}
		});
		
		CheckBox resumeCheckbox = (CheckBox)findViewById(R.id.resumeCheckbox);
		Log.v(TAG, "checkbox: " + resumeCheckbox);
		resumeCheckbox.setChecked(mPrefs.getBoolean("resume", false));
	}
	
	public void settingRowHelpButtonClicked(View v) {
		String helpButtonClicked = v.getTag().toString();
		if(helpDialog == null) {
			helpDialog = new AlertDialog.Builder(MainActivity.this);
		}
		helpDialog.setTitle(R.string.app_name);
		/*
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this)
		.setTitle(R.string.app_name)
		.setMessage(R.string.about_text);

		alertDialog.show();
		*/
		
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
		Log.v(TAG, "ON NEW INTENT IN MAINACTIVITY");
		String from = intent.getStringExtra("FROM");
		Log.v(TAG, "From: " + from);
		if(from == null) {
		} else if(from.equals("GDMReceiver")) {
			Log.v(TAG, "Origin: " + intent.getStringExtra("ORIGIN"));
			String origin = intent.getStringExtra("ORIGIN") == null ? "" : intent.getStringExtra("ORIGIN");
			if(origin.equals("MainActivity")) {
				Log.v(TAG, "Got " + VoiceControlForPlexApplication.getPlexMediaServers().size() + " servers");
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
		Log.v(TAG, "ScanServersForClients, number of servers = " + servers.size());
		serversScanned = 0;
		for(PlexServer thisServer : servers.values()) {
			Log.v(TAG, "ScanServersForClients server: " + thisServer);
			try {
			    AsyncHttpClient httpClient = new AsyncHttpClient();
			    httpClient.get(thisServer.getClientsURL(), new AsyncHttpResponseHandler() {
			        @Override
			        public void onSuccess(String response) {
			        	serversScanned++;
//			            Log.v(TAG, "HTTP REQUEST: " + response);
			    		MediaContainer clientMC = new MediaContainer();
			    		
			    		try {
			    			clientMC = serial.read(MediaContainer.class, response);
			    		} catch (NotFoundException e) {
			                e.printStackTrace();
			            } catch (Exception e) {
			                e.printStackTrace();
			            }
			    		// Exclude non-Plex Home Theater clients (pre 1.0.7)
			    		List<PlexClient> clients = new ArrayList<PlexClient>();
			    		for(int i=0;i<clientMC.clients.size();i++) {
			    			float version = clientMC.clients.get(i).getNumericVersion();
			    			Log.v(MainActivity.TAG, "Version: " + version);
			    			if(version >= 1.07 || !clientMC.clients.get(i).getProduct().equals("Plex Home Theater")) {
			    				clients.add(clientMC.clients.get(i));
			    			}
			    		}
			    		
			    		searchDialog.dismiss();
			    		if(clients.size() == 0) {
			    			AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
			    			builder.setTitle("No Plex Home Theater Clients Found");
			    			builder.setCancelable(false)
			    				.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
			    			        public void onClick(DialogInterface dialog, int id) {
			    			            dialog.cancel();
			    			        }
			    				});
			    			AlertDialog d = builder.create();
			    			d.show();
			    		} else {
				            Log.v(TAG, "Clients: " + clients.size());
				            if(serversScanned == VoiceControlForPlexApplication.getPlexMediaServers().size()) {
				            	showPlexClients(clients);
				            }
			    		}
			        }
			    });

			} catch (Exception e) {
				Log.e(TAG, "Exception getting clients: " + e.toString());
			}
		}
	}

	private void showPlexServers() {
		Log.v(TAG, "servers: " + VoiceControlForPlexApplication.getPlexMediaServers().size());
		searchDialog.dismiss();
		if(serverSelectDialog == null) {
			serverSelectDialog = new Dialog(this);
		}
		serverSelectDialog.setContentView(R.layout.server_select);
		serverSelectDialog.setTitle("Select a Plex Server");
		serverSelectDialog.show();
		
		final ListView serverListView = (ListView)serverSelectDialog.findViewById(R.id.serverListView);
		ConcurrentHashMap<String, PlexServer> servers = new ConcurrentHashMap<String, PlexServer>(VoiceControlForPlexApplication.getPlexMediaServers());
		servers.put("Scan All", new PlexServer());
		final PlexListAdapter adapter = new PlexListAdapter(this, PlexListAdapter.TYPE_SERVER);
    adapter.setServers(servers);
		serverListView.setAdapter(adapter);
		serverListView.setOnItemClickListener(new ListView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parentAdapter, View view, int position,
					long id) {
				Log.v(TAG, "Clicked position " + position);
				PlexServer s = (PlexServer)parentAdapter.getItemAtPosition(position);
				serverSelectDialog.dismiss();
				setServer(s);
			}
			
		});
	}
	
	private void setServer(PlexServer server) {
		Log.v(TAG, "Setting Server " + server.getName());
		if(server.getAddress().equals("")) {
			this.server = null;
			saveSettings();
			initMainWithServer();
			return;
		}
		this.server = server;
		
		if(this.client == null) {
			try {
			    AsyncHttpClient httpClient = new AsyncHttpClient();
			    httpClient.get(server.getBaseURL(), new AsyncHttpResponseHandler() {
			        @Override
			        public void onSuccess(String response) {
			            Log.v(TAG, "HTTP REQUEST: " + response);
			            MediaContainer mc = new MediaContainer();
			            try {
			            	mc = serial.read(MediaContainer.class, response);
			            } catch (NotFoundException e) {
			                e.printStackTrace();
			            } catch (Exception e) {
			                e.printStackTrace();
			            }
			            Log.v(TAG, "Machine id: " + mc.getMachineIdentifier());
			            getClients(mc);
			        }
			    });
	
			} catch (Exception e) {
				Log.e(TAG, "Exception getting clients: " + e.toString());
			}
		} else {
			this.server = server;
			this.saveSettings();
			initMainWithServer();
		}
		
	}
	
	private void getClients() {
		if(this.server == null) {
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
		try {
		    AsyncHttpClient httpClient = new AsyncHttpClient();
		    httpClient.get(server.getClientsURL(), new AsyncHttpResponseHandler() {
		        @Override
		        public void onSuccess(String response) {
//		            Log.v(TAG, "HTTP REQUEST: " + response);
		    		MediaContainer clientMC = new MediaContainer();
		    		
		    		try {
		    			clientMC = serial.read(MediaContainer.class, response);
		    		} catch (NotFoundException e) {
		                e.printStackTrace();
		            } catch (Exception e) {
		                e.printStackTrace();
		            }
		    		// Exclude non-Plex Home Theater clients (pre 1.0.7)
		    		List<PlexClient> clients = new ArrayList<PlexClient>();
		    		for(int i=0;i<clientMC.clients.size();i++) {
		    			float version = clientMC.clients.get(i).getNumericVersion();
		    			Log.v(MainActivity.TAG, "Version: " + version);
		    			if(version >= 1.07) {
		    				clients.add(clientMC.clients.get(i));
		    			}
		    		}
		    		
		    		searchDialog.dismiss();
		    		if(clients.size() == 0) {
		    			AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
		    			builder.setTitle("No Plex Home Theater Clients Found");
		    			builder.setCancelable(false)
		    				.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
		    			        public void onClick(DialogInterface dialog, int id) {
		    			            dialog.cancel();
		    			        }
		    				});
		    			AlertDialog d = builder.create();
		    			d.show();
		    		} else {
			            Log.v(TAG, "Clients: " + clients.size());
			            
			            showPlexClients(clients);
		    		}
		        }
		    });

		} catch (Exception e) {
			Log.e(TAG, "Exception getting clients: " + e.toString());
		}
	}

	private void showPlexClients(List<PlexClient> clients) {
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
		Log.v(TAG, "Selected client: " + client.getName());
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
	
	public void onReceive(Context context, Intent intent) {
		String message = "Broadcast intent detected " + intent.getAction();
		Log.v(TAG, message);
	}
	
	public void onFinishedSearch() {
		Log.v(TAG, "done with search");
	}

	public Activity getCurrentActivity(){
        return mCurrentActivity;
	}
	
	public void setCurrentActivity(MainActivity mCurrentActivity){
		this.mCurrentActivity = mCurrentActivity;
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


