package com.atomjack.vcfp.activities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.atomjack.vcfp.BuildConfig;
import com.atomjack.vcfp.Feedback;
import com.atomjack.vcfp.FutureRunnable;
import com.atomjack.vcfp.GDMService;
import com.atomjack.vcfp.LocalScan;
import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.adapters.MainListAdapter;
import com.atomjack.vcfp.Preferences;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.RemoteScan;
import com.atomjack.vcfp.ScanHandler;
import com.atomjack.vcfp.tasker.TaskerPlugin;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.MainSetting;
import com.atomjack.vcfp.model.Pin;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexDevice;
import com.atomjack.vcfp.model.PlexError;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.model.PlexUser;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpUserHandler;
import com.atomjack.vcfp.net.PlexPinResponseHandler;
import com.bugsense.trace.BugSenseHandler;
import com.cubeactive.martin.inscription.WhatsNewDialog;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
	public final static String PREFS = "VoiceControlForPlexPrefs";

	public final static int FEEDBACK_VOICE = 0;
	public final static int FEEDBACK_TOAST = 1;

	public final static String BUGSENSE_APIKEY = "879458d0";

	private final static int RESULT_VOICE_FEEDBACK_SELECTED = 0;
	private final static int RESULT_TASKER_PROJECT_IMPORTED = 1;
	private final static int RESULT_SHORTCUT_CREATED = 2;

	private BroadcastReceiver gdmReceiver = new GDMReceiver();

	private ArrayList<String> availableVoices;
	private boolean settingErrorFeedback = false;

	private Feedback feedback;

	private FutureRunnable fetchPinTask;

	private PlexServer server = null;
	private PlexClient client = null;

	private SharedPreferences mPrefs;
	private SharedPreferences.Editor mPrefsEditor;

	private Gson gson = new Gson();

	private TextToSpeech tts;

	Menu menu;
	private Dialog searchDialog;

	private String authToken;

	AlertDialog.Builder helpDialog;

	private LocalScan localScan;
	private RemoteScan remoteScan;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if(BuildConfig.USE_BUGSENSE)
			BugSenseHandler.initAndStartSession(MainActivity.this, BUGSENSE_APIKEY);


		final WhatsNewDialog whatsNewDialog = new WhatsNewDialog(this);
		whatsNewDialog.show();
		
		mPrefs = getSharedPreferences(PREFS, MODE_PRIVATE);
		mPrefsEditor = mPrefs.edit();
		Gson gson = new Gson();

		feedback = new Feedback(mPrefs, this);

		authToken = mPrefs.getString(Preferences.AUTHENTICATION_TOKEN, null);

		Type clientType = new TypeToken<HashMap<String, PlexClient>>(){}.getType();
		VoiceControlForPlexApplication.clients = gson.fromJson(mPrefs.getString(Preferences.SAVED_CLIENTS, ""), clientType);

		setContentView(R.layout.main);

		server = gson.fromJson(mPrefs.getString("Server", ""), PlexServer.class);
		if(server == null)
			server = new PlexServer(getString(R.string.scan_all));

		client = gson.fromJson(mPrefs.getString("Client", ""), PlexClient.class);

		localScan = new LocalScan(this, MainActivity.class, mPrefs, new ScanHandler() {
			@Override
			public void onDeviceSelected(PlexDevice device, boolean resume) {
				if(device instanceof PlexServer)
					setServer((PlexServer)device);
				else if(device instanceof PlexClient)
					setClient((PlexClient)device);
			}
		});
		remoteScan = new RemoteScan(mPrefs);

		initMainWithServer();
	}

	public void installTasker(MenuItem item) {
		openAppInPlayStore("net.dinglisch.android.taskerm");
	}

	public void installUtter(MenuItem item) {
		openAppInPlayStore("com.brandall.nutter");
	}

	public void installAutoVoice(MenuItem item) {
		openAppInPlayStore("com.joaomgcd.autovoice");
	}

	public void showChangelog(MenuItem item) {
		final WhatsNewDialog whatsNewDialog = new WhatsNewDialog(this);
		whatsNewDialog.forceShow();
	}

	private void openAppInPlayStore(String packageName) {
		try {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName)));
		} catch (android.content.ActivityNotFoundException anfe) {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + packageName)));
		}
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
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == RESULT_VOICE_FEEDBACK_SELECTED) {
			if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
				// success, create the TTS instance
				availableVoices = data.getStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES);
				// Need this or else voice selection won't show up:
				tts = new TextToSpeech(this, this);
			} else {
				// missing data, install it
				Intent installIntent = new Intent();
				installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
				startActivity(installIntent);
			}
		} else if(requestCode == RESULT_TASKER_PROJECT_IMPORTED) {
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
		} else if(requestCode == RESULT_SHORTCUT_CREATED) {
			if(resultCode == RESULT_OK) {
				data.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
				sendBroadcast(data);

				feedback.m(getString(R.string.shortcut_created));
			}
		}
	}

	private void initMainWithServer() {
		MainSetting setting_data[] = new MainSetting[] {
			new MainSetting(MainListAdapter.SettingHolder.TAG_SERVER, getResources().getString(R.string.stream_video_from_server), server.owned ? server.name : server.sourceTitle),
			new MainSetting(MainListAdapter.SettingHolder.TAG_CLIENT, getResources().getString(R.string.to_the_client), client != null ? client.name : getResources().getString(R.string.not_set)),
			new MainSetting(MainListAdapter.SettingHolder.TAG_FEEDBACK, getResources().getString(R.string.feedback), mPrefs.getInt("feedback", FEEDBACK_TOAST) == FEEDBACK_VOICE ? getResources().getString(R.string.voice) : getResources().getString(R.string.toast)),
			new MainSetting(MainListAdapter.SettingHolder.TAG_ERRORS, getResources().getString(R.string.errors), mPrefs.getInt("errors", FEEDBACK_TOAST) == FEEDBACK_VOICE ? getResources().getString(R.string.voice) : getResources().getString(R.string.toast))
		};
		
		MainListAdapter adapter = new MainListAdapter(this, R.layout.main_setting_item_row, setting_data);
		
		ListView settingsList = (ListView)findViewById(R.id.settingsList);
		settingsList.setFooterDividersEnabled(true);
		settingsList.addFooterView(new View(settingsList.getContext()));
		settingsList.setAdapter(adapter);
		setListViewHeightBasedOnChildren(settingsList);
		settingsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapter, View view, int position,
															long arg3) {
				MainListAdapter.SettingHolder holder = (MainListAdapter.SettingHolder) view.getTag();
				Logger.d("Clicked %s", holder.tag);
				if (holder.tag.equals(holder.TAG_SERVER)) {
					if(!VoiceControlForPlexApplication.isWifiConnected(MainActivity.this)) {
						feedback.e(getResources().getString(R.string.no_wifi_connection_message));
						return;
					}
					if(authToken != null) {
						searchDialog = new Dialog(MainActivity.this);

						searchDialog.setContentView(R.layout.search_popup);
						searchDialog.setTitle(getResources().getString(R.string.searching_for_plex_servers));

						searchDialog.show();
						remoteScan.refreshResources(authToken, new RemoteScan.RefreshResourcesResponseHandler() {
							@Override
							public void onSuccess() {
								localScan.searchForPlexServers(true);
							}

							@Override
							public void onFailure(int statusCode) {
								if(statusCode == 401) {
									authToken = null;
									mPrefsEditor.remove(Preferences.AUTHENTICATION_TOKEN);
									feedback.e(R.string.login_unauthorized);
									switchLogin();
								} else
									feedback.e(R.string.remote_scan_error);
							}
						});
					} else {
						Logger.d("not logged in");
						localScan.searchForPlexServers();
					}
				} else if (holder.tag.equals(holder.TAG_CLIENT)) {
					if(!VoiceControlForPlexApplication.isWifiConnected(MainActivity.this)) {
						feedback.e(getResources().getString(R.string.no_wifi_connection_message));
						return;
					}
					localScan.searchForPlexClients();
				} else if (holder.tag.equals(holder.TAG_FEEDBACK)) {
					selectFeedback();
				} else if (holder.tag.equals(holder.TAG_ERRORS)) {
					selectFeedback(true);
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
		} else if(helpButtonClicked.equals("errors")) {
			helpDialog.setMessage(R.string.help_errors);
		}
		helpDialog.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
			}
		});
		helpDialog.show();
	}

	private void selectFeedback() {
		selectFeedback(false);
	}

	private void selectFeedback(final boolean errors) {
		AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
		builder.setTitle(errors ? R.string.errors : R.string.feedback);
		final MainActivity ctx = this;
		builder.setPositiveButton(R.string.feedback_voice, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				mPrefsEditor.putInt(errors ? "errors" : "feedback", FEEDBACK_VOICE);
				mPrefsEditor.commit();
				Intent checkIntent = new Intent();
				checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
				tts = new TextToSpeech(ctx, new TextToSpeech.OnInitListener() {
					@Override
					public void onInit(int i) {

					}
				});
				String engine = tts.getDefaultEngine();
				if (engine != null)
					checkIntent.setPackage(engine);
				settingErrorFeedback = errors;
				startActivityForResult(checkIntent, RESULT_VOICE_FEEDBACK_SELECTED);
				initMainWithServer();
			}
		}).setNegativeButton(R.string.feedback_toast, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				mPrefsEditor.putInt(errors ? "errors" : "feedback", FEEDBACK_TOAST);
				mPrefsEditor.commit();
				initMainWithServer();
			}
		});
		AlertDialog d = builder.create();
		d.show();
	}

	public void installShortcut(MenuItem item) {
		Intent intent = new Intent(this, ShortcutProviderActivity.class);

		startActivityForResult(intent, RESULT_SHORTCUT_CREATED);

//		startActivity(intent);
		/*
		Intent.ShortcutIconResource icon = Intent.ShortcutIconResource.fromContext(this, R.drawable.ic_launcher);

		Intent launchIntent = new Intent(this, ShortcutActivity.class);

		final Intent intent = new Intent();
		intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent);
		intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.app_name));
		intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(this, R.drawable.ic_launcher));
		intent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");

		sendBroadcast(intent);
		*/
	}

	public void showAbout(MenuItem item) {
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this)
						.setTitle(R.string.app_name)
						.setMessage(R.string.about_text);

		alertDialog.show();
	}

	public void donate(MenuItem item) {
		Intent intent = new Intent(Intent.ACTION_VIEW,
						Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=UJF9QY9QELERG"));
		startActivity(intent);
	}

	public void logout(MenuItem item) {
		mPrefsEditor.remove(Preferences.AUTHENTICATION_TOKEN);
		authToken = null;

		// If the currently selected server is not local, reset it to scan all.
		if(!server.local) {
			server = new PlexServer(getString(R.string.scan_all));
			saveSettings();
			initMainWithServer();
		}

		// Remove any non-local servers from our list
		for(PlexServer s : VoiceControlForPlexApplication.servers.values()) {
			if(!s.local)
				VoiceControlForPlexApplication.servers.remove(s.name);
		}

		mPrefsEditor.putString(Preferences.SAVED_SERVERS, gson.toJson(VoiceControlForPlexApplication.servers));
		mPrefsEditor.commit();
		MenuItem loginItem = menu.findItem(R.id.menu_login);
		loginItem.setVisible(true);
		MenuItem logoutItem = menu.findItem(R.id.menu_logout);
		logoutItem.setVisible(false);

		feedback.m(R.string.logged_out);
	}

	public void showLogin(MenuItem item) {
		showLogin();
	}

	public void showLogin() {
		Header[] headers = {
			new BasicHeader(PlexHeaders.XPlexClientIdentifier, getUUID())
		};

		PlexHttpClient.getPinCode(MainActivity.this, headers, new PlexPinResponseHandler() {
			@Override
			public void onSuccess(Pin pin) {
				showPin(pin);
			}

			@Override
			public void onFailure(Throwable error) {
				error.printStackTrace();
				feedback.e(R.string.login_error);
			}
		});

		/*

*/
	}

	private void switchLogin() {
		menu.findItem(R.id.menu_login).setVisible(!menu.findItem(R.id.menu_login).isVisible());
		menu.findItem(R.id.menu_logout).setVisible(!menu.findItem(R.id.menu_logout).isVisible());
	}

	private void showPin(final Pin pin) {
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
		alertDialogBuilder.setTitle(R.string.pin_title);
		alertDialogBuilder.setMessage(String.format(getString(R.string.pin_message), pin.code));
		alertDialogBuilder
						.setCancelable(false)
						.setNegativeButton("Cancel",
										new DialogInterface.OnClickListener() {
											public void onClick(DialogInterface dialog, int id) {
												dialog.cancel();
												fetchPinTask.getFuture().cancel(false);
											}
										}
						)
						.setNeutralButton("Manual", new DialogInterface.OnClickListener() {
							@Override
							public void onClick(final DialogInterface dialog, int id) {
								dialog.dismiss();
								fetchPinTask.getFuture().cancel(false);
								showManualLogin();
							}
						});


		// create and show an alert dialog
		final AlertDialog pinAlert = alertDialogBuilder.create();
		pinAlert.show();

		// Now set up a task to hit the below url (based on the "id" field returned in the above http POST)
		// every second. Once the user has entered the code on the plex website, the xml returned from the
		// below http GET will contain their authentication token. Once that is retrieved, save it, switch
		// the login/logout buttons in the menu, and cancel the dialog.
		final Context context = MainActivity.this;
		fetchPinTask = new FutureRunnable() {
			@Override
			public void run() {
				AsyncHttpClient hclient = new AsyncHttpClient();
				String url = String.format("https://plex.tv:443/pins/%d.xml", pin.id);
				Logger.d("Fetching %s", url);
				Header[] headers = {
					new BasicHeader(PlexHeaders.XPlexClientIdentifier, getUUID())
				};
				hclient.get(MainActivity.this, url, headers, new RequestParams(), new AsyncHttpResponseHandler() {
					@Override
					public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
						Pin pin = new Pin();
						Serializer serial = new Persister();
						try {
							pin = serial.read(Pin.class, new String(responseBody, "UTF-8"));
						} catch (Exception e) {
							Logger.e("Exception parsing response: %s", e.toString());
						}
						if(pin.authToken != null) {
							authToken = pin.authToken;
							mPrefsEditor.putString(Preferences.AUTHENTICATION_TOKEN, authToken);
							mPrefsEditor.commit();
							pinAlert.cancel();
							Handler mainHandler = new Handler(context.getMainLooper());
							mainHandler.post(new Runnable() {
								@Override
								public void run() {
									feedback.m(R.string.logged_in);
									switchLogin();
									remoteScan.refreshResources(authToken, new RemoteScan.RefreshResourcesResponseHandler() {
										@Override
										public void onSuccess() {
											feedback.t(R.string.servers_refreshed);
										}

										@Override
										public void onFailure(int statusCode) {
											feedback.e(R.string.remote_scan_error);
										}
									});
								}
							});
							// We got the auth token, so cancel this task
							getFuture().cancel(false);
						}
					}

					@Override
					public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
						error.printStackTrace();
					}
				});
			}
		};
		// Set up the schedule service and let fetchPinTask know of the Future object, so the task can cancel
		// itself once the authentication token is retrieved.
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		Future<?> future = executor.scheduleAtFixedRate(fetchPinTask, 0, 1000, TimeUnit.MILLISECONDS);
		fetchPinTask.setFuture(future);
	}

	private void showManualLogin() {
		LayoutInflater layoutInflater = LayoutInflater.from(MainActivity.this);
		View promptView = layoutInflater.inflate(R.layout.login, null);
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
		alertDialogBuilder.setView(promptView);
		alertDialogBuilder.setTitle(R.string.login_title);
		alertDialogBuilder.setMessage(R.string.login_message);
		final EditText usernameInput = (EditText) promptView.findViewById(R.id.usernameInput);
		final EditText passwordInput = (EditText) promptView.findViewById(R.id.passwordInput);
		alertDialogBuilder
						.setCancelable(true)
						.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(final DialogInterface dialog, int id) {
							}
						})
						.setNeutralButton(R.string.button_pin, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(final DialogInterface dialog, int id) {
								dialog.dismiss();
								showLogin();
							}
						})
						.setNegativeButton(R.string.cancel,
										new DialogInterface.OnClickListener() {
											public void onClick(DialogInterface dialog, int id) {
												dialog.cancel();
											}
										}
						);


		// create an alert dialog
		final AlertDialog alertD = alertDialogBuilder.create();

		alertD.show();

		Button b = alertD.getButton(DialogInterface.BUTTON_POSITIVE);
		b.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Header[] headers = {
								new BasicHeader(com.atomjack.vcfp.PlexHeaders.XPlexClientPlatform, "Android"),
								new BasicHeader(com.atomjack.vcfp.PlexHeaders.XPlexClientIdentifier, getUUID()),
								new BasicHeader("Accept", "text/xml")
				};
				PlexHttpClient.signin(MainActivity.this, usernameInput.getText().toString(), passwordInput.getText().toString(), headers, "application/xml;charset=\"utf-8\"", new PlexHttpUserHandler() {
					@Override
					public void onSuccess(PlexUser user) {
						mPrefsEditor.putString(Preferences.AUTHENTICATION_TOKEN, user.authenticationToken);
						authToken = user.authenticationToken;
						mPrefsEditor.commit();
						feedback.m(R.string.logged_in);
						MenuItem loginItem = menu.findItem(R.id.menu_login);
						loginItem.setVisible(false);
						MenuItem logoutItem = menu.findItem(R.id.menu_logout);
						logoutItem.setVisible(true);
						alertD.cancel();
					}

					@Override
					public void onFailure(int statusCode, PlexError error) {
						Logger.e("Failure logging in");
						String err = getString(R.string.login_error);
						if (error.errors != null && error.errors.size() > 0)
							err = error.errors.get(0);
						feedback.e(err);
					}

				});
			}
		});
	}

	public String getUUID() {
		String uuid = mPrefs.getString(Preferences.UUID, null);
		if(uuid == null) {
			uuid = UUID.randomUUID().toString();
			mPrefsEditor.putString(Preferences.UUID, uuid);
			mPrefsEditor.commit();
		}
		return uuid;
	}

	public void importTaskerProject(MenuItem item) {
		String xmlfile = "VoiceControlForPlex.prj.xml";

		try
		{
			AssetManager am = getAssets();
			InputStream is = am.open(xmlfile);
			int size = is.available();
			byte[] buffer = new byte[size];
			is.read(buffer);
			is.close();

			String xmlContents = new String(buffer);
			xmlContents = xmlContents.replace("%RECOGNITION_REGEX%", getString(R.string.pattern_recognition));
			buffer = xmlContents.getBytes();
			Logger.d("directory: %s", Environment.getExternalStorageDirectory());

			File f = new File(Environment.getExternalStorageDirectory() + "/" + xmlfile);
			FileOutputStream fos = new FileOutputStream(f);
			fos.write(buffer);
			fos.close();

			Logger.d("Wrote xml file");

			Intent i = new Intent();
			i.setAction(Intent.ACTION_VIEW);
			i.setDataAndType(Uri.fromFile(f), "text/xml");
			startActivityForResult(i, RESULT_TASKER_PROJECT_IMPORTED);
		} catch (Exception e) {
			Logger.d("Exception opening tasker profile xml: ");
			e.printStackTrace();
			return;
		}


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

	/**** Method for Setting the Height of the ListView dynamically.
	 **** Hack to fix the issue of not showing all the items of the ListView
	 **** when placed inside a ScrollView  ****/
	public static void setListViewHeightBasedOnChildren(ListView listView) {
		ListAdapter listAdapter = listView.getAdapter();
		if (listAdapter == null)
			return;

		int desiredWidth = View.MeasureSpec.makeMeasureSpec(listView.getWidth(), View.MeasureSpec.UNSPECIFIED);
		int totalHeight = 0;
		View view = null;
		for (int i = 0; i < listAdapter.getCount(); i++) {
			view = listAdapter.getView(i, view, listView);
			if (i == 0)
				view.setLayoutParams(new ViewGroup.LayoutParams(desiredWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

			view.measure(desiredWidth, View.MeasureSpec.UNSPECIFIED);
			totalHeight += view.getMeasuredHeight();
		}
		ViewGroup.LayoutParams params = listView.getLayoutParams();
		params.height = totalHeight + (listView.getDividerHeight() * (listAdapter.getCount() - 1));
		listView.setLayoutParams(params);
		listView.requestLayout();
	}

	@Override
	public void onNewIntent(Intent intent) {
		Logger.d("ON NEW INTENT IN MAINACTIVITY");
		String from = intent.getStringExtra("FROM");
		Logger.d("From: %s", from);
		if(intent.getAction().equals(VoiceControlForPlexApplication.Intent.GDMRECEIVE)) {
			Logger.d("Origin: " + intent.getStringExtra("ORIGIN"));
			String origin = intent.getStringExtra("ORIGIN") == null ? "" : intent.getStringExtra("ORIGIN");
			if(origin.equals("MainActivity")) {
				if(intent.getStringExtra(VoiceControlForPlexApplication.Intent.SCAN_TYPE).equals("server")) {
					Logger.d("Got " + VoiceControlForPlexApplication.servers.size() + " servers");

					mPrefsEditor.putString(Preferences.SAVED_SERVERS, gson.toJson(VoiceControlForPlexApplication.servers));
					mPrefsEditor.commit();
					if(searchDialog != null)
						searchDialog.dismiss();
					if (VoiceControlForPlexApplication.servers.size() > 0) {
						localScan.showPlexServers();
					} else {
						AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
						builder.setTitle(R.string.no_servers_found);
						builder.setCancelable(false)
										.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
											public void onClick(DialogInterface dialog, int id) {
												dialog.cancel();
											}
										});
						AlertDialog d = builder.create();
						d.show();
					}
				} else if(intent.getStringExtra(VoiceControlForPlexApplication.Intent.SCAN_TYPE).equals("client")) {
					ArrayList<PlexClient> clients = intent.getParcelableArrayListExtra(VoiceControlForPlexApplication.Intent.EXTRA_CLIENTS);
					if(clients != null) {
						VoiceControlForPlexApplication.clients = new HashMap<String, PlexClient>();
						for (PlexClient c : clients) {
							VoiceControlForPlexApplication.clients.put(c.name, c);
						}
						mPrefsEditor.putString(Preferences.SAVED_CLIENTS, gson.toJson(VoiceControlForPlexApplication.clients));
						mPrefsEditor.commit();
						localScan.showPlexClients(VoiceControlForPlexApplication.clients);
					} else {
						localScan.hideSearchDialog();
						AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
						builder.setTitle(R.string.no_clients_found);
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
		}
	}
	


	private void setServer(PlexServer _server) {
		Logger.d("Setting Server %s", _server.name);
		server = _server;
		saveSettings();

		if(client == null) {
			localScan.searchForPlexClients();
		} else {
			initMainWithServer();
		}
		
	}
	
	private void setClient(PlexClient _client) {
		client = _client;
		Logger.d("Selected client: " + client.name);
		saveSettings();
		initMainWithServer();
	}
	
	private void saveSettings() {
		Gson gson = new Gson();
		mPrefsEditor.putString("Server", gson.toJson(server));
		mPrefsEditor.putString("Client", gson.toJson(client));
		mPrefsEditor.putBoolean("resume", mPrefs.getBoolean("resume", false));
		mPrefsEditor.commit();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu _menu) {
		super.onCreateOptionsMenu(menu);
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_main, _menu);
		menu = _menu;
		if(authToken != null) {
			menu.findItem(R.id.menu_login).setVisible(false);
			menu.findItem(R.id.menu_logout).setVisible(true);
		}
		if (!hasValidAutoVoice() && !hasValidUtter()) {
			_menu.findItem(R.id.menu_tasker_import).setVisible(false);
			if (!hasValidTasker()) {
				_menu.findItem(R.id.menu_install_tasker).setVisible(true);
			}
			if (!hasValidUtter()) {
				_menu.findItem(R.id.menu_install_utter).setVisible(true);
			}
			if (!hasValidAutoVoice()) {
				_menu.findItem(R.id.menu_install_autovoice).setVisible(true);
			}
		}
		return true;
	}

	@Override
	protected void onDestroy() {
		if(gdmReceiver != null) {
			LocalBroadcastManager.getInstance(this).unregisterReceiver(gdmReceiver);
		}
		feedback.destroy();
		if(tts != null)
			tts.shutdown();
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		super.onPause();
		VoiceControlForPlexApplication.applicationPaused();
		if(gdmReceiver != null) {
			LocalBroadcastManager.getInstance(this).unregisterReceiver(gdmReceiver);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		VoiceControlForPlexApplication.applicationResumed();
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
		if(getCallingActivity() != null)
		{
			Bundle bundle = new Bundle();
			// Pass the entire string of what was said into autovoice
			bundle.putString(VoiceControlForPlexApplication.Intent.ARGUMENTS, "%avcomm");
			if (TaskerPlugin.Setting.hostSupportsOnFireVariableReplacement(this))
				TaskerPlugin.Setting.setVariableReplaceKeys(bundle, new String[]{VoiceControlForPlexApplication.Intent.ARGUMENTS});

			Intent i = new Intent();
			i.putExtras(bundle);

			String blurb = "Server: " + (server != null ? server.name : getResources().getString(R.string.scan_all));
			blurb += " | Client: " + (client != null ? client.name : "Not specified.");
			if(mPrefs.getBoolean("resume", false))
				blurb += " (resume)";


			i.putExtra("com.twofortyfouram.locale.intent.extra.BLURB", blurb);
			setResult(Activity.RESULT_OK, i);
		}
		super.finish();
	}

	@Override
	public void onInit(int status) {
		if (status == TextToSpeech.SUCCESS) {
			final String pref = settingErrorFeedback ? Preferences.ERRORS_VOICE : Preferences.FEEDBACK_VOICE;
			if (availableVoices != null) {
				AlertDialog.Builder adb = new AlertDialog.Builder(this);
				final CharSequence items[] = availableVoices.toArray(new CharSequence[availableVoices.size()]);
				int selectedVoice = -1;
				String v = mPrefs.getString(pref, "Locale.US");
				if (availableVoices.indexOf(v) > -1)
					selectedVoice = availableVoices.indexOf(v);

				adb.setSingleChoiceItems(items, selectedVoice, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface d, int n) {
						mPrefsEditor.putString(pref, items[n].toString());
						mPrefsEditor.commit();
						d.dismiss();
					}
				});
				adb.setNegativeButton(R.string.cancel, null);
				adb.setTitle(R.string.select_voice);
				adb.show();
			} else {
				mPrefsEditor.putString(pref, "Locale.US");
				mPrefsEditor.commit();
			}
		}
	}
}


