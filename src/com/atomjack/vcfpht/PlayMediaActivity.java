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
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.atomjack.vcfpht.model.MediaContainer;
import com.atomjack.vcfpht.model.PlexClient;
import com.atomjack.vcfpht.model.PlexDirectory;
import com.atomjack.vcfpht.model.PlexResponse;
import com.atomjack.vcfpht.model.PlexServer;
import com.atomjack.vcfpht.model.PlexTrack;
import com.atomjack.vcfpht.model.PlexVideo;
import com.bugsense.trace.BugSenseHandler;
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
	private int musicSectionsSearched = 0;
	private List<PlexVideo> videos = new ArrayList<PlexVideo>();
	private Boolean videoPlayed = false;
	private List<PlexDirectory> shows = new ArrayList<PlexDirectory>();
	private Boolean resumePlayback = false;
	private List<PlexTrack> tracks = new ArrayList<PlexTrack>();
	
	protected void onCreate(Bundle savedInstanceState) {
		Logger.d("on create PlayMediaActivity");
		super.onCreate(savedInstanceState);

    BugSenseHandler.initAndStartSession(PlayMediaActivity.this, MainActivity.BUGSENSE_APIKEY);

    mPrefs = getSharedPreferences(PREFS, MODE_PRIVATE);

		setContentView(R.layout.play_media);
		Intent intent = getIntent();
		this.queryText = intent.getStringExtra("queryText");
		startup();
	}

	@Override
	public void onNewIntent(Intent intent) {
		Logger.d("ON NEW INTENT IN PLAYMEDIACTIVITY");
		String origin = intent.getStringExtra("ORIGIN");
		String from = intent.getStringExtra("FROM");
		Logger.d("origin: %s", origin);
		Logger.d("from: %s", from);
		if(from == null) {
			if(origin.equals("GoogleSearchReceiver")) {
				this.queryText = intent.getStringExtra("queryText");
				startup();
			}
		} else if(from.equals("GDMReceiver")) {
			videoPlayed = false;
			Logger.d("Origin: %s", intent.getStringExtra("ORIGIN"));
			this.plexmediaServers = VoiceControlForPlexApplication.getPlexMediaServers();
			setClient();
		}
	}
	
	private void startup() {
        tracks = new ArrayList<PlexTrack>();
        videos = new ArrayList<PlexVideo>();
        shows = new ArrayList<PlexDirectory>();

		if(searchDialog == null) {
			searchDialog = new Dialog(this);
		}
		searchDialog.setCancelable(true);
		searchDialog.setContentView(R.layout.search_popup);
		searchDialog.setTitle("Searching");
		
		searchDialog.show();
		
		Gson gson = new Gson();
		PlexServer defaultServer = (PlexServer)gson.fromJson(mPrefs.getString("Server", ""), PlexServer.class);
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
				Logger.d("ip: %s", server.getAddress());
				Logger.d("port: %s", server.getPort());
				try {
				    String url = "http://" + server.getAddress() + ":" + server.getPort() + "/clients";
				    AsyncHttpClient httpClient = new AsyncHttpClient();
				    httpClient.get(url, new AsyncHttpResponseHandler() {
				        @Override
				        public void onSuccess(String response) {
				            Logger.d("HTTP REQUEST: %s", response);
				            serversScanned++;
				            MediaContainer mc = new MediaContainer();
				            try {
				            	mc = serial.read(MediaContainer.class, response);
				            } catch (NotFoundException e) {
				                e.printStackTrace();
				            } catch (Exception e) {
				                e.printStackTrace();
				            }
				            Logger.d("Clients: %d", mc.clients.size());
				            for(int i=0;i<mc.clients.size();i++) {
				            	clients.add(mc.clients.get(i));
				            }
				            if(serversScanned == plexmediaServers.size()) {
				            	handleVoiceSearch();
				            }
				        }
				    });

				} catch (Exception e) {
					Logger.e("Exception getting clients: %s", e.toString());
				}
			}
		}
	}

	private void handleVoiceSearch() {
		Logger.d("GOT QUERY: %s", queryText);
		
		resumePlayback = false;
		String mediaType = ""; // movie or show or music
		String queryTerm = "";
		String season = "";
		String episode = "";
		String episodeSpecified = "";
		String showSpecified = "";
		Boolean latest = false;
    Boolean next = false;
		String specifiedClient = "";
		
		// Music
		String artist = "";
		String track = "";
		
		// If the query spoken ends with "on <something>", check to see if the <something> matches the name of a client to play the media on
		Pattern p = Pattern.compile( "on ([^ ]+)$", Pattern.DOTALL);
		Matcher matcher = p.matcher(queryText);
		Gson gson = new Gson();
		this.client = null;
		if(matcher.find()) {
			specifiedClient = matcher.group(1);
			
			Logger.d("Clients: %d", clients.size());
			Logger.d("query text now %s", queryText);
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
		
		Logger.d("Servers: %d", VoiceControlForPlexApplication.getPlexMediaServers().size());
		
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
      p = Pattern.compile("watch the next episode of (.*)");
      matcher = p.matcher(queryText);

      if(matcher.find()) {
        mediaType = "show";
        next = true;
        queryTerm = matcher.group(1);
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
		if(mediaType.equals("")) {
			p = Pattern.compile("watch (.*) episode (.*)");
			matcher = p.matcher(queryText);
			if(matcher.find()) {
				mediaType = "show";
				episodeSpecified = matcher.group(2);
				showSpecified = matcher.group(1);
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
		if(mediaType.equals("")) {
			p = Pattern.compile("listen to (.*) by (.*)");
			matcher = p.matcher(queryText);
			if(matcher.find()) {
				mediaType = "music";
				track = matcher.group(1);
				artist = matcher.group(2);
			}
		}
		
		Logger.d("media type: %s", mediaType);
		Logger.d("query term: !%s!", queryTerm);
		Logger.d("season: %s", season);
		Logger.d("episode: %s", episode);
    Logger.d("latest: %s", latest);
    Logger.d("next: %s", next);
		Logger.d("episodeSpecified: %s", episodeSpecified);
        Logger.d("showSpecified: %s", showSpecified);
		
		if(!queryTerm.equals("") || (!episodeSpecified.equals("") && !showSpecified.equals("")) || (!artist.equals("") && !track.equals(""))) {
//			if(this.server != null && this.client != null) {
				if(mediaType.equals("movie")) {
					doMovieSearch(mediaType, queryTerm);
				} else if(mediaType.equals("show")) {
					if(next == true) {
						doNextEpisodeSearch(queryTerm);
          } else if(latest == true) {
            doLatestEpisodeSearch(queryTerm);
					} else if(!episodeSpecified.equals("") && !showSpecified.equals("")) {
						doShowSearch(episodeSpecified, showSpecified);
					} else {
						doShowSearch(queryTerm, season, episode);
					}
				} else if(mediaType.equals("music")) {
					Logger.d("Searching for %s by %s.", track, artist);
					searchForSong(artist, track);
				} else {
                    searchDialog.dismiss();
                    feedback("Sorry, I couldn't find anything to play.");
                    finish();
                    return;
                }
//			} else {
//				Logger.d("Server & Client are null!");
//			}
		} else {
			searchDialog.dismiss();
			feedback("Sorry, I couldn't find anything to play.");
			finish();
			return;
		}
	}
	
	private void searchForSong(final String artist, final String track) {
		musicSectionsSearched = 0;
		serversSearched = 0;
		Logger.d("Servers: %d", this.plexmediaServers.size());
		for(final PlexServer server : this.plexmediaServers.values()) {
			if(server.getMusicSections().size() == 0) {
				serversSearched++;
				if(serversSearched == plexmediaServers.size()) {
					if(tracks.size() > 0) {
						playTrack(tracks.get(0));
					} else {
						feedback("Sorry, I couldn't find a track to play.");
						searchDialog.dismiss();
						finish();
						return;
					}
				}
			}
			for(int i=0;i<server.getMusicSections().size();i++) {
				String section = server.getMusicSections().get(i);
				try {
				    String url = "http://" + server.getAddress() + ":" + server.getPort() + "/library/sections/" + section + "/search?type=10&query=" + track;
				    Logger.d("URL: %s", url);
				    AsyncHttpClient httpClient = new AsyncHttpClient();
				    httpClient.get(url, new AsyncHttpResponseHandler() {
				        @Override
				        public void onSuccess(String response) {
//				            Logger.d("HTTP REQUEST: %s", response);
				        	musicSectionsSearched++;
				        	MediaContainer mc = new MediaContainer();
				            try {
				            	mc = serial.read(MediaContainer.class, response);
				            } catch (NotFoundException e) {
				                e.printStackTrace();
				            } catch (Exception e) {
				                e.printStackTrace();
				            }
				            for(int j=0;j<mc.tracks.size();j++) {
				            	PlexTrack thisTrack = mc.tracks.get(j);
				            	thisTrack.setArtist(thisTrack.getGrandparentTitle());
				            	thisTrack.setAlbum(thisTrack.getParentTitle());
				            	Logger.d("Track: %s by %s.", thisTrack.getTitle(), thisTrack.getArtist());
				            	if(thisTrack.getArtist().toLowerCase().equals(artist.toLowerCase())) {
				            		thisTrack.setServer(server);
				            		tracks.add(thisTrack);
				            	}
				            }

				        	if(server.getMusicSections().size() == musicSectionsSearched) {
				        		serversSearched++;
				            	if(serversSearched == plexmediaServers.size()) {
//				            		playSpecificEpisode();
				            		Logger.d("found music to play.");
				            		if(tracks.size() > 0) {
										playTrack(tracks.get(0));
									} else {
										feedback("Sorry, I couldn't find a track to play.");
										searchDialog.dismiss();
										finish();
										return;
									}
				            	}
				        	}
				        }
				    });
				} catch (Exception e) {
					Logger.e("Exception getting search results: %s", e.toString());
					e.printStackTrace();
				}
			}
		}
	}

	private void doShowSearch(String episodeSpecified, final String showSpecified) {
		showSectionsSearched = 0;
		serversSearched = 0;
		for(final PlexServer server : this.plexmediaServers.values()) {
            if(server.getTvSections().size() == 0) {
                serversSearched++;
                if(serversSearched == plexmediaServers.size()) {
                    playSpecificEpisode();
                }
            }
			for(int i=0;i<server.getTvSections().size();i++) {
				String section = server.getTvSections().get(i);
				try {
				    String url = "http://" + server.getAddress() + ":" + server.getPort() + "/library/sections/" + section + "/search?type=4&query=" + episodeSpecified;
				    AsyncHttpClient httpClient = new AsyncHttpClient();
				    httpClient.get(url, new AsyncHttpResponseHandler() {
				        @Override
				        public void onSuccess(String response) {
	//			            Logger.d("HTTP REQUEST: %s", response);
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
				            	Logger.d("Show: %s", mc.videos.get(j).getGrandparentTitle());
				            	PlexVideo video = mc.videos.get(j);
				            	if(video.getGrandparentTitle().toLowerCase().equals(showSpecified.toLowerCase())) {
				            		video.setServer(server);
				            		video.setThumb(video.getGrandparentThumb());
				            		video.setShowTitle(video.getGrandparentTitle());
                        Logger.d("Adding %s - %s.", video.getShowTitle(), video.getTitle());
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
					Logger.e("Exception getting search results: %s", e.toString());
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
		Logger.d("doShowSearch: %s s%s e%s", queryTerm, season, episode);
		showSectionsSearched = 0;
		serversSearched = 0;
		for(final PlexServer server : this.plexmediaServers.values()) {
            Logger.d("Searching server %s", server.getName());
            if(server.getTvSections().size() == 0) {
                Logger.d("%s has no tv sections", server.getName());
                serversSearched++;
                if(serversSearched == plexmediaServers.size()) {
                    doEpisodeSearch(queryTerm, season, episode);
                }
            }
			for(int i=0;i<server.getTvSections().size();i++) {
				String section = server.getTvSections().get(i);
				try {
				    String url = "http://" + server.getAddress() + ":" + server.getPort() + "/library/sections/" + section + "/search?type=2&query=" + queryTerm;
				    AsyncHttpClient httpClient = new AsyncHttpClient();
				    httpClient.get(url, new AsyncHttpResponseHandler() {
				        @Override
				        public void onSuccess(String response) {
	//			            Logger.d("HTTP REQUEST: %s", response);
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
					Logger.e("Exception getting search results: %s", e.toString());
				}
			}
		}
	}
	
	private void doEpisodeSearch(String queryTerm, final String season, final String episode) {
		Logger.d("Found shows: %d", shows.size());
        serversSearched = 0;
		for(final PlexServer server : this.plexmediaServers.values()) {
			if(shows.size() == 0 && serversSearched == this.plexmediaServers.size()) {
                serversSearched++;
				feedback("Sorry, I couldn't find " + queryTerm);
				searchDialog.dismiss();
				finish();
				return;
			} else if(shows.size() == 1) {
				final PlexDirectory show = shows.get(0);
				Logger.d("Show key: %s", show.getKey());
				try {
				    String url = "http://" + server.getAddress() + ":" + server.getPort() + "" + show.getKey();
				    AsyncHttpClient httpClient = new AsyncHttpClient();
				    httpClient.get(url, new AsyncHttpResponseHandler() {
				        @Override
				        public void onSuccess(String response) {
	//			            Logger.d("HTTP REQUEST: %s", response);
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
				            		Logger.d("Found season %s: %s.", season, directory.getKey());
				            		foundSeason = directory;
				            		break;
				            	}
				            }
				            
				            if(foundSeason == null && serversSearched == plexmediaServers.size() && !videoPlayed) {
                                serversSearched++;
				            	feedback("Sorry, I couldn't find that season.");
				            	searchDialog.dismiss();
				    			finish();
				    			return;
				            } else if(foundSeason != null) {
				            	try {
				    			    String url = "http://" + server.getAddress() + ":" + server.getPort() + "" + foundSeason.getKey();
				    			    AsyncHttpClient httpClient = new AsyncHttpClient();
				    			    httpClient.get(url, new AsyncHttpResponseHandler() {
				    			        @Override
				    			        public void onSuccess(String response) {
	//			    			            Logger.d("HTTP REQUEST: %s", response);
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
				    			            	Logger.d("Looking at episode %d", mc.videos.get(i).getIndex());
				    			            	if(mc.videos.get(i).getIndex().equals(episode) && !videoPlayed) {
                                                    serversSearched++;
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
				    			            Logger.d("foundEpisode = %s", foundEpisode);
				    			            if(foundEpisode == false && serversSearched == plexmediaServers.size() && !videoPlayed) {
                                                serversSearched++;
				    			            	feedback("Sorry, I couldn't find that episode.");
				    			            	searchDialog.dismiss();
				    			    			finish();
				    			    			return;
				    			            }
				    			        }
				    			    });
				            	} catch(Exception e) {
				            		Logger.e("Exception getting episode list: %s", e.toString());
                        e.printStackTrace();
				            	}
				            }
				        }
				    });
				} catch (Exception e) {
					Logger.e("Exception getting search results: %s", e.toString());
				}
			}
		}
	}

  private void doLatestEpisodeSearch(final String queryTerm) {
    Logger.d("doLatestEpisodeSearch: %s", queryTerm);
    showSectionsSearched = 0;
    serversSearched = 0;
    for(final PlexServer server : this.plexmediaServers.values()) {
      Logger.d("Searching server %s", server.getName());
      if(server.getTvSections().size() == 0) {
        Logger.d(server.getName() + " has no tv sections");
        serversSearched++;
        if(serversSearched == plexmediaServers.size()) {
          doLatestEpisode();
        }
      }
      for(int i=0;i<server.getTvSections().size();i++) {
        String section = server.getTvSections().get(i);
        try {
          String url = "http://" + server.getAddress() + ":" + server.getPort() + "/library/sections/" + section + "/search?type=2&query=" + queryTerm;
          AsyncHttpClient httpClient = new AsyncHttpClient();
          httpClient.get(url, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(String response) {
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
                PlexDirectory show = mc.directories.get(j);
                show.server = server;
                shows.add(show);
              }

              if(server.getTvSections().size() == showSectionsSearched) {
                serversSearched++;
                if(serversSearched == plexmediaServers.size()) {
                  doLatestEpisode();
                }
              }
            }
          });

        } catch (Exception e) {
          Logger.e("Exception getting search results: %s", e.toString());
        }
      }
    }
  }

  private void doLatestEpisode() {
    // For now, just grab the latest show by season/episode
    try {
      final PlexDirectory show = shows.get(0);
      String url = "http://" + show.server.getAddress() + ":" + show.server.getPort() + "/library/metadata/" + show.ratingKey + "/allLeaves";
      AsyncHttpClient httpClient = new AsyncHttpClient();
      httpClient.get(url, new AsyncHttpResponseHandler() {
        @Override
        public void onSuccess(String response) {
          showSectionsSearched++;
          MediaContainer mc = new MediaContainer();
          try {
            mc = serial.read(MediaContainer.class, response);
          } catch (NotFoundException e) {
            e.printStackTrace();
          } catch (Exception e) {
            e.printStackTrace();
          }
          PlexVideo latestVideo = null;
          for(int j=0;j<mc.videos.size();j++) {
            PlexVideo video = mc.videos.get(j);
            if(latestVideo == null || latestVideo.airDate().before(video.airDate()))
              latestVideo = video;
          }
          latestVideo.setServer(show.server);
          Logger.d("Found video: %s", latestVideo.airDate());
          if(latestVideo != null) {
            playVideo(latestVideo);
          } else {
            feedback("Sorry, I couldn't find a video to play.");
          }
        }
      });

    } catch (Exception e) {
      Logger.e("Exception getting search results: %s", e.toString());
    }
  }

	private void doNextEpisodeSearch(final String queryTerm) {
		showSectionsSearched = 0;
		serversSearched = 0;
		for(final PlexServer server : this.plexmediaServers.values()) {
            if(server.getTvSections().size() == 0) {
                serversSearched++;
                if(serversSearched == plexmediaServers.size()) {
                    onFinishedNextEpisodeSearch(queryTerm);
                }
            }
			for(int i=0;i<server.getTvSections().size();i++) {
				String section = server.getTvSections().get(i);
				try {
				    String url = "http://" + server.getAddress() + ":" + server.getPort() + "/library/sections/" + section + "/onDeck";
				    AsyncHttpClient httpClient = new AsyncHttpClient();
				    httpClient.get(url, new AsyncHttpResponseHandler() {
				        @Override
				        public void onSuccess(String response) {
	//			            Logger.d("HTTP REQUEST: %s", response);
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
					            	video.setShowTitle(video.getGrandparentTitle());
					            	videos.add(video);
				            	}
				            }

				            if(server.getTvSections().size() == showSectionsSearched) {
                                serversSearched++;
				            	if(serversSearched == plexmediaServers.size()) {
				            		onFinishedNextEpisodeSearch(queryTerm);
				            	}
				        	}
				        }
				    });
				} catch(Exception e) {
					Logger.e("Exception doing latest episode search: %s", e.toString());
				}
			}
		}
	}
	
	private void onFinishedNextEpisodeSearch(String queryTerm) {
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
			Logger.d("Searching server: %s", server.getMachineIdentifier());
            if(server.getMovieSections().size() == 0) {
                serversSearched++;
                if(serversSearched == plexmediaServers.size()) {
                    onMovieSearchFinished(queryTerm);
                }
            }
			for(int i=0;i<server.getMovieSections().size();i++) {
				String section = server.getMovieSections().get(i);
				try {
				    String url = "http://" + server.getAddress() + ":" + server.getPort() + "/library/sections/" + section + "/search?type=1&query=" + queryTerm;
				    AsyncHttpClient httpClient = new AsyncHttpClient();
				    httpClient.get(url, new AsyncHttpResponseHandler() {
				        @Override
				        public void onSuccess(String response) {
	//			            Logger.d("HTTP REQUEST: %s", response);
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
				            Logger.d("Videos: %d", mc.videos.size());
				            if(server.getMovieSections().size() == movieSectionsSearched) {
				            	serversSearched++;
				            	if(serversSearched == plexmediaServers.size()) {
				            		onMovieSearchFinished(queryTerm);
				            	}
				            }
				        }
				    });
		
				} catch (Exception e) {
					Logger.e("Exception getting search results: %s", e.toString());
				}
			}
			
		}
	}
	
	private void onMovieSearchFinished(String queryTerm) {
		Logger.d("Done searching! Have videos: %d", videos.size());
		PlexVideo chosenVideo = null;
		
		for(int i=0;i<videos.size();i++) {
			if(videos.get(i).getTitle().toLowerCase().equals(queryTerm)) {
				chosenVideo = videos.get(i);
			}
		}
		
		if(chosenVideo != null) {
			Logger.d("Chosen video: %s", chosenVideo.getTitle());
			feedback("Now watching " + chosenVideo.getTitle() + " on " + client.getName());
			
			playVideo(chosenVideo);
		} else {
			Logger.d("Didn't find a video");
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
		Logger.d(text);
	}
	
	private void playTrack(final PlexTrack track) {
		try {
//			Logger.d("Host: %s", client.getHost());
//			Logger.d("Port: %s", client.getPort());
//			Logger.d("key: %s", video.getKey());
//			Logger.d("Machine ID: %s", video.getServer().getMachineIdentifier());
		    String url = "http://" + client.getHost() + ":" + client.getPort() + "/player/playback/playMedia?machineIdentifier=" + track.getServer().getMachineIdentifier() + "&key=" + track.getKey();
		    if(mPrefs.getBoolean("resume", false) || resumePlayback) {
		    	url += "&viewOffset=" + track.getViewOffset();
		    }
		    Logger.d("Url: %s", url);
		    AsyncHttpClient httpClient = new AsyncHttpClient();
		    httpClient.get(url, new AsyncHttpResponseHandler() {
		        @Override
		        public void onSuccess(String response) {
//		            Logger.d("HTTP REQUEST: %s", response);
		            
		            searchDialog.dismiss();
		            
		            PlexResponse r = new PlexResponse();
		            try {
		            	r = serial.read(PlexResponse.class, response);
		            } catch (Exception e) {
		            	Logger.e("Exception parsing response: %s", e.toString());
		            }
                  Boolean passed = true;
                  if(r.getCode() != null) {
                      if(!r.getCode().equals("200")) {
                          passed = false;
                      }
                  }
                  Logger.d("Playback response: %s", r.getCode());
                  if(passed) {
		            	setContentView(R.layout.now_playing_music);
		            	
		            	TextView artist = (TextView)findViewById(R.id.nowPlayingArtist);
		            	artist.setText(track.getArtist());
	            		TextView album = (TextView)findViewById(R.id.nowPlayingAlbum);
	            		album.setText(track.getAlbum());
	            		TextView title = (TextView)findViewById(R.id.nowPlayingTitle);
	            		title.setText(track.getTitle());
	            		
	            		TextView nowPlayingOnClient = (TextView)findViewById(R.id.nowPlayingOnClient);
		            	nowPlayingOnClient.setText(getResources().getString(R.string.now_playing_on) + " " + client.getName());
		            	
	            		if(!track.getThumb().equals("")) {
//	            			final RelativeLayout layout = (RelativeLayout)findViewById(R.id.background);
		            		try {
		            			final String url = "http://" + track.getServer().getAddress() + ":" + track.getServer().getPort() + track.getThumb();
		            			Logger.d("url: %s", url);
		            		    AsyncHttpClient httpClient = new AsyncHttpClient();
		            		    httpClient.get(url, new BinaryHttpResponseHandler() {
		            		        @Override
		            		        public void onSuccess(byte[] imageData) {
//		            		        	Logger.d("Response length: %s", response.getBytes().length);
		            		        	InputStream is  = new ByteArrayInputStream(imageData);
		            		        	try {
											is.reset();
										} catch (IOException e) {
											e.printStackTrace();
										}
		            		        	Drawable d = Drawable.createFromStream(is, "thumb");
		            		        	d.setAlpha(80);
//		            		        	layout.setBackground(d);
		            		        	ImageView nowPlayingImage = (ImageView)findViewById(R.id.nowPlayingImage);
		            		        	nowPlayingImage.setImageDrawable(d);
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
			Logger.e("Exception trying to play video: %s", e.toString());
			e.printStackTrace();
		}
	}
	
	private void playVideo(final PlexVideo video) {
		Logger.d("Playing video: %s", video.getTitle());
		try {
			Logger.d("Host: %s", client.getHost());
			Logger.d("Port: %s", client.getPort());
			Logger.d("key: %s", video.getKey());
			Logger.d("Machine ID: %s", video.getServer().getMachineIdentifier());
		    String url = "http://" + client.getHost() + ":" + client.getPort() + "/player/playback/playMedia?machineIdentifier=" + video.getServer().getMachineIdentifier() + "&key=" + video.getKey();
		    if(mPrefs.getBoolean("resume", false) || resumePlayback) {
		    	url += "&viewOffset=" + video.getViewOffset();
		    }
		    Logger.d("Url: %s", url);
		    AsyncHttpClient httpClient = new AsyncHttpClient();
		    httpClient.get(url, new AsyncHttpResponseHandler() {
		        @Override
		        public void onSuccess(String response) {
//		            Logger.d("HTTP REQUEST: %s", response);
		            searchDialog.dismiss();
		            
		            PlexResponse r = new PlexResponse();
		            try {
		            	r = serial.read(PlexResponse.class, response);
		            } catch (Exception e) {
		            	Logger.e("Exception parsing response: %s", e.toString());
		            }
		            Boolean passed = true;
		            if(r.getCode() != null) {
		            	if(!r.getCode().equals("200")) {
		            		passed = false;
		            	}
		            }
		            Logger.d("Playback response: %s", r.getCode());
		            if(passed) {
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
		            			final String url = "http://" + video.getServer().getAddress() + ":" + video.getServer().getPort() + video.getThumb();
		            			Logger.d("url: %s", url);
		            		    AsyncHttpClient httpClient = new AsyncHttpClient();
		            		    httpClient.get(url, new BinaryHttpResponseHandler() {
		            		        @Override
		            		        public void onSuccess(byte[] imageData) {
//		            		        	Logger.d("Response length: %d", response.getBytes().length);
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
			Logger.e("Exception trying to play video: %s", e.toString());
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
    LocalBroadcastManager.getInstance(this).registerReceiver(gdmReceiver, filters);
  }
}
