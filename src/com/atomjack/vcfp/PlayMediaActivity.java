package com.atomjack.vcfp;

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
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexDirectory;
import com.atomjack.vcfp.model.PlexResponse;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.model.PlexVideo;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpMediaContainerHandler;
import com.atomjack.vcfp.net.PlexHttpResponseHandler;
import com.bugsense.trace.BugSenseHandler;
import com.google.gson.Gson;

public class PlayMediaActivity extends Activity {
	public final static String PREFS = MainActivity.PREFS;
	private SharedPreferences mPrefs;
	private String queryText;
	private Dialog searchDialog = null;
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
	private List<PlexDirectory> albums = new ArrayList<PlexDirectory>();

	protected void onCreate(Bundle savedInstanceState) {
		Logger.d("on create PlayMediaActivity");
		super.onCreate(savedInstanceState);

//    BugSenseHandler.initAndStartSession(PlayMediaActivity.this, MainActivity.BUGSENSE_APIKEY);

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
		PlexServer defaultServer = gson.fromJson(mPrefs.getString("Server", ""), PlexServer.class);
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
        String url = "http://" + server.getAddress() + ":" + server.getPort() + "/clients";
        PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
        {
          @Override
          public void onSuccess(MediaContainer mc)
          {
            serversScanned++;
            Logger.d("Clients: %d", mc.clients.size());
            for(int i=0;i<mc.clients.size();i++) {
              clients.add(mc.clients.get(i));
            }
            if(serversScanned == plexmediaServers.size()) {
              handleVoiceSearch();
            }
          }
        });
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
		String album = "";

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
			this.client = gson.fromJson(mPrefs.getString("Client", ""), PlexClient.class);
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
      p = Pattern.compile("listen to the album (.*) by (.*)");
      matcher = p.matcher(queryText);
      if(matcher.find()) {
        mediaType = "music";
        album = matcher.group(1);
        artist = matcher.group(2);
      }
    }
    if(mediaType.equals("")) {
      p = Pattern.compile("listen to the album (.*)");
      matcher = p.matcher(queryText);
      if(matcher.find()) {
        mediaType = "music";
        album = matcher.group(1);
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
    Logger.d("album: %s", album);
		Logger.d("episodeSpecified: %s", episodeSpecified);
    Logger.d("showSpecified: %s", showSpecified);
		
		if(!queryTerm.equals("") || (!episodeSpecified.equals("") && !showSpecified.equals("")) || (!artist.equals("") && (!track.equals("")) || !album.equals(""))) {
				if(mediaType.equals("movie")) {
					doMovieSearch(queryTerm);
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
          if(!album.equals("")) {
            Logger.d("Searching for album %s by %s.", album, artist);
            searchForAlbum(artist, album);
          } else {
            Logger.d("Searching for %s by %s.", track, artist);
            searchForSong(artist, track);
          }
				} else {
          searchDialog.dismiss();
          feedback("Sorry, I couldn't find anything to play.");
          finish();
          return;
        }
		} else {
			searchDialog.dismiss();
			feedback("Sorry, I couldn't find anything to play.");
			finish();
			return;
		}
	}

  private void searchForAlbum(final String artist, final String album) {
    Logger.d("Searching for album %s by %s.", album, artist);
    musicSectionsSearched = 0;
    serversSearched = 0;
    Logger.d("Servers: %d", this.plexmediaServers.size());
    for(final PlexServer server : this.plexmediaServers.values()) {
      if(server.getMusicSections().size() == 0) {
        serversSearched++;
        if(serversSearched == plexmediaServers.size()) {
          if(albums.size() == 1) {
            playAlbum(albums.get(0));
          } else {
            feedback(albums.size() > 1 ? "I found more than one matching album. Please specify artist." : "Sorry, I couldn't find an album to play.");
            searchDialog.dismiss();
            finish();
            return;
          }
        }
      }
      for(int i=0;i<server.getMusicSections().size();i++) {
        String section = server.getMusicSections().get(i);
        String url = "http://" + server.getAddress() + ":" + server.getPort() + "/library/sections/" + section + "/search?type=9&query=" + album;
        PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
        {
          @Override
          public void onSuccess(MediaContainer mc)
          {
            musicSectionsSearched++;
            for(int j=0;j<mc.directories.size();j++) {
              PlexDirectory thisAlbum = mc.directories.get(j);
              Logger.d("Album: %s by %s.", thisAlbum.title, thisAlbum.parentTitle);
              if(compareTitle(thisAlbum.title, album) || artist.equals("")) {
//              if(thisAlbum.title.toLowerCase().equals(album.toLowerCase()) || artist.equals("")) {
                Logger.d("adding album");
                thisAlbum.server = server;
                albums.add(thisAlbum);
              }
            }

            if(server.getMusicSections().size() == musicSectionsSearched) {
              serversSearched++;
              if(serversSearched == plexmediaServers.size()) {
                Logger.d("found %d albums to play.", albums.size());
                if(albums.size() == 1) {
                  playAlbum(albums.get(0));
                } else {
                  Boolean exactMatch = false;
                  for(int k=0;k<albums.size();k++) {
                    if(albums.get(k).title.toLowerCase().equals(album.toLowerCase())) {
                      exactMatch = true;
                      playAlbum(albums.get(k));
                    }
                  }
                  if(!exactMatch) {
                    feedback(albums.size() > 1 ? "I found more than one matching album. Please specify artist." : "Sorry, I couldn't find an album to play.");
                    searchDialog.dismiss();
                    finish();
                    return;
                  }
                }
              }

            }
          }
        });
      }
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
            searchDialog.dismiss();
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
        String url = "http://" + server.getAddress() + ":" + server.getPort() + "/library/sections/" + section + "/search?type=10&query=" + track;
        PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
        {
          @Override
          public void onSuccess(MediaContainer mc)
          {
            musicSectionsSearched++;
            for(int j=0;j<mc.tracks.size();j++) {
              PlexTrack thisTrack = mc.tracks.get(j);
              thisTrack.setArtist(thisTrack.getGrandparentTitle());
              thisTrack.setAlbum(thisTrack.getParentTitle());
              Logger.d("Track: %s by %s.", thisTrack.getTitle(), thisTrack.getArtist());
              if(compareTitle(thisTrack.getArtist(), artist)) {
//              if(thisTrack.getArtist().toLowerCase().equals(artist.toLowerCase())) {
                thisTrack.setServer(server);
                tracks.add(thisTrack);
              }
            }

            if(server.getMusicSections().size() == musicSectionsSearched) {
              serversSearched++;
              if(serversSearched == plexmediaServers.size()) {
                Logger.d("found music to play.");
                if(tracks.size() > 0) {
                  playTrack(tracks.get(0));
                } else {
                  Boolean exactMatch = false;
                  for(int k=0;k<albums.size();k++) {
                    if(tracks.get(k).getArtist().toLowerCase().equals(artist.toLowerCase())) {
                      exactMatch = true;
                      playTrack(tracks.get(k));
                    }
                  }
                  if(!exactMatch) {
                    feedback("Sorry, I couldn't find a track to play.");
                    searchDialog.dismiss();
                    finish();
                    return;
                  }
                }
              }
            }
          }
        });
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
          playSpecificEpisode(showSpecified);
        }
      }
			for(int i=0;i<server.getTvSections().size();i++) {
				String section = server.getTvSections().get(i);
        String url = "http://" + server.getAddress() + ":" + server.getPort() + "/library/sections/" + section + "/search?type=4&query=" + episodeSpecified;
        PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
        {
          @Override
          public void onSuccess(MediaContainer mc)
          {
            showSectionsSearched++;
            for(int j=0;j<mc.videos.size();j++) {
              Logger.d("Show: %s", mc.videos.get(j).getGrandparentTitle());
              PlexVideo video = mc.videos.get(j);
              if(compareTitle(video.getGrandparentTitle(), showSpecified)) {
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
                playSpecificEpisode(showSpecified);
              }
            }
          }
        });
			}
		}
	}

	private void playSpecificEpisode(String showSpecified) {
		if(videos.size() == 0) {
			feedback("Sorry, I couldn't find the episode you specified.");
			searchDialog.dismiss();
			finish();
			return;
		} else if(videos.size() == 1) {
			playVideo(videos.get(0));
		} else {
      Boolean exactMatch = false;
      for(int i=0;i<videos.size();i++) {
        if(videos.get(i).getGrandparentTitle().toLowerCase().equals(showSpecified.toLowerCase())) {
          exactMatch = true;
          playVideo(videos.get(i));
          break;
        }
      }
      if(!exactMatch) {
        feedback("Sorry, I found more than one matching show. Try to be more specific?");
        searchDialog.dismiss();
        finish();
        return;
      }
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
        String url = "http://" + server.getAddress() + ":" + server.getPort() + "/library/sections/" + section + "/search?type=2&query=" + queryTerm;
        PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
        {
          @Override
          public void onSuccess(MediaContainer mc)
          {
            showSectionsSearched++;
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
        String url = "http://" + server.getAddress() + ":" + server.getPort() + "" + show.getKey();
        PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
        {
          @Override
          public void onSuccess(MediaContainer mc)
          {
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
              String url = "http://" + server.getAddress() + ":" + server.getPort() + "" + foundSeason.getKey();
              PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
              {
                @Override
                public void onSuccess(MediaContainer mc)
                {
                  Boolean foundEpisode = false;
                  for(int i=0;i<mc.videos.size();i++) {
                    Logger.d("Looking at episode %s", mc.videos.get(i).getIndex());
                    if(mc.videos.get(i).getIndex().equals(episode) && !videoPlayed) {
                      serversSearched++;
                      PlexVideo video = mc.videos.get(i);
                      video.setServer(server);
                      video.setThumb(show.getThumb());
                      video.setShowTitle(show.getTitle());
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
            }
          }
        });
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
          doLatestEpisode(queryTerm);
        }
      }
      for(int i=0;i<server.getTvSections().size();i++) {
        String section = server.getTvSections().get(i);
        String url = "http://" + server.getAddress() + ":" + server.getPort() + "/library/sections/" + section + "/search?type=2&query=" + queryTerm;
        PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
        {
          @Override
          public void onSuccess(MediaContainer mc)
          {
            showSectionsSearched++;
            for(int j=0;j<mc.directories.size();j++) {
              PlexDirectory show = mc.directories.get(j);
              if(compareTitle(show.title, queryTerm)) {
                show.server = server;
                shows.add(show);
                Logger.d("Adding %s", show.title);
              }
            }

            if(server.getTvSections().size() == showSectionsSearched) {
              serversSearched++;
              if(serversSearched == plexmediaServers.size()) {
                doLatestEpisode(queryTerm);
              }
            }
          }
        });
      }
    }
  }

  private void doLatestEpisode(String queryTerm) {
    if(shows.size() == 0) {
      feedback("Sorry, I couldn't find a video to play.");
      searchDialog.dismiss();
      finish();
      return;
    }
    PlexDirectory chosenShow = null;
    if(shows.size() > 1) {
      for(int i=0;i<shows.size();i++) {
        PlexDirectory show = shows.get(i);
        if(show.title.toLowerCase().equals(queryTerm.toLowerCase())) {
          chosenShow = show;
          break;
        }
      }
    } else {
      chosenShow = shows.get(0);
    }

    if(chosenShow == null) {
      feedback("I found more than one matching show. Please be more specific.");
      searchDialog.dismiss();
      finish();
      return;
    }
    final PlexDirectory show = chosenShow;
    String url = "http://" + show.server.getAddress() + ":" + show.server.getPort() + "/library/metadata/" + show.ratingKey + "/allLeaves";
    PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
    {
      @Override
      public void onSuccess(MediaContainer mc)
      {
        showSectionsSearched++;
        PlexVideo latestVideo = null;
        for(int j=0;j<mc.videos.size();j++) {
          PlexVideo video = mc.videos.get(j);
          if(latestVideo == null || latestVideo.airDate().before(video.airDate())) {
            video.setShowTitle(video.getGrandparentTitle());
            latestVideo = video;
          }
        }
        latestVideo.setServer(show.server);
        Logger.d("Found video: %s", latestVideo.airDate());
        if(latestVideo != null) {
          playVideo(latestVideo);
        } else {
          feedback("Sorry, I couldn't find a video to play.");
          searchDialog.dismiss();
          finish();
          return;
        }
      }
    });
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
        String url = "http://" + server.getAddress() + ":" + server.getPort() + "/library/sections/" + section + "/onDeck";
        PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
        {
          @Override
          public void onSuccess(MediaContainer mc)
          {
            showSectionsSearched++;
            for (int j = 0; j < mc.videos.size(); j++)
            {
              PlexVideo video = mc.videos.get(j);
              if(compareTitle(video.getGrandparentTitle(), queryTerm)) {
                video.setServer(server);
                video.setThumb(video.getGrandparentThumb());
                video.setShowTitle(video.getGrandparentTitle());
                videos.add(video);
                Logger.d("ADDING " + video.getGrandparentTitle());
              }
            }

            if (server.getTvSections().size() == showSectionsSearched)
            {
              serversSearched++;
              if (serversSearched == plexmediaServers.size())
              {
                onFinishedNextEpisodeSearch(queryTerm);
              }
            }
          }
        });
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
      if(videos.size() == 1)
  			playVideo(videos.get(0));
      else {
        // We found more than one matching show. Let's check if the title of any of the matching shows
        // exactly equals the query term, otherwise tell the user to be more specific.
        //
        int exactMatch = -1;
        for(int i=0;i<videos.size();i++) {
          if(videos.get(i).getGrandparentTitle().toLowerCase().equals(queryTerm.toLowerCase())) {
            exactMatch = i;
            break;
          }
        }

        if(exactMatch > -1) {
          playVideo(videos.get(exactMatch));
        } else {
          feedback("I found more than one matching show. Please be more specific.");
          searchDialog.dismiss();
          finish();
          return;
        }
      }
		}
	}
	
	private void doMovieSearch(final String queryTerm) {
    Logger.d("Doing movie search. %d servers", plexmediaServers.size());
		movieSectionsSearched = 0;
		serversSearched = 0;
		for(final PlexServer server : this.plexmediaServers.values()) {
			Logger.d("Searching server: %s, %d sections", server.getMachineIdentifier(), server.getMovieSections().size());
      if(server.getMovieSections().size() == 0) {
        serversSearched++;
        if(serversSearched == plexmediaServers.size()) {
          onMovieSearchFinished(queryTerm);
        }
      }
			for(int i=0;i<server.getMovieSections().size();i++) {
				String section = server.getMovieSections().get(i);
        String url = "http://" + server.getAddress() + ":" + server.getPort() + "/library/sections/" + section + "/search?type=1&query=" + queryTerm;
        PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
        {
          @Override
          public void onSuccess(MediaContainer mc)
          {
            movieSectionsSearched++;
            for(int j=0;j<mc.videos.size();j++) {
              PlexVideo video = mc.videos.get(j);
              if(compareTitle(video.getTitle().toLowerCase(), queryTerm.toLowerCase())) {
                video.setServer(server);
                video.setShowTitle(mc.grandparentTitle);
                videos.add(video);
              }
            }
            Logger.d("Videos: %d", mc.videos.size());
            Logger.d("sections searched: %d", movieSectionsSearched);
            if(server.getMovieSections().size() == movieSectionsSearched) {
              serversSearched++;
              if(serversSearched == plexmediaServers.size()) {
                onMovieSearchFinished(queryTerm);
              }
            }
          }
        });
			}
			
		}
	}
	
	private void onMovieSearchFinished(String queryTerm) {
		Logger.d("Done searching! Have videos: %d", videos.size());

		if(videos.size() == 1) {
			Logger.d("Chosen video: %s", videos.get(0).getTitle());
			feedback("Now watching " + videos.get(0).getTitle() + " on " + client.getName());
			
			playVideo(videos.get(0));
		} else if(videos.size() > 1) {
      // We found more than one match, but let's see if any of them are an exact match
      Boolean exactMatch = false;
      for(int i=0;i<videos.size();i++) {
        if(videos.get(i).getTitle().toLowerCase().equals(queryTerm.toLowerCase())) {
          exactMatch = true;
          playVideo(videos.get(i));
          feedback("Now watching " + videos.get(i).getTitle() + " on " + client.getName());
          break;
        }
      }
      if(!exactMatch) {
        feedback("I found more than one matching movie. Please be more specific.");
        searchDialog.dismiss();
        finish();
        return;
      }
    } else {
			Logger.d("Didn't find a video");
			feedback("Sorry, I couldn't find a video to play.");
			searchDialog.dismiss();
			finish();
			return;
		}
	}
	
	private void feedback(String text) {
//		if(mPrefs.getInt("feedback", MainActivity.FEEDBACK_VOICE) == MainActivity.FEEDBACK_VOICE) {
//			GoogleSearchApi.speak(this, text);
//		} else {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
//		}
		Logger.d(text);
	}

  private void playAlbum(final PlexDirectory album) {
    String url = "http://" + album.server.getAddress() + ":" + album.server.getPort() + album.key;
    PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
    {
      @Override
      public void onSuccess(MediaContainer mc)
      {
        if(mc.tracks.size() > 0) {
          PlexTrack track = mc.tracks.get(0);
          track.setServer(album.server);
          track.setThumb(album.thumb);
          track.setArtist(album.parentTitle);
          track.setAlbum(album.title);
          playTrack(track, album);
        } else {
          Logger.d("Didn't find a video");
          feedback("Sorry, I couldn't find an album to play.");
          searchDialog.dismiss();
          finish();
          return;
        }
      }
    });
  }

  private void playTrack(final PlexTrack track) {
    playTrack(track, null);
  }

	private void playTrack(final PlexTrack track, final PlexDirectory album) {
    String url = "http://" + client.getHost() + ":" + client.getPort() + "/player/playback/playMedia?machineIdentifier=" + track.getServer().getMachineIdentifier() + "&key=" + track.getKey();
    if(album != null)
      url += "&containerKey=" + album.key;
    if(mPrefs.getBoolean("resume", false) || resumePlayback) {
      url += "&viewOffset=" + track.getViewOffset();
    }
    PlexHttpClient.get(url, null, new PlexHttpResponseHandler()
    {
      @Override
      public void onSuccess(PlexResponse r)
      {
      searchDialog.dismiss();
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

        PlexHttpClient.setThumb(track, (ImageView)findViewById(R.id.nowPlayingImage));


      }
      }
    });
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

      PlexHttpClient.get(url, null, new PlexHttpResponseHandler()
      {
        @Override
        public void onSuccess(PlexResponse r)
        {
          searchDialog.dismiss();
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

            PlexHttpClient.setThumb(video, (ScrollView)findViewById(R.id.background));
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
//    IntentFilter filters = new IntentFilter();
//    filters.addAction(GDMService.MSG_RECEIVED);
//    filters.addAction(GDMService.SOCKET_CLOSED);
//    LocalBroadcastManager.getInstance(this).registerReceiver(gdmReceiver, filters);
  }

  private static Boolean compareTitle(String title, String queryTerm) {
    // First, check if the two terms are equal
    if(title.toLowerCase().equals(queryTerm.toLowerCase()))
      return true;

    // No equal match, so split the query term up by words, and see if the title contains every single word
    String[] words = queryTerm.split(" ");
    Boolean missing = false;
    for(int i=0;i<words.length;i++) {
      if(!title.toLowerCase().matches(".*\\b" + words[i].toLowerCase() + "\\b.*"))
        missing = true;
    }
    return !missing;
  }
}
