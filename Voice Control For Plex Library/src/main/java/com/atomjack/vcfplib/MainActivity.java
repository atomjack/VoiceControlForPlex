package com.atomjack.vcfplib;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.atomjack.vcfplib.model.MainSetting;
import com.atomjack.vcfplib.model.MediaContainer;
import com.atomjack.vcfplib.model.PlexClient;
import com.atomjack.vcfplib.model.PlexServer;
import com.atomjack.vcfplib.net.PlexHttpClient;
import com.atomjack.vcfplib.net.PlexHttpMediaContainerHandler;
import com.google.gson.Gson;
import com.bugsense.trace.BugSenseHandler;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
	public final static String PREFS = "VoiceControlForPlexPrefs";

	public final static int FEEDBACK_VOICE = 0;
	public final static int FEEDBACK_TOAST = 1;

	public final static String BUGSENSE_APIKEY = "879458d0";

	private final static int VOICE_FEEDBACK_SELECTED = 0;
	private final static int TASKER_PROJECT_IMPORTED = 1;

	private BroadcastReceiver gdmReceiver = new GDMReceiver();


	private TextToSpeech mTts;
	private String ttsDelayedFeedback = null;
	private ArrayList<String> availableVoices;

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


		if (hasValidAutoVoice() || hasValidUtter()) {
			TextView t = (TextView)findViewById(R.id.taskerImportHeader);
			t.setVisibility(TextView.VISIBLE);
			Button b = (Button) findViewById(R.id.taskerImportButton);
			b.setVisibility(Button.VISIBLE);
		} else if(!hasValidGoogleSearch()) {
			TextView t1 = (TextView) findViewById(R.id.taskerInstructionsHeader);
			t1.setVisibility(TextView.VISIBLE);

			TextView t2 = (TextView) findViewById(R.id.taskerInstructionsView);
			t2.setVisibility(TextView.VISIBLE);

			if(!hasValidTasker()) {
				Button taskerButton = (Button)findViewById(R.id.taskerInstallButton);
				taskerButton.setVisibility(Button.VISIBLE);
			}
			if(!hasValidUtter()) {
				Button utterButton = (Button)findViewById(R.id.utterInstallButton);
				utterButton.setVisibility(Button.VISIBLE);
			}
			if(!hasValidAutoVoice()) {
				Button autoVoiceButton = (Button)findViewById(R.id.autoVoiceInstallButton);
				autoVoiceButton.setVisibility(Button.VISIBLE);
			}
		}

		this.server = gson.fromJson(mPrefs.getString("Server", ""), PlexServer.class);
		this.client = gson.fromJson(mPrefs.getString("Client", ""), PlexClient.class);

		initMainWithServer();
	}

	public void installTasker(View v) {
		openAppInPlayStore("net.dinglisch.android.taskerm");
	}

	public void installUtter(View v) {
		openAppInPlayStore("com.brandall.nutter");
	}

	public void installAutoVoice(View v) {
		openAppInPlayStore("com.joaomgcd.autovoice");
	}

	private void openAppInPlayStore(String packageName) {
		try {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName)));
		} catch (android.content.ActivityNotFoundException anfe) {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + packageName)));
		}
	}

	private boolean hasValidGoogleSearch() {
		try
		{
			PackageInfo pinfo = getPackageManager().getPackageInfo("com.google.android.googlequicksearchbox", 0);
			if(VoiceControlForPlexApplication.isVersionLessThan(pinfo.versionName, "3.4"))
				return true;
		} catch(Exception e) {}
		return false;
	}

	private boolean hasValidTasker() {
		PackageInfo pinfo;
		try
		{
			pinfo = getPackageManager().getPackageInfo("net.dinglisch.android.tasker", 0);
			return true;
		} catch(Exception e) {}
		try
		{
			pinfo = getPackageManager().getPackageInfo("net.dinglisch.android.taskerm", 0);
			return true;
		} catch(Exception e) {
			Logger.d("Exception getting google search version: " + e.getStackTrace());
		}
		return false;
	}

	private boolean hasValidAutoVoice() {
		try
		{
			if(hasValidTasker()) {
				PackageInfo pinfo = getPackageManager().getPackageInfo("com.joaomgcd.autovoice", 0);
				return true;
			}
		} catch(Exception e) {
			Logger.d("Exception getting autovoice version: " + e.getStackTrace());
		}
		return false;
	}

	private boolean hasValidUtter() {
		try
		{
			if(hasValidTasker()) {
				PackageInfo pinfo = getPackageManager().getPackageInfo("com.brandall.nutter", 0);
				return true;
			}
		} catch(Exception e) {
			Logger.d("Exception getting utter version: " + e.getStackTrace());
		}
		return false;
	}






	public void resumeChecked(View v) {
		mPrefsEditor.putBoolean("resume", ((CheckBox)v).isChecked());
		mPrefsEditor.commit();
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(item.getItemId() == R.id.menu_about) {
			return showAbout();
		} else if(item.getItemId() == R.id.menu_donate) {
			Intent intent = new Intent(Intent.ACTION_VIEW,
							Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=UJF9QY9QELERG"));
			startActivity(intent);
			return true;
		}
		/*
		switch (item.getItemId()) {
		case R.id.menu_about:

		case R.id.menu_donate:

		}
		*/
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == VOICE_FEEDBACK_SELECTED) {
			if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
				// success, create the TTS instance
				availableVoices = data.getStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES);
				mTts = new TextToSpeech(this, this);
			} else {
				// missing data, install it
				Intent installIntent = new Intent();
				installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
				startActivity(installIntent);
			}
		} else if(requestCode == TASKER_PROJECT_IMPORTED) {
			AlertDialog.Builder usageDialog = new AlertDialog.Builder(MainActivity.this);
			usageDialog.setTitle(R.string.import_tasker_project);
			usageDialog.setMessage(R.string.import_tasker_instructions);
			usageDialog.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.dismiss();
					Intent i;
					PackageManager manager = getPackageManager();
					try {
						i = manager.getLaunchIntentForPackage("net.dinglisch.android.tasker");
						if (i == null)
							throw new PackageManager.NameNotFoundException();
						i.addCategory(Intent.CATEGORY_LAUNCHER);
						startActivity(i);
					} catch (PackageManager.NameNotFoundException e) {

					}
				}
			});
			usageDialog.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.dismiss();
				}
			});
			usageDialog.show();
		}
	}

	private boolean showAbout() {
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this)
		.setTitle(R.string.app_name)
		.setMessage(R.string.about_text);

		alertDialog.show();

		return true;
	}

	private void initMainWithServer() {
		MainSetting setting_data[] = new MainSetting[] {
			new MainSetting("server", getResources().getString(R.string.stream_video_from_server), this.server != null ? this.server.name : getResources().getString(R.string.scan_all)),
			new MainSetting("client", getResources().getString(R.string.to_the_client), this.client != null ? this.client.name : getResources().getString(R.string.not_set)),
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
				MainListAdapter.SettingHolder holder = (MainListAdapter.SettingHolder)view.getTag();
				Logger.d("Clicked %s", holder.tag);
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
		builder.setTitle(R.string.feedback);
		final MainActivity ctx = this;
		builder.setCancelable(false)
			.setPositiveButton(R.string.feedback_voice, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							mPrefsEditor.putInt("feedback", FEEDBACK_VOICE);
							mPrefsEditor.commit();
							Intent checkIntent = new Intent();
							checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
							final TextToSpeech tts = new TextToSpeech(ctx, new TextToSpeech.OnInitListener() {
								@Override
								public void onInit(int i) {

								}
							});
							String engine = tts.getDefaultEngine();
							if(engine != null)
								checkIntent.setPackage(engine);
							startActivityForResult(checkIntent, VOICE_FEEDBACK_SELECTED);
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

	public void importTaskerProject(View v) {
		String xmlfile = "VoiceControlForPlex.prj.xml";
		File f = new File(Environment.getExternalStorageDirectory() + "/" + xmlfile);
		if(!f.exists()) {
			try
			{
				AssetManager am = this.getAssets();
				InputStream is = am.open(xmlfile);
				int size = is.available();
				byte[] buffer = new byte[size];
				is.read(buffer);
				is.close();

				FileOutputStream fos = new FileOutputStream(f);
				fos.write(buffer);
				fos.close();

				Logger.d("Wrote xml file");
			} catch (Exception e) {
				Logger.d("Exception opening tasker profile xml: ");
				e.printStackTrace();
				return;
			}
		}
		Intent i = new Intent();
		i.setAction(Intent.ACTION_VIEW);
		i.setDataAndType(Uri.fromFile(f), "text/xml");
		startActivityForResult(i, TASKER_PROJECT_IMPORTED);
	}
	
	public void showUsageExamples(View v) {
		AlertDialog.Builder usageDialog = new AlertDialog.Builder(MainActivity.this);
		usageDialog.setTitle(R.string.help_usage_button);
		usageDialog.setMessage(R.string.help_usage);
		usageDialog.setPositiveButton(R.string.got_it, new DialogInterface.OnClickListener() {
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
						if((!VoiceControlForPlexApplication.isVersionLessThan(clientMC.clients.get(i).version, "1.0.7") || !clientMC.clients.get(i).product.equals("Plex Home Theater")) && !m_clients.containsKey(clientMC.clients.get(i).name)) {
							m_clients.put(clientMC.clients.get(i).name, clientMC.clients.get(i));
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

				@Override
				public void onFailure(Throwable error) {
					searchDialog.dismiss();
					feedback(getResources().getString(R.string.got_error), error.getMessage());
					finish();
				}
			});
		}
	}

	private void feedback(String text, Object... arguments) {
		text = String.format(text, arguments);
		feedback(text);
	}

	public void feedback(String text) {
		//mTts = new TextToSpeech(this, this);
		if(mPrefs.getInt("feedback", 0) == MainActivity.FEEDBACK_VOICE) {
			if(mTts == null) {
				// Set up the TTS engine, and save the text that should be spoken so it can be done after the engine is initialized
				mTts = new TextToSpeech(getApplicationContext(), this);
				ttsDelayedFeedback = text;
				return;
			} else
				mTts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
		} else {
			Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
		}
		Logger.d(text);
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
		Logger.d("Setting Server %s", server.name);
		if(server.name.equals("")) {
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
					Logger.d("Machine id: " + mediaContainer.machineIdentifier);
					getClients(mediaContainer);
				}

				@Override
				public void onFailure(Throwable error) {
					searchDialog.dismiss();
					feedback(getResources().getString(R.string.got_error), error.getMessage());
					finish();
				}
			});
		} else {
			this.server = server;
			this.saveSettings();
			initMainWithServer();
		}
		
	}
	
	private void getClients() {
		if(server == null || server.name.equals(getResources().getString(R.string.scan_all))) {
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
			this.server.machineIdentifier = mc.machineIdentifier;
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
					if (!VoiceControlForPlexApplication.isVersionLessThan(clientMC.clients.get(i).version, "1.0.7"))
					{
						clients.put(clientMC.clients.get(i).name, clientMC.clients.get(i));
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

			@Override
			public void onFailure(Throwable error) {
				searchDialog.dismiss();
				feedback(getResources().getString(R.string.got_error), error.getMessage());
				finish();
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
		Logger.d("Selected client: " + client.name);
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
		if(gdmReceiver != null) {
			LocalBroadcastManager.getInstance(this).unregisterReceiver(gdmReceiver);
		}
		if (mTts != null) {
			mTts.stop();
			mTts.shutdown();
		}
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

	@Override
	public void finish() {
		if(this.getCallingActivity() != null)
		{
			Bundle bundle = new Bundle();
			// Pass the entire string of what was said into autovoice
			bundle.putString("com.atomjack.vcfp.intent.ARGUMENTS", "%avcomm");
			if (TaskerPlugin.Setting.hostSupportsOnFireVariableReplacement(this))
				TaskerPlugin.Setting.setVariableReplaceKeys(bundle, new String[]{"com.atomjack.vcfp.intent.ARGUMENTS"});

			Intent i = new Intent();
			i.putExtras(bundle);

			String blurb = "Server: " + (this.server != null ? this.server.name : getResources().getString(R.string.scan_all));
			blurb += " | Client: " + (this.client != null ? client.name : "Not specified.");
			if(mPrefs.getBoolean("resume", false))
				blurb += " (resume)";


			i.putExtra("com.twofortyfouram.locale.intent.extra.BLURB", blurb);
			setResult(Activity.RESULT_OK, i);
		}
		super.finish();
	}

	@Override
	public void onInit(int status) {
		if(mTts == null)
			return;

		if(ttsDelayedFeedback != null) {
			feedback(ttsDelayedFeedback);
			ttsDelayedFeedback = null;
		}

		AlertDialog.Builder adb = new AlertDialog.Builder(this);
		final CharSequence items[] = availableVoices.toArray(new CharSequence[availableVoices.size()]);
		int selectedVoice = -1;
		String v = mPrefs.getString(VoiceControlForPlexApplication.PREF_FEEDBACK_VOICE, "Locale.US");
		if(availableVoices.indexOf(v) > -1)
			selectedVoice = availableVoices.indexOf(v);

		adb.setSingleChoiceItems(items, selectedVoice, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface d, int n) {
				mTts.setLanguage(VoiceControlForPlexApplication.getVoiceLocale(items[n].toString()));
				mPrefsEditor.putString(VoiceControlForPlexApplication.PREF_FEEDBACK_VOICE, items[n].toString());
				mPrefsEditor.commit();
				d.dismiss();
			}
		});
		adb.setNegativeButton(R.string.cancel, null);
		adb.setTitle(R.string.select_voice);
		adb.show();
	}
}


