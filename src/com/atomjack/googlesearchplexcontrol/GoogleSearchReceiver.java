package com.atomjack.googlesearchplexcontrol;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources.NotFoundException;
import android.util.Log;
import android.widget.Toast;

import com.atomjack.googlesearchplexcontrol.model.MediaContainer;
import com.atomjack.googlesearchplexcontrol.model.PlexClient;
import com.atomjack.googlesearchplexcontrol.model.PlexDirectory;
import com.atomjack.googlesearchplexcontrol.model.PlexServer;
import com.atomjack.googlesearchplexcontrol.model.PlexVideo;
import com.google.gson.Gson;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

public class GoogleSearchReceiver extends BroadcastReceiver {

	private static final String TAG = MainActivity.TAG;
	
	private Context context;
	
	private PlexServer server = null;
	private PlexClient client = null;
	
	private int movieSectionsSearched = 0;
	private int showSectionsSearched = 0;
	
	private Serializer serial = new Persister();
	
	private List<PlexVideo> videos = new ArrayList<PlexVideo>();
	
	private List<PlexDirectory> shows = new ArrayList<PlexDirectory>();
	
	private SharedPreferences mPrefs;
	
	@Override
	public void onReceive(Context context, Intent intent) {
		mPrefs = context.getSharedPreferences(MainActivity.PREFS, context.MODE_PRIVATE);
		
		String queryText = intent.getStringExtra(GoogleSearchApi.KEY_QUERY_TEXT).toLowerCase();
		
		this.context = context;
		// Queries to respond to:
		// "watch latest episode of <show>" - use on deck
//		"watch season x episode x of <show>"
//		"watch movie x"
//		"watch x" (movie)
		// "watch episode foobar of homeland" - naming episode
		
		// Ideas:
		// "navigate to homeland"
		// "navigate to homeland season 2"
		// (NOT POSSIBLE:( )
		
		
		// Only respond to queries that begin with watch
		if(queryText.startsWith("watch")) {
			Log.v(MainActivity.TAG, "GOT QUERY: " + queryText);
			
			String mediaType = ""; // movie or show
			String queryTerm = "";
			String season = "";
			String episode = "";
			String episodeSpecified = "";
			String showSpecified = "";
			
			Boolean latest = false;
			
			Pattern p = Pattern.compile( "watch movie (.*)", Pattern.DOTALL);
			Matcher matcher = p.matcher(queryText);
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
			Log.v(MainActivity.TAG, "query term: " + queryTerm);
			Log.v(MainActivity.TAG, "season: " + season);
			Log.v(MainActivity.TAG, "episode: " + episode);
			Log.v(MainActivity.TAG, "latest: " + latest);
			Log.v(TAG, "episodeSpecified: " + episodeSpecified);
			
			if(!queryTerm.equals("") || (!episodeSpecified.equals("") && !showSpecified.equals(""))) {
				Gson gson = new Gson();
				this.server = (PlexServer)gson.fromJson(mPrefs.getString("Server", ""), PlexServer.class);
				this.client = (PlexClient)gson.fromJson(mPrefs.getString("Client", ""), PlexClient.class);
				
				if(this.server != null && this.client != null) {
					Log.v(MainActivity.TAG, "Server: " + this.server.getName());
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
				} else {
					Log.v(MainActivity.TAG, "Server & Client are null!");
				}
			}
			
		}
	}

	private void doShowSearch(String episodeSpecified, final String showSpecified) {
		showSectionsSearched = 0;
		for(int i=0;i<server.getTvSections().size();i++) {
			String section = server.getTvSections().get(i);
			try {
			    String url = "http://" + server.getIPAddress() + ":32400/library/sections/" + section + "/search?type=4&query=" + episodeSpecified;
			    AsyncHttpClient client = new AsyncHttpClient();
			    client.get(url, new AsyncHttpResponseHandler() {
			        @Override
			        public void onSuccess(String response) {
			            Log.v(MainActivity.TAG, "HTTP REQUEST: " + response);
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
			            	if(mc.videos.get(j).getGrandparentTitle().toLowerCase().equals(showSpecified.toLowerCase()))
			            		videos.add(mc.videos.get(j));
			            }
			        	
			        	if(server.getTvSections().size() == showSectionsSearched) {
			        		playSpecificEpisode();
			        	}
			        }
			    });
			} catch (Exception e) {
				Log.e(MainActivity.TAG, "Exception getting search results: " + e.toString());
			}
		}
	}

	private void playSpecificEpisode() {
		if(videos.size() == 1) {
			playVideo(videos.get(0));
		} else {
			doToast("Sorry, I found more than one match. Try to be more specific?");
			GoogleSearchApi.speak(context, "Sorry, I found more than one match. Try to be more specific?");
		}
	}
	
	private void doShowSearch(final String queryTerm, final String season, final String episode) {
		Log.v(TAG, "doShowSearch: " + queryTerm + " " + season + " " + episode);
		showSectionsSearched = 0;
		for(int i=0;i<server.getTvSections().size();i++) {
			String section = server.getTvSections().get(i);
			try {
			    String url = "http://" + server.getIPAddress() + ":32400/library/sections/" + section + "/search?type=2&query=" + queryTerm;
			    AsyncHttpClient client = new AsyncHttpClient();
			    client.get(url, new AsyncHttpResponseHandler() {
			        @Override
			        public void onSuccess(String response) {
			            Log.v(MainActivity.TAG, "HTTP REQUEST: " + response);
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
			        		doEpisodeSearch(season, episode);
			        	}
			        }
			    });
	
			} catch (Exception e) {
				Log.e(MainActivity.TAG, "Exception getting search results: " + e.toString());
			}
		}
	}

	private void doEpisodeSearch(final String season, final String episode) {
		Log.v(MainActivity.TAG, "Found shows: " + shows.size());
		
		if(shows.size() == 1) {
			PlexDirectory show = shows.get(0);
			Log.v(TAG, "Show key: " + show.getKey());
			try {
			    String url = "http://" + server.getIPAddress() + ":32400" + show.getKey();
			    AsyncHttpClient client = new AsyncHttpClient();
			    client.get(url, new AsyncHttpResponseHandler() {
			        @Override
			        public void onSuccess(String response) {
			            Log.v(MainActivity.TAG, "HTTP REQUEST: " + response);
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
			            	}
			            }
			            
			            if(foundSeason == null) {
			            	Log.e(TAG, "Sorry, I couldn't find that season.");
			            	GoogleSearchApi.speak(context, "Sorry, I couldn't find that season.");
			            	doToast("Sorry, I couldn't find that season.");
			            } else {
			            	try {
			    			    String url = "http://" + server.getIPAddress() + ":32400" + foundSeason.getKey();
			    			    AsyncHttpClient client = new AsyncHttpClient();
			    			    client.get(url, new AsyncHttpResponseHandler() {
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
			    			            	if(mc.videos.get(i).getIndex().equals(episode)) {
			    			            		playVideo(mc.videos.get(i));
			    			            		foundEpisode = true;
			    			            	}
			    			            }
			    			            Log.v(TAG, "foundEpisode = " + foundEpisode);
			    			            if(foundEpisode == false) {
			    			            	GoogleSearchApi.speak(context, "Sorry, I couldn't find that episode.");
			    			            	doToast("Sorry, I couldn't find that episode.");
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
	
	private void doLatestEpisodeSearch(String queryTerm) {
		// TODO Auto-generated method stub
		
	}

	private void onShowSearchFinished(String queryTerm) {
		
	}

	private void doMovieSearch(String mediaType, final String queryTerm) {
		movieSectionsSearched = 0;
		for(int i=0;i<server.getMovieSections().size();i++) {
			String section = server.getMovieSections().get(i);
			try {
			    String url = "http://" + server.getIPAddress() + ":32400/library/sections/" + section + "/search?type=1&query=" + queryTerm;
			    AsyncHttpClient client = new AsyncHttpClient();
			    client.get(url, new AsyncHttpResponseHandler() {
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
			            	videos.add(mc.videos.get(j));
			            }
			            Log.v(MainActivity.TAG, "Videos: " + mc.videos.size());
			            if(server.getMovieSections().size() == movieSectionsSearched) {
			            	onMovieSearchFinished(queryTerm);
			            }
			        }
			    });
	
			} catch (Exception e) {
				Log.e(MainActivity.TAG, "Exception getting search results: " + e.toString());
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
			GoogleSearchApi.speak(context, "Now watching " + chosenVideo.getTitle() + " on " + client.getName());
			
			playVideo(chosenVideo);
		} else {
			Log.v(MainActivity.TAG, "Didn't find a video");
			GoogleSearchApi.speak(context, "Sorry, I couldn't find a video to play.");
			doToast("Sorry, I couldn't find a video to play.");
		}
	}
	
	private void doToast(String text) {
		Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
	}
	private void playVideo(PlexVideo video) {
		try {
		    String url = "http://" + client.getHost() + ":" + client.getPort() + "/player/playback/playMedia?machineIdentifier=" + server.getMachineIdentifier() + "&key=" + video.getKey();
		    if(mPrefs.getBoolean("resume", false)) {
		    	url += "&viewOffset=" + video.getViewOffset();
		    }
		    Log.v(MainActivity.TAG, "Url: " + url);
		    AsyncHttpClient client = new AsyncHttpClient();
		    client.get(url, new AsyncHttpResponseHandler() {
		        @Override
		        public void onSuccess(String response) {
		            Log.v(MainActivity.TAG, "HTTP REQUEST: " + response);
		           
		        }
		    });

		} catch (Exception e) {
			Log.e(MainActivity.TAG, "Exception getting clients: " + e.toString());
		}
	}
	
}
