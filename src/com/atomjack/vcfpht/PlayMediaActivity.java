package com.atomjack.vcfpht;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import us.nineworlds.serenity.GDMReceiver;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.atomjack.vcfpht.model.MediaContainer;
import com.atomjack.vcfpht.model.PlexClient;
import com.atomjack.vcfpht.model.PlexDirectory;
import com.atomjack.vcfpht.model.PlexResponse;
import com.atomjack.vcfpht.model.PlexServer;
import com.atomjack.vcfpht.model.PlexVideo;
import com.google.gson.Gson;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.BinaryHttpResponseHandler;

public class PlayMediaActivity extends Activity {
	private static final String TAG = MainActivity.TAG;
	public final static String PREFS = MainActivity.PREFS;
	private SharedPreferences mPrefs;
//	private SharedPreferences.Editor mPrefsEditor;
	private String queryText;
	private Dialog searchDialog = null;
//	private Intent mServiceIntent;
	private ConcurrentHashMap<String, PlexServer> plexmediaServers = new ConcurrentHashMap<String, PlexServer>();
	private static Serializer serial = new Persister();
	private int serversScanned = 0;
	private BroadcastReceiver gdmReceiver = new GDMReceiver();
	private Intent mServiceIntent;
	private List<PlexClient> clients;
	private PlexClient client = null;
	private int movieSectionsSearched = 0;
	private int serversSearched = 0;
	private int showSectionsSearched = 0;
	private List<PlexVideo> videos = new ArrayList<PlexVideo>();
	private Boolean videoPlayed = false;
	private List<PlexDirectory> shows = new ArrayList<PlexDirectory>();
	private Boolean resumePlayback = false;
	
	protected void onCreate(Bundle savedInstanceState) {
		Log.v(TAG, "on create PlayMediaActivity");
		super.onCreate(savedInstanceState);
		
		mPrefs = getSharedPreferences(PREFS, MODE_PRIVATE);
//		mPrefsEditor = mPrefs.edit();

		setContentView(R.layout.play_media);
		
		Intent intent = getIntent();
		String origin = intent.getStringExtra("ORIGIN");
		Log.v(TAG, "origin: " + origin);
		if(origin == null) {
		} else if(origin.equals("GoogleSearchReceiver")) {
			this.queryText = intent.getStringExtra("queryText");
			if(searchDialog == null) {
				searchDialog = new Dialog(this);
			}
			searchDialog.setCancelable(false);
			searchDialog.setContentView(R.layout.search_popup);
			searchDialog.setTitle("Searching");
			
			searchDialog.show();
			
			if(mServiceIntent == null) {
				mServiceIntent = new Intent(this, GDMService.class);
			}
			mServiceIntent.putExtra("ORIGIN", "PlayMediaActivity");
			startService(mServiceIntent);
			
			
		}
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
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	private boolean showAbout() {
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(this)
		.setTitle(R.string.app_name)
		.setMessage(R.string.about_text);

		alertDialog.show();

		return true;
	}
	@Override
	public void onNewIntent(Intent intent) {
		Log.v(TAG, "ON NEW INTENT IN PLAYMEDIACTIVITY");
		String origin = intent.getStringExtra("ORIGIN");
		String from = intent.getStringExtra("FROM");
//		this.queryText = intent.getStringExtra("queryText");
		Log.v(TAG, "origin: " + origin);
		Log.v(TAG, "from: " + from);
		if(from == null) {
			if(origin.equals("GoogleSearchReceiver")) {
				Gson gson = new Gson();
				PlexServer defaultServer = (PlexServer)gson.fromJson(mPrefs.getString("Client", ""), PlexServer.class);
				if(defaultServer != null) {
					this.plexmediaServers = new ConcurrentHashMap<String, PlexServer>();
					this.plexmediaServers.put(defaultServer.getName(), defaultServer);
					
					setClient();
				} else {
					if(mServiceIntent == null) {
						mServiceIntent = new Intent(this, GDMService.class);
					}
					mServiceIntent.putExtra("ORIGIN", "PlayMediaActivity");
					startService(mServiceIntent);
				}
				
			}
		} else if(from.equals("GDMReceiver")) {
			videoPlayed = false;
//			LocalBroadcastManager.getInstance(this).unregisterReceiver(gdmReceiver);
			Log.v(TAG, "Origin: " + intent.getStringExtra("ORIGIN"));
			
			this.plexmediaServers = GoogleSearchPlexControlApplication.getPlexMediaServers();
			
			setClient();
			
			
		}
		
		
		/*
		String from = intent.getStringExtra("FROM");
		Log.v(TAG, "From: " + from);
		if(from == null) {
			
		} else if(from.equals("GDMReceiver")) {
			Log.v(TAG, "Origin: " + intent.getStringExtra("ORIGIN"));
			String origin = intent.getStringExtra("ORIGIN") == null ? "" : intent.getStringExtra("ORIGIN");
			if(origin.equals("GoogleSearchReceiver")) {
				Log.v(TAG, "Got intent from google search receiver: " + intent.getStringExtra("queryText"));
				handleVoiceSearch(intent.getStringExtra("queryText"));
			}
		}
		*/
	}
	
	private void setClient() {
		Pattern p = Pattern.compile( "on (.*)$", Pattern.DOTALL);
		Matcher matcher = p.matcher(queryText);
		if(!matcher.find()) {
			
			// Client not specified, so use default
			handleVoiceSearch();
		} else {
			// Get available clients
			serversScanned = 0;
			clients = new ArrayList<PlexClient>();
			for(PlexServer server : plexmediaServers.values()) {
				Log.v(TAG, "ip: " + server.getIPAddress());
				Log.v(TAG, "port: " + server.getPort());
				try {
				    String url = "http://" + server.getIPAddress() + ":" + server.getPort() + "/clients";
				    AsyncHttpClient httpClient = new AsyncHttpClient();
				    httpClient.get(url, new AsyncHttpResponseHandler() {
				        @Override
				        public void onSuccess(String response) {
				            Log.v(TAG, "HTTP REQUEST: " + response);
				            serversScanned++;
				            MediaContainer mc = new MediaContainer();
				            try {
				            	mc = serial.read(MediaContainer.class, response);
				            } catch (NotFoundException e) {
				                e.printStackTrace();
				            } catch (Exception e) {
				                e.printStackTrace();
				            }
				            Log.v(TAG, "Clients: " + mc.clients.size());
				            for(int i=0;i<mc.clients.size();i++) {
				            	clients.add(mc.clients.get(i));
				            }
				            if(serversScanned == plexmediaServers.size()) {
				            	handleVoiceSearch();
				            }
				        }
				    });

				} catch (Exception e) {
					Log.e(TAG, "Exception getting clients: " + e.toString());
				}
			}
		}
	}

	private void handleVoiceSearch() {
		Log.v(MainActivity.TAG, "GOT QUERY: " + queryText);
		
		resumePlayback = false;
		String mediaType = ""; // movie or show
		String queryTerm = "";
		String season = "";
		String episode = "";
		String episodeSpecified = "";
		String showSpecified = "";
		Boolean latest = false;
		String specifiedClient = "";
		
		// If the query spoken ends with "on <something>", check to see if the <something> matches the name of a client to play the media on
		Pattern p = Pattern.compile( "on ([^ ]+)$", Pattern.DOTALL);
		Matcher matcher = p.matcher(queryText);
		Gson gson = new Gson();
		this.client = null;
		if(matcher.find()) {
			specifiedClient = matcher.group(1);
			
			Log.v(TAG, "Clients: " + clients.size());
			Log.v(TAG, "query text now " + queryText);
			for(int i=0;i<clients.size();i++) {
				if(clients.get(i).getName().toLowerCase().equals(specifiedClient)) {
					this.client = clients.get(i);
					queryText = queryText.replaceAll(" on ([^ ]+)$", "");
					break;
				}
			}
		}
		if(this.client == null) {
			this.client = (PlexClient)gson.fromJson(mPrefs.getString("Client", ""), PlexClient.class);
		}
		
		if(this.client == null) {
			// No client set in options, and either none specified in the query or I just couldn't find it.
			feedback("Sorry, you didn't specify a client in the settings, or I couldn't find it.");
			searchDialog.dismiss();
			finish();
			return;
		}
		
		Log.v(TAG, "Servers: " + GoogleSearchPlexControlApplication.getPlexMediaServers().size());
		
		// Check for a sentence starting with "resume watching"
		p = Pattern.compile("^resume watching (.*)");
		matcher = p.matcher(queryText);
		if(matcher.find()) {
			resumePlayback = true;
			// Replace "resume watching" with just "watch" so the pattern matching below works
			queryText = queryText.replaceAll("^resume watching ", "watch ");
		}
		
		
		p = Pattern.compile( "watch movie (.*)", Pattern.DOTALL);
		matcher = p.matcher(queryText);
		if(matcher.find()) {
			mediaType = "movie";
			queryTerm = matcher.group(1);
		} 
		if(mediaType.equals("")) {
			p = Pattern.compile("watch season ([0-9]+) episode ([0-9]+) of (.*)");
			matcher = p.matcher(queryText);
			
			if(matcher.find()) {
				mediaType = "show";
				queryTerm = matcher.group(3);
				season = matcher.group(1);
				episode = matcher.group(2);
			}
		}
		if(mediaType.equals("")) {
			p = Pattern.compile("watch (.*) season ([0-9]+) episode ([0-9]+)");
			matcher = p.matcher(queryText);
			
			if(matcher.find()) {
				mediaType = "show";
				queryTerm = matcher.group(1);
				season = matcher.group(2);
				episode = matcher.group(3);
			}
		}
		if(mediaType.equals("")) {
			p = Pattern.compile("watch episode (.*) of (.*)");
			matcher = p.matcher(queryText);
			if(matcher.find()) {
				mediaType = "show";
				episodeSpecified = matcher.group(1);
				showSpecified = matcher.group(2);
			}
		}
		if(mediaType.equals("")) {
			p = Pattern.compile("watch (.*) episode (.*)");
			matcher = p.matcher(queryText);
			if(matcher.find()) {
				mediaType = "show";
				episodeSpecified = matcher.group(2);
				showSpecified = matcher.group(1);
			}
		}
		if(mediaType.equals("")) {
			p = Pattern.compile("watch( the)? latest episode of (.*)");
			matcher = p.matcher(queryText);
			
			if(matcher.find()) {
				mediaType = "show";
				latest = true;
				queryTerm = matcher.group(2);
			}
		}
		// Lastly, try to find a movie matching whatever comes after "watch"
		if(mediaType.equals("")) {
			p = Pattern.compile("watch (.*)");
			matcher = p.matcher(queryText);
			
			if(matcher.find()) {
				mediaType = "movie";
				queryTerm = matcher.group(1);
			}
		}
		
		Log.v(MainActivity.TAG, "media type: " + mediaType);
		Log.v(MainActivity.TAG, "query term: !" + queryTerm + "!");
		Log.v(MainActivity.TAG, "season: " + season);
		Log.v(MainActivity.TAG, "episode: " + episode);
		Log.v(MainActivity.TAG, "latest: " + latest);
		Log.v(TAG, "episodeSpecified: " + episodeSpecified);
		
		if(!queryTerm.equals("") || (!episodeSpecified.equals("") && !showSpecified.equals(""))) {
//			if(this.server != null && this.client != null) {
				if(mediaType.equals("movie")) {
					doMovieSearch(mediaType, queryTerm);
				} else if(mediaType.equals("show")) {
					if(latest == true) {
						doLatestEpisodeSearch(queryTerm);
					} else if(!episodeSpecified.equals("") && !showSpecified.equals("")) {
						doShowSearch(episodeSpecified, showSpecified);
					} else {
						doShowSearch(queryTerm, season, episode);
					}
				}
//			} else {
//				Log.v(MainActivity.TAG, "Server & Client are null!");
//			}
		}
	}
	
	private void doShowSearch(String episodeSpecified, final String showSpecified) {
		showSectionsSearched = 0;
		serversSearched = 0;
		for(final PlexServer server : this.plexmediaServers.values()) {
			for(int i=0;i<server.getTvSections().size();i++) {
				String section = server.getTvSections().get(i);
				try {
				    String url = "http://" + server.getIPAddress() + ":" + server.getPort() + "/library/sections/" + section + "/search?type=4&query=" + episodeSpecified;
				    AsyncHttpClient httpClient = new AsyncHttpClient();
				    httpClient.get(url, new AsyncHttpResponseHandler() {
				        @Override
				        public void onSuccess(String response) {
	//			            Log.v(MainActivity.TAG, "HTTP REQUEST: " + response);
				        	showSectionsSearched++;
				        	MediaContainer mc = new MediaContainer();
				            try {
				            	mc = serial.read(MediaContainer.class, response);
				            } catch (NotFoundException e) {
				                e.printStackTrace();
				            } catch (Exception e) {
				                e.printStackTrace();
				            }
				            for(int j=0;j<mc.videos.size();j++) {
				            	Log.v(TAG, "Show: " + mc.videos.get(j).getGrandparentTitle());
				            	PlexVideo video = mc.videos.get(j);
				            	if(video.getGrandparentTitle().toLowerCase().equals(showSpecified.toLowerCase())) {
				            		video.setServer(server);
				            		video.setThumb(video.getGrandparentThumb());
				            		video.setShowTitle(mc.grandparentTitle);
				            		videos.add(video);
				            	}
				            }
				        	
				        	if(server.getTvSections().size() == showSectionsSearched) {
				        		serversSearched++;
				            	if(serversSearched == plexmediaServers.size()) {
				            		playSpecificEpisode();
				            	}
				        	}
				        }
				    });
				} catch (Exception e) {
					Log.e(MainActivity.TAG, "Exception getting search results: " + e.toString());
				}
			}
		}
	}

	private void playSpecificEpisode() {
		if(videos.size() == 0) {
			feedback("Sorry, I couldn't find the episode you specified.");
			searchDialog.dismiss();
			finish();
			return;
		} else if(videos.size() == 1) {
			playVideo(videos.get(0));
		} else {
			feedback("Sorry, I found more than one match. Try to be more specific?");
			searchDialog.dismiss();
			finish();
			return;
		}
	}
	
	private void doShowSearch(final String queryTerm, final String season, final String episode) {
		Log.v(TAG, "doShowSearch: " + queryTerm + " s" + season + " e" + episode);
		showSectionsSearched = 0;
		serversSearched = 0;
		for(final PlexServer server : this.plexmediaServers.values()) {
			for(int i=0;i<server.getTvSections().size();i++) {
				String section = server.getTvSections().get(i);
				try {
				    String url = "http://" + server.getIPAddress() + ":" + server.getPort() + "/library/sections/" + section + "/search?type=2&query=" + queryTerm;
				    AsyncHttpClient httpClient = new AsyncHttpClient();
				    httpClient.get(url, new AsyncHttpResponseHandler() {
				        @Override
				        public void onSuccess(String response) {
	//			            Log.v(MainActivity.TAG, "HTTP REQUEST: " + response);
				        	showSectionsSearched++;
				        	MediaContainer mc = new MediaContainer();
				            try {
				            	mc = serial.read(MediaContainer.class, response);
				            } catch (NotFoundException e) {
				                e.printStackTrace();
				            } catch (Exception e) {
				                e.printStackTrace();
				            }
				            for(int j=0;j<mc.directories.size();j++) {
				            	shows.add(mc.directories.get(j));
				            }
				        	
				        	if(server.getTvSections().size() == showSectionsSearched) {
				        		serversSearched++;
				            	if(serversSearched == plexmediaServers.size()) {
				            		doEpisodeSearch(queryTerm, season, episode);
				            	}
				        	}
				        }
				    });
		
				} catch (Exception e) {
					Log.e(MainActivity.TAG, "Exception getting search results: " + e.toString());
				}
			}
		}
	}
	
	private void doEpisodeSearch(String queryTerm, final String season, final String episode) {
		Log.v(MainActivity.TAG, "Found shows: " + shows.size());
		for(final PlexServer server : this.plexmediaServers.values()) {
			if(shows.size() == 0) {
				feedback("Sorry, I couldn't find " + queryTerm);
				searchDialog.dismiss();
				finish();
				return;
			} else if(shows.size() == 1) {
				final PlexDirectory show = shows.get(0);
				Log.v(TAG, "Show key: " + show.getKey());
				try {
				    String url = "http://" + server.getIPAddress() + ":" + server.getPort() + "" + show.getKey();
				    AsyncHttpClient httpClient = new AsyncHttpClient();
				    httpClient.get(url, new AsyncHttpResponseHandler() {
				        @Override
				        public void onSuccess(String response) {
	//			            Log.v(MainActivity.TAG, "HTTP REQUEST: " + response);
				            MediaContainer mc = new MediaContainer();
				            try {
				            	mc = serial.read(MediaContainer.class, response);
				            } catch (NotFoundException e) {
				                e.printStackTrace();
				            } catch (Exception e) {
				                e.printStackTrace();
				            }
				            PlexDirectory foundSeason = null;
				            for(int i=0;i<mc.directories.size();i++) {
				            	PlexDirectory directory = mc.directories.get(i);
				            	if(directory.getTitle().equals("Season " + season)) {
				            		Log.v(TAG, "Found season " + season + "!!!: " + directory.getKey());
				            		foundSeason = directory;
				            		break;
				            	}
				            }
				            
				            if(foundSeason == null) {
				            	Log.e(TAG, "Sorry, I couldn't find that season.");
				            	feedback("Sorry, I couldn't find that season.");
				            	searchDialog.dismiss();
				    			finish();
				    			return;
				            } else {
				            	try {
				    			    String url = "http://" + server.getIPAddress() + ":" + server.getPort() + "" + foundSeason.getKey();
				    			    AsyncHttpClient httpClient = new AsyncHttpClient();
				    			    httpClient.get(url, new AsyncHttpResponseHandler() {
				    			        @Override
				    			        public void onSuccess(String response) {
	//			    			            Log.v(MainActivity.TAG, "HTTP REQUEST: " + response);
				    			            MediaContainer mc = new MediaContainer();
				    			            try {
				    			            	mc = serial.read(MediaContainer.class, response);
				    			            } catch (NotFoundException e) {
				    			                e.printStackTrace();
				    			            } catch (Exception e) {
				    			                e.printStackTrace();
				    			            }
				    			            
				    			            
				    			            Boolean foundEpisode = false;
				    			            for(int i=0;i<mc.videos.size();i++) {
				    			            	Log.v(TAG, "Looking at episode " + mc.videos.get(i).getIndex());
				    			            	if(mc.videos.get(i).getIndex().equals(episode) && !videoPlayed) {
				    			            		PlexVideo video = mc.videos.get(i);
				    			            		video.setServer(server);
				    			            		video.setThumb(show.getThumb());
				    			            		video.setShowTitle(show.getTitle());
//				    			            		video.setThumb(video.getGrandparentThumb());
				    			            		playVideo(video);
				    			            		foundEpisode = true;
				    			            		break;
				    			            	}
				    			            }
				    			            Log.v(TAG, "foundEpisode = " + foundEpisode);
				    			            if(foundEpisode == false) {
				    			            	feedback("Sorry, I couldn't find that episode.");
				    			            	searchDialog.dismiss();
				    			    			finish();
				    			    			return;
				    			            }
				    			        }
				    			    });
				            	} catch(Exception e) {
				            		Log.e(MainActivity.TAG, "Exception getting episode list: " + e.toString());
				            	}
				            }
				        }
				    });
				} catch (Exception e) {
					Log.e(MainActivity.TAG, "Exception getting search results: " + e.toString());
				}
			}
		}
	}
	
	private void doLatestEpisodeSearch(final String queryTerm) {
		showSectionsSearched = 0;
		serversSearched = 0;
		for(final PlexServer server : this.plexmediaServers.values()) {
			for(int i=0;i<server.getTvSections().size();i++) {
				String section = server.getTvSections().get(i);
				try {
				    String url = "http://" + server.getIPAddress() + ":" + server.getPort() + "/library/sections/" + section + "/onDeck";
				    AsyncHttpClient httpClient = new AsyncHttpClient();
				    httpClient.get(url, new AsyncHttpResponseHandler() {
				        @Override
				        public void onSuccess(String response) {
	//			            Log.v(MainActivity.TAG, "HTTP REQUEST: " + response);
				            showSectionsSearched++;
				            
				            MediaContainer mc = new MediaContainer();
				            try {
				            	mc = serial.read(MediaContainer.class, response);
				            } catch (NotFoundException e) {
				                e.printStackTrace();
				            } catch (Exception e) {
				                e.printStackTrace();
				            }
				            for(int j=0;j<mc.videos.size();j++) {
				            	PlexVideo video = mc.videos.get(j);
				            	if(video.getGrandparentTitle().toLowerCase().equals(queryTerm)) {
					            	video.setServer(server);
					            	video.setThumb(video.getGrandparentThumb());
					            	video.setShowTitle(mc.grandparentTitle);
					            	videos.add(video);
				            	}
				            }
				            
				            if(server.getTvSections().size() == showSectionsSearched) {
				            	serversSearched++;
				            	if(serversSearched == plexmediaServers.size()) {
				            		onFinishedLatestEpisodeSearch(queryTerm);
				            	}
				        	}
				        }
				    });
				} catch(Exception e) {
					Log.e(MainActivity.TAG, "Exception doing latest episode search: " + e.toString());
				}
			}
		}
	}
	
	private void onFinishedLatestEpisodeSearch(String queryTerm) {
		if(videos.size() == 0) {
			feedback("Sorry, I couldn't find " + queryTerm);
			searchDialog.dismiss();
			finish();
			return;
		} else {
			// For now, just take the first one
			playVideo(videos.get(0));
		}
	}
	
	private void doMovieSearch(String mediaType, final String queryTerm) {
		movieSectionsSearched = 0;
		serversSearched = 0;
		for(final PlexServer server : this.plexmediaServers.values()) {
			Log.v(TAG, "Searching server: " + server.getMachineIdentifier());
			for(int i=0;i<server.getMovieSections().size();i++) {
				String section = server.getMovieSections().get(i);
				try {
				    String url = "http://" + server.getIPAddress() + ":" + server.getPort() + "/library/sections/" + section + "/search?type=1&query=" + queryTerm;
				    AsyncHttpClient httpClient = new AsyncHttpClient();
				    httpClient.get(url, new AsyncHttpResponseHandler() {
				        @Override
				        public void onSuccess(String response) {
	//			            Log.v(MainActivity.TAG, "HTTP REQUEST: " + response);
				            movieSectionsSearched++;
				            
				            MediaContainer mc = new MediaContainer();
				            try {
				            	mc = serial.read(MediaContainer.class, response);
				            } catch (NotFoundException e) {
				                e.printStackTrace();
				            } catch (Exception e) {
				                e.printStackTrace();
				            }
				            for(int j=0;j<mc.videos.size();j++) {
				            	PlexVideo video = mc.videos.get(j);
				            	video.setServer(server);
//				            	video.setThumbnail(video.getThumb());
				            	video.setShowTitle(mc.grandparentTitle);
				            	videos.add(video);
				            }
				            Log.v(MainActivity.TAG, "Videos: " + mc.videos.size());
				            if(server.getMovieSections().size() == movieSectionsSearched) {
				            	serversSearched++;
				            	if(serversSearched == plexmediaServers.size()) {
				            		onMovieSearchFinished(queryTerm);
				            	}
				            }
				        }
				    });
		
				} catch (Exception e) {
					Log.e(MainActivity.TAG, "Exception getting search results: " + e.toString());
				}
			}
			
		}
	}
	
	private void onMovieSearchFinished(String queryTerm) {
		Log.v(MainActivity.TAG, "Done searching! Have videos: " + videos.size());
		PlexVideo chosenVideo = null;
		
		for(int i=0;i<videos.size();i++) {
			if(videos.get(i).getTitle().toLowerCase().equals(queryTerm)) {
				chosenVideo = videos.get(i);
			}
		}
		
		if(chosenVideo != null) {
			Log.v(MainActivity.TAG, "Chosen video: " + chosenVideo.getTitle());
			feedback("Now watching " + chosenVideo.getTitle() + " on " + client.getName());
			
			playVideo(chosenVideo);
		} else {
			Log.v(MainActivity.TAG, "Didn't find a video");
			feedback("Sorry, I couldn't find a video to play.");
			searchDialog.dismiss();
			finish();
			return;
		}
	}
	
	private void feedback(String text) {
		if(mPrefs.getInt("feedback", MainActivity.FEEDBACK_VOICE) == MainActivity.FEEDBACK_VOICE) {
			GoogleSearchApi.speak(this, text);
		} else {
			Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
		}
		Log.v(TAG, text);
	}
	
	private void playVideo(final PlexVideo video) {
		try {
//			Log.v(TAG, "Host: " + client.getHost());
//			Log.v(TAG, "Port: " + client.getPort());
//			Log.v(TAG, "key: " + video.getKey());
//			Log.v(TAG, "Machine ID: " + video.getServer().getMachineIdentifier());
		    String url = "http://" + client.getHost() + ":" + client.getPort() + "/player/playback/playMedia?machineIdentifier=" + video.getServer().getMachineIdentifier() + "&key=" + video.getKey();
		    if(mPrefs.getBoolean("resume", false) || resumePlayback) {
		    	url += "&viewOffset=" + video.getViewOffset();
		    }
		    Log.v(MainActivity.TAG, "Url: " + url);
		    AsyncHttpClient httpClient = new AsyncHttpClient();
		    httpClient.get(url, new AsyncHttpResponseHandler() {
		        @Override
		        public void onSuccess(String response) {
//		            Log.v(MainActivity.TAG, "HTTP REQUEST: " + response);
		            
		            searchDialog.dismiss();
		            
		            PlexResponse r = new PlexResponse();
		            try {
		            	r = serial.read(PlexResponse.class, response);
		            } catch (Exception e) {
		            	Log.e(MainActivity.TAG, "Exception parsing response: " + e.toString());
		            }
		            Log.v(TAG, "Playback response: " + r.getCode());
		            if(r.getCode().equals("200")) {
		            	videoPlayed = true;
		            	if(video.getType().equals("movie")) {
		            		setContentView(R.layout.now_playing_movie);
		            		TextView title = (TextView)findViewById(R.id.nowPlayingTitle);
		            		title.setText(video.getTitle());
		            		TextView genre = (TextView)findViewById(R.id.nowPlayingGenre);
		            		genre.setText(video.getGenres());
		            		TextView year = (TextView)findViewById(R.id.nowPlayingYear);
		            		year.setText(video.getYear());
		            		TextView duration = (TextView)findViewById(R.id.nowPlayingDuration);
		            		duration.setText(video.getDuration());
		            		TextView summary = (TextView)findViewById(R.id.nowPlayingSummary);
		            		summary.setText(video.getSummary());
		            	} else {
		            		setContentView(R.layout.now_playing_show);
		            		
		            		TextView showTitle = (TextView)findViewById(R.id.nowPlayingShowTitle);
		            		showTitle.setText(video.getShowTitle());
		            		TextView episodeTitle = (TextView)findViewById(R.id.nowPlayingEpisodeTitle);
		            		episodeTitle.setText(video.getTitle());
		            		TextView year = (TextView)findViewById(R.id.nowPlayingYear);
		            		year.setText(video.getYear());
		            		TextView duration = (TextView)findViewById(R.id.nowPlayingDuration);
		            		duration.setText(video.getDuration());
		            		TextView summary = (TextView)findViewById(R.id.nowPlayingSummary);
		            		summary.setText(video.getSummary());
		            	}
		            	
		            	TextView nowPlayingOnClient = (TextView)findViewById(R.id.nowPlayingOnClient);
		            	nowPlayingOnClient.setText(getResources().getString(R.string.now_playing_on) + " " + client.getName());
		            	
		            	if(!video.getThumb().equals("")) {
		            		final ScrollView layout = (ScrollView)findViewById(R.id.background);
		            		try {
		            			final String url = "http://" + video.getServer().getIPAddress() + ":" + video.getServer().getPort() + video.getThumb();
		            			Log.v(TAG, "url: " + url);
		            		    AsyncHttpClient httpClient = new AsyncHttpClient();
		            		    httpClient.get(url, new BinaryHttpResponseHandler() {
		            		        @Override
		            		        public void onSuccess(byte[] imageData) {
//		            		        	Log.v(TAG, "Response length: " + response.getBytes().length);
		            		        	InputStream is  = new ByteArrayInputStream(imageData);
		            		        	try {
											is.reset();
										} catch (IOException e) {
											e.printStackTrace();
										}
		            		        	Drawable d = Drawable.createFromStream(is, "thumb");
		            		        	d.setAlpha(80);
		            		        	layout.setBackground(d);
		            		        }
		            		    });
		            		} catch(Exception e) {
		            			e.printStackTrace();
		            		}
		            	}
		            }
		        }
		    });

		} catch (Exception e) {
			Log.e(MainActivity.TAG, "Exception trying to play video: " + e.toString());
			e.printStackTrace();
		}
	}

	@Override
    protected void onPause() {
            super.onPause();
            LocalBroadcastManager.getInstance(this).unregisterReceiver(gdmReceiver);
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
