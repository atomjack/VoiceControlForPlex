package com.atomjack.googlesearchplexcontrol;

import java.util.ArrayList;
import java.util.List;

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
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;

import com.atomjack.googlesearchplexcontrol.model.MainSetting;
import com.atomjack.googlesearchplexcontrol.model.MediaContainer;
import com.atomjack.googlesearchplexcontrol.model.PlexClient;
import com.atomjack.googlesearchplexcontrol.model.PlexServer;
import com.google.gson.Gson;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

public class MainActivity extends Activity {
	public final static String PREFS = "GoogleSearchPlexControlPrefs";
	public final static String TAG = "GoogleSearchPlexControl";
	public final static String SEARCH_TYPE = "com.atomjack.googlesearchplexcontrol.SEARCH_TYPE";
	
	Button serverSelectButton = null;
	Button clientSelectButton = null;
	
	private BroadcastReceiver gdmReceiver = new GDMReceiver();
	private Activity mCurrentActivity = null;
    
	private Dialog searchDialog = null;
	private Dialog serverSelectDialog = null;
	
	private PlexServer server = null;
	private PlexClient client = null;
	
	private Serializer serial = new Persister();
	
	private SharedPreferences mPrefs;
	private SharedPreferences.Editor mPrefsEditor;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.v(TAG, "on create MainActivity");
		super.onCreate(savedInstanceState);
		
		
		mPrefs = getSharedPreferences(PREFS, MODE_PRIVATE);
		mPrefsEditor = mPrefs.edit();
		Gson gson = new Gson();
		String json = mPrefs.getString("Server", "");
		PlexServer s = (PlexServer)gson.fromJson(json, PlexServer.class);
		if(s == null) {
			Log.v(TAG, "Server is null");
			searchForPlexServers();
			setContentView(R.layout.main_without_server);
			serverSelectButton = (Button)findViewById(R.id.searchForServersButton);
			serverSelectButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					searchForPlexServers();
				}
			});
			
			
			
		} else {
			this.server = s;
			this.client = (PlexClient)gson.fromJson(mPrefs.getString("Client", ""), PlexClient.class);
			Log.v(TAG, "Server: " + s.getMachineIdentifier());
			initMainWithServer();
		}
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
		setContentView(R.layout.main_with_server);
		
		MainSetting setting_data[] = new MainSetting[] {
			new MainSetting("Stream from the server", this.server.getName()),
			new MainSetting("To the client", this.client.getName())
		};
		
		MainListAdapter adapter = new MainListAdapter(this, R.layout.main_setting_item_row, setting_data);
		
		ListView listView1 = (ListView)findViewById(R.id.listView1);
		
		listView1.setAdapter(adapter);
		listView1.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapter, View view, int position,
					long arg3) {
				if(position == 0) {
					searchForPlexServers();
				} else if(position == 1) {
					getClients();
				}
			}
		});
		
		CheckBox resumeCheckbox = (CheckBox)findViewById(R.id.resumeCheckbox);
		Log.v(TAG, "checkbox: " + resumeCheckbox);
		resumeCheckbox.setChecked(mPrefs.getBoolean("resume", false));
	}
	
	private void searchForPlexServers() {
		searchDialog = new Dialog(this);
		
		searchDialog.setContentView(R.layout.search_popup);
		searchDialog.setTitle("Searching for Plex Servers");
		
		searchDialog.show();
		
		Intent mServiceIntent = new Intent(this, GDMService.class);
		startService(mServiceIntent);
	}
	
	@Override
	public void onNewIntent(Intent intent) {
		Log.v(TAG, "ON NEW INTENT");
		String from = intent.getStringExtra("FROM");
		if(from == null) {
		} else if(from.equals("GDMReceiver")) {
			Log.v(TAG, "Got " + GoogleSearchPlexControlApplication.getPlexMediaServers().size() + " servers");
			if(GoogleSearchPlexControlApplication.getPlexMediaServers().size() > 0) {
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
		}
	}
	
	private void showPlexServers() {
		Log.v(TAG, "servers: " + GoogleSearchPlexControlApplication.getPlexMediaServers().size());
		searchDialog.dismiss();
		if(serverSelectDialog == null) {
			serverSelectDialog = new Dialog(this);
		}
		serverSelectDialog.setContentView(R.layout.server_select);
		serverSelectDialog.setTitle("Select a Plex Server");
		serverSelectDialog.show();
		
		final ListView serverListView = (ListView)serverSelectDialog.findViewById(R.id.serverListView);
		final ServerListAdapter adapter = new ServerListAdapter(this, GoogleSearchPlexControlApplication.getPlexMediaServers());
		PlexServer s = (PlexServer)adapter.getItem(0);
		Log.v(TAG, "Server 0: " + s.getName());
		serverListView.setAdapter(adapter);
		serverListView.setOnItemClickListener(new ListView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parentAdapter, View view, int position,
					long id) {
				PlexServer s = (PlexServer)parentAdapter.getItemAtPosition(position);
				serverSelectDialog.dismiss();
				setServer(s);
			}
			
		});
	}
	
	private void setServer(PlexServer server) {
		Log.v(TAG, "Setting Server " + server.getName());
		this.server = server;
		
		try {
		    String url = "http://" + server.getIPAddress() + ":" + server.getPort() + "/";
		    AsyncHttpClient client = new AsyncHttpClient();
		    client.get(url, new AsyncHttpResponseHandler() {
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
		
	}
	
	private void getClients() {
		getClients(null);
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
		    String url = "http://" + server.getIPAddress() + ":" + server.getPort() + "/clients";
		    AsyncHttpClient client = new AsyncHttpClient();
		    client.get(url, new AsyncHttpResponseHandler() {
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
		final ClientListAdapter adapter = new ClientListAdapter(this, clients);
//		PlexClient c = (PlexClient)adapter.getItem(0);
//		Log.v(TAG, "Client 0: " + c.getName());
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
//		String json = mPrefs.getString("Server", "");
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
            LocalBroadcastManager.getInstance(this).unregisterReceiver(gdmReceiver);
    }

	@Override
    protected void onResume() {
            super.onResume();
            IntentFilter filters = new IntentFilter();
            filters.addAction(GDMService.MSG_RECEIVED);
            filters.addAction(GDMService.SOCKET_CLOSED);
            LocalBroadcastManager.getInstance(this).registerReceiver(gdmReceiver,
                            filters);
    }

}
