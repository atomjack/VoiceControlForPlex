package com.atomjack.vcfp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import us.nineworlds.serenity.GDMReceiver;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
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

public class PlayMediaActivity extends Activity implements TextToSpeech.OnInitListener {
	public final static String PREFS = MainActivity.PREFS;
	private SharedPreferences mPrefs;
	private String queryText;
	private Dialog searchDialog = null;
	private ConcurrentHashMap<String, PlexServer> plexmediaServers = new ConcurrentHashMap<String, PlexServer>();
	private int serversScanned = 0;
	private BroadcastReceiver gdmReceiver = new GDMReceiver();
	private Intent mServiceIntent;
	private List<PlexClient> clients;
	private PlexClient client = null;
	private int serversSearched = 0;
	private List<PlexVideo> videos = new ArrayList<PlexVideo>();
	private Boolean videoPlayed = false;
	private List<PlexDirectory> shows = new ArrayList<PlexDirectory>();
	private Boolean resumePlayback = false;
	private List<PlexTrack> tracks = new ArrayList<PlexTrack>();
	private List<PlexDirectory> albums = new ArrayList<PlexDirectory>();
	private TextToSpeech feedbackTts = null;
	private TextToSpeech errorsTts = null;
	private String ttsDelayedFeedback = null;
	private boolean ttsDelayedFeedbackIsError = false;
	private PlexVideo playingVideo; // The video currently playing
	private PlexTrack playingTrack; // The track currently playing

	protected void onCreate(Bundle savedInstanceState) {
		Logger.d("on create PlayMediaActivity");
		super.onCreate(savedInstanceState);

    BugSenseHandler.initAndStartSession(PlayMediaActivity.this, MainActivity.BUGSENSE_APIKEY);

    mPrefs = getSharedPreferences(PREFS, MODE_PRIVATE);

		setContentView(R.layout.play_media);
		if(savedInstanceState == null) {
			playingVideo = null;
			playingTrack = null;
			client = null;
			Intent intent = getIntent();


			if(getIntent().getExtras().getStringArrayList(RecognizerIntent.EXTRA_RESULTS) != null)
			{
				ArrayList<String> voiceResults = getIntent().getExtras().getStringArrayList(RecognizerIntent.EXTRA_RESULTS);
				if(voiceResults.size() > 0) {
					queryText = voiceResults.get(0);
				} else {
					errors(getResources().getString(R.string.nothing_to_play));
					finish();
				}
			} else
				queryText = intent.getStringExtra("queryText");
			startup();
		} else {
			Logger.d("found saved instance state");
			playingVideo = savedInstanceState.getParcelable("video");
			playingTrack = savedInstanceState.getParcelable("track");
			client = savedInstanceState.getParcelable("client");
			if(playingVideo != null)
				showPlayingVideo();
			else if(playingTrack != null)
				showPlayingTrack();
		}
	}

	@Override
	public void onNewIntent(Intent intent) {
		Logger.d("ON NEW INTENT IN PLAYMEDIACTIVITY");
		String origin = intent.getStringExtra("ORIGIN");
		String from = intent.getStringExtra("FROM");
		Logger.d("origin: %s", origin);
		Logger.d("from: %s", from);
		if(from == null) {
			if(origin.equals("GoogleSearchReceiver") || origin.equals("Tasker") || origin.equals("vcpf")) {
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
			this.plexmediaServers.put(defaultServer.name, defaultServer);
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
		if(item.getItemId() == R.id.menu_about) {
			return showAbout();
		} else if(item.getItemId() == R.id.menu_donate) {
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
				Logger.d("ip: %s", server.address);
				Logger.d("port: %s", server.port);
        String url = "http://" + server.address + ":" + server.port + "/clients";
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

					@Override
					public void onFailure(Throwable error) {
						searchDialog.dismiss();
						errors(getResources().getString(R.string.got_error), error.getMessage());
						finish();
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
		Pattern p = Pattern.compile( "on (.*)$", Pattern.DOTALL);
		Matcher matcher = p.matcher(queryText);
		Gson gson = new Gson();
		this.client = null;
		if(matcher.find()) {
			specifiedClient = matcher.group(1).toLowerCase();

			Logger.d("Clients: %d", clients.size());
			Logger.d("query text now %s", queryText);
			for(int i=0;i<clients.size();i++) {
				if(clients.get(i).name.toLowerCase().equals(specifiedClient)) {
					this.client = clients.get(i);
					Logger.d("Specified client %s", this.client.name);
					queryText = queryText.replaceAll(" on (.*)$", "");
					break;
				}
			}
		}
		if(this.client == null) {
			this.client = gson.fromJson(mPrefs.getString("Client", ""), PlexClient.class);
		}

		if(this.client == null) {
			// No client set in options, and either none specified in the query or I just couldn't find it.
			errors(getResources().getString(R.string.client_not_found));
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
		if(mediaType.equals("")) {
			p = Pattern.compile("pause playback", Pattern.DOTALL);
			matcher = p.matcher(queryText);
			if (matcher.find()) {
				Logger.d("pausing playback");
				pausePlayback();
				return;
			}
			p = Pattern.compile("resume playback", Pattern.DOTALL);
			matcher = p.matcher(queryText);
			if (matcher.find()) {
				Logger.d("resuming playback");
				resumePlayback();
				return;
			}
			p = Pattern.compile("stop playback", Pattern.DOTALL);
			matcher = p.matcher(queryText);
			if (matcher.find()) {
				Logger.d("stopping playback");
				stopPlayback();
				return;
			}
			p = Pattern.compile("(offset|timecode) ([0-9]+|two) (hours?|minutes?|seconds?)(?: ([0-9]+|two) (minutes?|seconds?))?(?: ([0-9]+|two) (seconds?))?", Pattern.DOTALL);
			matcher = p.matcher(queryText);
			if (matcher.find()) {
				String groupOne = matcher.group(2) != null && matcher.group(2).equals("two") ? "2" : matcher.group(2);
				String groupThree = matcher.group(4) != null && matcher.group(4).equals("two") ? "2" : matcher.group(4);
				String groupFive = matcher.group(6) != null && matcher.group(6).equals("two") ? "2" : matcher.group(6);
				int hours = 0, minutes = 0, seconds = 0;
				if(matcher.group(5) != null && matcher.group(5).startsWith("minute"))
					minutes = Integer.parseInt(groupThree);
				else if(matcher.group(3) != null && matcher.group(3).startsWith("minute"))
					minutes = Integer.parseInt(groupOne);

				if(matcher.group(6) != null && matcher.group(6).startsWith("second"))
					seconds = Integer.parseInt(groupFive);
				else if(matcher.group(5) != null && matcher.group(5).startsWith("second"))
					seconds = Integer.parseInt(groupThree);
				else if(matcher.group(3).startsWith("second"))
					seconds = Integer.parseInt(groupOne);

				if(matcher.group(3).startsWith("hour"))
					hours = Integer.parseInt(groupOne);

				seekTo(hours, minutes, seconds);
				return;
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
					errors(getResources().getString(R.string.nothing_to_play));
          finish();
          return;
        }
		} else {
			searchDialog.dismiss();
			errors(getResources().getString(R.string.nothing_to_play));
			finish();
			return;
		}
	}

	private void adjustPlayback(String which, final String onFinish) {
		ArrayList<String> validModes = new ArrayList<String>(Arrays.asList("pause", "play", "stop"));
		if(validModes.indexOf(which) == -1)
			return;
		try {
			Logger.d("Host: %s", client.host);
			Logger.d("Port: %s", client.port);
			String url = "http://" + client.host + ":" + client.port + "/player/playback/" + which;

			PlexHttpClient.get(url, null, new PlexHttpResponseHandler()
			{
				@Override
				public void onSuccess(PlexResponse r)
				{
					searchDialog.dismiss();
					Boolean passed = true;
					if(r.code != null) {
						if(!r.code.equals("200")) {
							passed = false;
						}
					}
					Logger.d("Playback response: %s", r.code);
					if(passed) {
						feedback(onFinish);
						finish();
					} else {
						searchDialog.dismiss();
						errors(getResources().getString(R.string.http_status_code_error), r.code);
						finish();
					}
				}

				@Override
				public void onFailure(Throwable error) {
					searchDialog.dismiss();
					errors(getResources().getString(R.string.got_error), error.getMessage());
					finish();
				}
			});
		} catch (Exception e) {
			Logger.e("Exception trying to play video: %s", e.toString());
			e.printStackTrace();
		}
	}

	private void pausePlayback() {
		adjustPlayback("pause", getResources().getString(R.string.playback_paused));
	}

	private void resumePlayback() {
		adjustPlayback("play", getResources().getString(R.string.playback_resumed));
	}

	private void stopPlayback() {
		adjustPlayback("stop", getResources().getString(R.string.playback_stopped));
	}

	private void seekTo(int hours, int minutes, int seconds) {
		Logger.d("Seeking to %d hours, %d minutes, %d seconds", hours, minutes, seconds);
		int offset = 1000*((hours*60*60)+(minutes*60)+seconds);
		Logger.d("offset: %d milliseconds", offset);

		try {
			Logger.d("Host: %s", client.host);
			Logger.d("Port: %s", client.port);
			String url = "http://" + client.host + ":" + client.port + "/player/playback/seekTo?offset=" + offset;

			PlexHttpClient.get(url, null, new PlexHttpResponseHandler()
			{
				@Override
				public void onSuccess(PlexResponse r)
				{
					searchDialog.dismiss();
					Boolean passed = true;
					if(r.code != null) {
						if(!r.code.equals("200")) {
							passed = false;
						}
					}
					Logger.d("Playback response: %s", r.code);
					if(passed) {
						finish();
					} else {
						searchDialog.dismiss();
						errors(getResources().getString(R.string.http_status_code_error), r.code);
						finish();
					}
				}

				@Override
				public void onFailure(Throwable error) {
					searchDialog.dismiss();
					errors(getResources().getString(R.string.got_error), error.getMessage());
					finish();
				}
			});
		} catch (Exception e) {
			Logger.e("Exception trying to play video: %s", e.toString());
			e.printStackTrace();
		}
	}

  private void searchForAlbum(final String artist, final String album) {
    Logger.d("Searching for album %s by %s.", album, artist);
    serversSearched = 0;
    Logger.d("Servers: %d", this.plexmediaServers.size());
    for(final PlexServer server : this.plexmediaServers.values()) {
      server.musicSectionsSearched = 0;
      if(server.musicSections.size() == 0) {
        serversSearched++;
        if(serversSearched == plexmediaServers.size()) {
          if(albums.size() == 1) {
            playAlbum(albums.get(0));
          } else {
						errors(getResources().getString(albums.size() > 1 ? R.string.found_more_than_one_album : R.string.couldnt_find_album));
            searchDialog.dismiss();
            finish();
            return;
          }
        }
      }
      for(int i=0;i<server.musicSections.size();i++) {
        String section = server.musicSections.get(i);
        String url = "http://" + server.address + ":" + server.port + "/library/sections/" + section + "/search?type=9&query=" + album;
        PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
        {
          @Override
          public void onSuccess(MediaContainer mc)
          {
            server.musicSectionsSearched++;
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

            if(server.musicSections.size() == server.musicSectionsSearched) {
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
										errors(getResources().getString(albums.size() > 1 ? R.string.found_more_than_one_album : R.string.couldnt_find_album));
                    searchDialog.dismiss();
                    finish();
                    return;
                  }
                }
              }

            }
          }

					@Override
					public void onFailure(Throwable error) {
						searchDialog.dismiss();
						errors(getResources().getString(R.string.got_error), error.getMessage());
						finish();
					}
        });
      }
    }
  }

	private void searchForSong(final String artist, final String track) {
		serversSearched = 0;
		Logger.d("Servers: %d", this.plexmediaServers.size());
		for(final PlexServer server : this.plexmediaServers.values()) {
      server.musicSectionsSearched = 0;
			if(server.musicSections.size() == 0) {
				serversSearched++;
				if(serversSearched == plexmediaServers.size()) {
					if(tracks.size() > 0) {
            searchDialog.dismiss();
						playTrack(tracks.get(0));
					} else {
						errors(getResources().getString(R.string.couldnt_find_track));
						searchDialog.dismiss();
						finish();
						return;
					}
				}
			}
			for(int i=0;i<server.musicSections.size();i++) {
				String section = server.musicSections.get(i);
        String url = "http://" + server.address + ":" + server.port + "/library/sections/" + section + "/search?type=10&query=" + track;
        PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
        {
          @Override
          public void onSuccess(MediaContainer mc)
          {
            server.musicSectionsSearched++;
            for(int j=0;j<mc.tracks.size();j++) {
              PlexTrack thisTrack = mc.tracks.get(j);
              thisTrack.artist = thisTrack.grandparentTitle;
              thisTrack.album = thisTrack.parentTitle;
              Logger.d("Track: %s by %s.", thisTrack.title, thisTrack.artist);
              if(compareTitle(thisTrack.artist, artist)) {
                thisTrack.server = server;
                tracks.add(thisTrack);
              }
            }

            if(server.musicSections.size() == server.musicSectionsSearched) {
              serversSearched++;
              if(serversSearched == plexmediaServers.size()) {
                Logger.d("found music to play.");
                if(tracks.size() > 0) {
                  playTrack(tracks.get(0));
                } else {
                  Boolean exactMatch = false;
                  for(int k=0;k<albums.size();k++) {
                    if(tracks.get(k).artist.toLowerCase().equals(artist.toLowerCase())) {
                      exactMatch = true;
                      playTrack(tracks.get(k));
                    }
                  }
                  if(!exactMatch) {
										errors(getResources().getString(R.string.couldnt_find_track));
                    searchDialog.dismiss();
                    finish();
                    return;
                  }
                }
              }
            }
          }

					@Override
					public void onFailure(Throwable error) {
						searchDialog.dismiss();
						errors(getResources().getString(R.string.got_error), error.getMessage());
						finish();
					}
        });
			}
		}
	}

	private void doShowSearch(String episodeSpecified, final String showSpecified) {
		serversSearched = 0;
		for(final PlexServer server : this.plexmediaServers.values()) {
      server.showSectionsSearched = 0;
      if(server.tvSections.size() == 0) {
        serversSearched++;
        if(serversSearched == plexmediaServers.size()) {
          playSpecificEpisode(showSpecified);
        }
      }
			for(int i=0;i<server.tvSections.size();i++) {
				String section = server.tvSections.get(i);
        String url = "http://" + server.address + ":" + server.port + "/library/sections/" + section + "/search?type=4&query=" + episodeSpecified;
        PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
        {
          @Override
          public void onSuccess(MediaContainer mc)
          {
            server.showSectionsSearched++;
            for(int j=0;j<mc.videos.size();j++) {
              Logger.d("Show: %s", mc.videos.get(j).grandparentTitle);
              PlexVideo video = mc.videos.get(j);
              if(compareTitle(video.grandparentTitle, showSpecified)) {
                video.server = server;
                video.thumb = video.grandparentThumb;
                video.showTitle = video.grandparentTitle;
                Logger.d("Adding %s - %s.", video.showTitle, video.title);
                videos.add(video);
              }
            }

            if(server.tvSections.size() == server.showSectionsSearched) {
              serversSearched++;
              if(serversSearched == plexmediaServers.size()) {
                playSpecificEpisode(showSpecified);
              }
            }
          }

					@Override
					public void onFailure(Throwable error) {
						searchDialog.dismiss();
						errors(getResources().getString(R.string.got_error), error.getMessage());
						finish();
					}
        });
			}
		}
	}

	private void playSpecificEpisode(String showSpecified) {
		if(videos.size() == 0) {
			errors(getResources().getString(R.string.couldnt_find_episode));
			searchDialog.dismiss();
			finish();
			return;
		} else if(videos.size() == 1) {
			playVideo(videos.get(0));
		} else {
      Boolean exactMatch = false;
      for(int i=0;i<videos.size();i++) {
        if(videos.get(i).grandparentTitle.toLowerCase().equals(showSpecified.toLowerCase())) {
          exactMatch = true;
          playVideo(videos.get(i));
          break;
        }
      }
      if(!exactMatch) {
				errors(getResources().getString(R.string.found_more_than_one_show));
        searchDialog.dismiss();
        finish();
        return;
      }
		}
	}

	private void doShowSearch(final String queryTerm, final String season, final String episode) {
		Logger.d("doShowSearch: %s s%s e%s", queryTerm, season, episode);
		serversSearched = 0;
		for(final PlexServer server : this.plexmediaServers.values()) {
      server.showSectionsSearched = 0;
      Logger.d("Searching server %s", server.name);
      if(server.tvSections.size() == 0) {
          Logger.d("%s has no tv sections", server.name);
          serversSearched++;
          if(serversSearched == plexmediaServers.size()) {
              doEpisodeSearch(queryTerm, season, episode);
          }
      }
			for(int i=0;i<server.tvSections.size();i++) {
				String section = server.tvSections.get(i);
        String url = "http://" + server.address + ":" + server.port + "/library/sections/" + section + "/search?type=2&query=" + queryTerm;
        PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
        {
          @Override
          public void onSuccess(MediaContainer mc)
          {
            server.showSectionsSearched++;
            for(int j=0;j<mc.directories.size();j++) {
              shows.add(mc.directories.get(j));
            }

            if(server.tvSections.size() == server.showSectionsSearched) {
              serversSearched++;
              if(serversSearched == plexmediaServers.size()) {
                doEpisodeSearch(queryTerm, season, episode);
              }
            }
          }

					@Override
					public void onFailure(Throwable error) {
						searchDialog.dismiss();
						errors(getResources().getString(R.string.got_error), error.getMessage());
						finish();
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
				errors(getResources().getString(R.string.couldnt_find), queryTerm);
				searchDialog.dismiss();
				finish();
				return;
			} else if(shows.size() == 1) {
				final PlexDirectory show = shows.get(0);
				Logger.d("Show key: %s", show.key);
        String url = "http://" + server.address + ":" + server.port + "" + show.key;
        PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
        {
          @Override
          public void onSuccess(MediaContainer mc)
          {
            PlexDirectory foundSeason = null;
            for(int i=0;i<mc.directories.size();i++) {
              PlexDirectory directory = mc.directories.get(i);
              if(directory.title.equals("Season " + season)) {
                Logger.d("Found season %s: %s.", season, directory.key);
                foundSeason = directory;
                break;
              }
            }

            if(foundSeason == null && serversSearched == plexmediaServers.size() && !videoPlayed) {
              serversSearched++;
							errors(getResources().getString(R.string.couldnt_find_season));
              searchDialog.dismiss();
              finish();
              return;
            } else if(foundSeason != null) {
              String url = "http://" + server.address + ":" + server.port + "" + foundSeason.key;
              PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
              {
                @Override
                public void onSuccess(MediaContainer mc)
                {
                  Boolean foundEpisode = false;
                  for(int i=0;i<mc.videos.size();i++) {
                    Logger.d("Looking at episode %s", mc.videos.get(i).index);
                    if(mc.videos.get(i).index.equals(episode) && !videoPlayed) {
                      serversSearched++;
                      PlexVideo video = mc.videos.get(i);
                      video.server = server;
                      video.thumb = show.thumb;
                      video.showTitle = show.title;
                      playVideo(video);
                      foundEpisode = true;
                      break;
                    }
                  }
                  Logger.d("foundEpisode = %s", foundEpisode);
                  if(foundEpisode == false && serversSearched == plexmediaServers.size() && !videoPlayed) {
                    serversSearched++;
										errors(getResources().getString(R.string.couldnt_find_episode));
                    searchDialog.dismiss();
                    finish();
                    return;
                  }
                }

								@Override
								public void onFailure(Throwable error) {
									searchDialog.dismiss();
									errors(getResources().getString(R.string.got_error), error.getMessage());
									finish();
								}
              });
            }
          }

					@Override
					public void onFailure(Throwable error) {
						searchDialog.dismiss();
						errors(getResources().getString(R.string.got_error), error.getMessage());
						finish();
					}
        });
			}
		}
	}

  private void doLatestEpisodeSearch(final String queryTerm) {
    Logger.d("doLatestEpisodeSearch: %s", queryTerm);
    serversSearched = 0;
    for(final PlexServer server : this.plexmediaServers.values()) {
      server.showSectionsSearched = 0;
      Logger.d("Searching server %s", server.name);
      if(server.tvSections.size() == 0) {
        Logger.d(server.name + " has no tv sections");
        serversSearched++;
        if(serversSearched == plexmediaServers.size()) {
          doLatestEpisode(queryTerm);
        }
      }
      for(int i=0;i<server.tvSections.size();i++) {
        String section = server.tvSections.get(i);
        String url = "http://" + server.address + ":" + server.port + "/library/sections/" + section + "/search?type=2&query=" + queryTerm;
        PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
        {
          @Override
          public void onSuccess(MediaContainer mc)
          {
            server.showSectionsSearched++;
            for(int j=0;j<mc.directories.size();j++) {
              PlexDirectory show = mc.directories.get(j);
              if(compareTitle(show.title, queryTerm)) {
                show.server = server;
                shows.add(show);
                Logger.d("Adding %s", show.title);
              }
            }

            if(server.tvSections.size() == server.showSectionsSearched) {
              serversSearched++;
              if(serversSearched == plexmediaServers.size()) {
                doLatestEpisode(queryTerm);
              }
            }
          }

					@Override
					public void onFailure(Throwable error) {
						searchDialog.dismiss();
						errors(getResources().getString(R.string.got_error), error.getMessage());
						finish();
					}
        });
      }
    }
  }

  private void doLatestEpisode(final String queryTerm) {
    if(shows.size() == 0) {
			errors(getResources().getString(R.string.couldnt_find), queryTerm);
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
			errors(getResources().getString(R.string.found_more_than_one_show));
      searchDialog.dismiss();
      finish();
      return;
    }
    final PlexDirectory show = chosenShow;
    String url = "http://" + show.server.address + ":" + show.server.port + "/library/metadata/" + show.ratingKey + "/allLeaves";
    PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
    {
      @Override
      public void onSuccess(MediaContainer mc)
      {
        PlexVideo latestVideo = null;
        for(int j=0;j<mc.videos.size();j++) {
          PlexVideo video = mc.videos.get(j);
          if(latestVideo == null || latestVideo.airDate().before(video.airDate())) {
            video.showTitle = video.grandparentTitle;
            latestVideo = video;
          }
        }
        latestVideo.server = show.server;
        Logger.d("Found video: %s", latestVideo.airDate());
        if(latestVideo != null) {
          playVideo(latestVideo);
        } else {
					errors(getResources().getString(R.string.couldnt_find), queryTerm);
          searchDialog.dismiss();
          finish();
          return;
        }
      }

			@Override
			public void onFailure(Throwable error) {
				searchDialog.dismiss();
				errors(getResources().getString(R.string.got_error), error.getMessage());
				finish();
			}
    });
  }

	private void doNextEpisodeSearch(final String queryTerm) {
		serversSearched = 0;
		for(final PlexServer server : this.plexmediaServers.values()) {
      server.showSectionsSearched = 0;
      if(server.tvSections.size() == 0) {
        serversSearched++;
        if(serversSearched == plexmediaServers.size()) {
            onFinishedNextEpisodeSearch(queryTerm);
        }
      }
			for(int i=0;i<server.tvSections.size();i++) {
				String section = server.tvSections.get(i);
        String url = "http://" + server.address + ":" + server.port + "/library/sections/" + section + "/onDeck";
        PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
        {
          @Override
          public void onSuccess(MediaContainer mc)
          {
            server.showSectionsSearched++;
            for (int j = 0; j < mc.videos.size(); j++)
            {
              PlexVideo video = mc.videos.get(j);
              if(compareTitle(video.grandparentTitle, queryTerm)) {
                video.server = server;
                video.thumb = video.grandparentThumb;
                video.showTitle = video.grandparentTitle;
                videos.add(video);
                Logger.d("ADDING " + video.grandparentTitle);
              }
            }

            if (server.tvSections.size() == server.showSectionsSearched)
            {
              serversSearched++;
              if (serversSearched == plexmediaServers.size())
              {
                onFinishedNextEpisodeSearch(queryTerm);
              }
            }
          }

					@Override
					public void onFailure(Throwable error) {
						searchDialog.dismiss();
						errors(getResources().getString(R.string.got_error), error.getMessage());
						finish();
					}
        });
			}
		}
	}

	private void onFinishedNextEpisodeSearch(String queryTerm) {
		if(videos.size() == 0) {
			errors(getResources().getString(R.string.couldnt_find_next), queryTerm);
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
          if(videos.get(i).grandparentTitle.toLowerCase().equals(queryTerm.toLowerCase())) {
            exactMatch = i;
            break;
          }
        }

        if(exactMatch > -1) {
          playVideo(videos.get(exactMatch));
        } else {
					errors(getResources().getString(R.string.found_more_than_one_show));
          searchDialog.dismiss();
          finish();
          return;
        }
      }
		}
	}

	private void doMovieSearch(final String queryTerm) {
    Logger.d("Doing movie search. %d servers", plexmediaServers.size());
		serversSearched = 0;
		for(final PlexServer server : this.plexmediaServers.values()) {
      server.movieSectionsSearched = 0;
			Logger.d("Searching server: %s, %d sections", server.machineIdentifier, server.movieSections.size());
      if(server.movieSections.size() == 0) {
        serversSearched++;
        if(serversSearched == plexmediaServers.size()) {
          onMovieSearchFinished(queryTerm);
        }
      }
			for(int i=0;i<server.movieSections.size();i++) {
				String section = server.movieSections.get(i);
        String url = "http://" + server.address + ":" + server.port + "/library/sections/" + section + "/search?type=1&query=" + queryTerm;
        PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
        {
          @Override
          public void onSuccess(MediaContainer mc)
          {
            server.movieSectionsSearched++;
            for(int j=0;j<mc.videos.size();j++) {
              PlexVideo video = mc.videos.get(j);
              if(compareTitle(video.title.toLowerCase(), queryTerm.toLowerCase())) {
                video.server = server;
                video.showTitle = mc.grandparentTitle;
                videos.add(video);
              }
            }
            Logger.d("Videos: %d", mc.videos.size());
            Logger.d("sections searched: %d", server.movieSectionsSearched);
            if(server.movieSections.size() == server.movieSectionsSearched) {
              serversSearched++;
              if(serversSearched == plexmediaServers.size()) {
                onMovieSearchFinished(queryTerm);
              }
            }
          }

					@Override
					public void onFailure(Throwable error) {
						searchDialog.dismiss();
						errors(getResources().getString(R.string.got_error), error.getMessage());
						finish();
					}
        });
			}

		}
	}

	private void onMovieSearchFinished(String queryTerm) {
		Logger.d("Done searching! Have videos: %d", videos.size());

		if(videos.size() == 1) {
			Logger.d("Chosen video: %s", videos.get(0).title);

			playVideo(videos.get(0));
		} else if(videos.size() > 1) {
      // We found more than one match, but let's see if any of them are an exact match
      Boolean exactMatch = false;
      for(int i=0;i<videos.size();i++) {
        if(videos.get(i).title.toLowerCase().equals(queryTerm.toLowerCase())) {
          exactMatch = true;
          playVideo(videos.get(i));
          break;
        }
      }
      if(!exactMatch) {
				errors(getResources().getString(R.string.found_more_than_one_movie));
        searchDialog.dismiss();
        finish();
        return;
      }
    } else {
			Logger.d("Didn't find a video");
			errors(getResources().getString(R.string.couldnt_find), queryTerm);
			searchDialog.dismiss();
			finish();
			return;
		}
	}

	private void feedback(String text, Object... arguments) {
		text = String.format(text, arguments);
		feedback(text);
	}

	private void errors(String text, Object... arguments) {
		text = String.format(text, arguments);
		errors(text);
	}

	public void errors(String text) {
		feedback(text, true);
	}

	public void feedback(String text) {
		feedback(text, false);
	}

  public void feedback(String text, boolean errors) {
		if(mPrefs.getInt(errors ? "errors" : "feedback", 0) == MainActivity.FEEDBACK_VOICE) {
			TextToSpeech tts = errors ? errorsTts : feedbackTts;
			if(tts == null) {
				// Set up the TTS engine, and save the text that should be spoken so it can be done after the engine is initialized
				if(errors)
					errorsTts = new TextToSpeech(getApplicationContext(), this);
				else
					feedbackTts = new TextToSpeech(getApplicationContext(), this);
				ttsDelayedFeedback = text;
				ttsDelayedFeedbackIsError = errors;
				return;
			} else
				tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
		} else {
			Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
		}
    Logger.d(text);
  }

	@Override
	public void onInit(int status) {
		if(ttsDelayedFeedbackIsError)
			errorsTts.setLanguage(VoiceControlForPlexApplication.getVoiceLocale(mPrefs.getString(VoiceControlForPlexApplication.PREF_ERRORS_VOICE, "Locale.US")));
		else
			feedbackTts.setLanguage(VoiceControlForPlexApplication.getVoiceLocale(mPrefs.getString(VoiceControlForPlexApplication.PREF_FEEDBACK_VOICE, "Locale.US")));
		Logger.d("Set language: %s", mPrefs.getString(VoiceControlForPlexApplication.PREF_FEEDBACK_VOICE, "Locale.US"));
		if(ttsDelayedFeedback != null) {
			feedback(ttsDelayedFeedback, ttsDelayedFeedbackIsError);
			ttsDelayedFeedback = null;
			ttsDelayedFeedbackIsError = false;
		}
	}

  private void playAlbum(final PlexDirectory album) {
    String url = "http://" + album.server.address + ":" + album.server.port + album.key;
    PlexHttpClient.get(url, null, new PlexHttpMediaContainerHandler()
    {
      @Override
      public void onSuccess(MediaContainer mc)
      {
        if(mc.tracks.size() > 0) {
          PlexTrack track = mc.tracks.get(0);
          track.server = album.server;
          track.thumb = album.thumb;
          track.artist = album.parentTitle;
          track.album = album.title;
          playTrack(track, album);
        } else {
          Logger.d("Didn't find any tracks");
					errors(getResources().getString(R.string.couldnt_find_album));
          searchDialog.dismiss();
          finish();
          return;
        }
      }

			@Override
			public void onFailure(Throwable error) {
				searchDialog.dismiss();
				errors(getResources().getString(R.string.got_error), error.getMessage());
				finish();
			}
    });
  }

  private void playTrack(final PlexTrack track) {
    playTrack(track, null);
  }

	private void playTrack(final PlexTrack track, final PlexDirectory album) {
    String url = "http://" + client.host + ":" + client.port + "/player/playback/playMedia?machineIdentifier=" + track.server.machineIdentifier + "&key=" + track.key;
    if(album != null)
      url += "&containerKey=" + album.key;
    if(mPrefs.getBoolean("resume", false) || resumePlayback) {
      url += "&viewOffset=" + track.viewOffset;
    }
    PlexHttpClient.get(url, null, new PlexHttpResponseHandler()
    {
      @Override
      public void onSuccess(PlexResponse r)
      {
				searchDialog.dismiss();
				Boolean passed = true;
				if(r.code != null) {
					if(!r.code.equals("200")) {
						passed = false;
					}
				}
				Logger.d("Playback response: %s", r.code);
				if(passed) {
					playingTrack = track;
					showPlayingTrack();
				} else {
					searchDialog.dismiss();
					errors(getResources().getString(R.string.http_status_code_error), r.code);
					finish();
				}
      }

			@Override
			public void onFailure(Throwable error) {
				searchDialog.dismiss();
				errors(getResources().getString(R.string.got_error), error.getMessage());
				finish();
			}
    });
	}

	private void showPlayingTrack() {
		if(playingTrack == null)
			return;
		setContentView(R.layout.now_playing_music);

		TextView artist = (TextView)findViewById(R.id.nowPlayingArtist);
		artist.setText(playingTrack.artist);
		TextView album = (TextView)findViewById(R.id.nowPlayingAlbum);
		album.setText(playingTrack.album);
		TextView title = (TextView)findViewById(R.id.nowPlayingTitle);
		title.setText(playingTrack.title);

		TextView nowPlayingOnClient = (TextView)findViewById(R.id.nowPlayingOnClient);
		nowPlayingOnClient.setText(getResources().getString(R.string.now_playing_on) + " " + client.name);

		PlexHttpClient.setThumb(playingTrack, (ImageView)findViewById(R.id.nowPlayingImage));
	}

	private void playVideo(final PlexVideo video) {
		Logger.d("Playing video: %s", video.title);
		try {
			Logger.d("Host: %s", client.host);
			Logger.d("Port: %s", client.port);
			Logger.d("key: %s", video.key);
			Logger.d("Machine ID: %s", video.server.machineIdentifier);
      String url = "http://" + client.host + ":" + client.port + "/player/playback/playMedia?machineIdentifier=" + video.server.machineIdentifier + "&key=" + video.key;
      if(mPrefs.getBoolean("resume", false) || resumePlayback) {
        url += "&viewOffset=" + video.viewOffset;
      }

      PlexHttpClient.get(url, null, new PlexHttpResponseHandler()
      {
        @Override
        public void onSuccess(PlexResponse r)
        {
          searchDialog.dismiss();
					feedback(getResources().getString(R.string.now_watching_video), video.type.equals("movie") ? video.title : video.showTitle, client.name);
          Boolean passed = true;
          if(r.code != null) {
            if(!r.code.equals("200")) {
              passed = false;
            }
          }
          Logger.d("Playback response: %s", r.code);
          if(passed) {
						playingVideo = video;
            videoPlayed = true;
            showPlayingVideo();
          } else {
						searchDialog.dismiss();
						errors(getResources().getString(R.string.http_status_code_error), r.code);
						finish();
					}
        }

				@Override
				public void onFailure(Throwable error) {
					searchDialog.dismiss();
					errors(getResources().getString(R.string.got_error), error.getMessage());
					finish();
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

	private void showPlayingVideo() {
		if(playingVideo == null)
			return;
		if(playingVideo.type.equals("movie")) {
			setContentView(R.layout.now_playing_movie);
			TextView title = (TextView)findViewById(R.id.nowPlayingTitle);
			title.setText(playingVideo.title);
			TextView genre = (TextView)findViewById(R.id.nowPlayingGenre);
			genre.setText(playingVideo.getGenres());
			TextView year = (TextView)findViewById(R.id.nowPlayingYear);
			year.setText(playingVideo.year);
			TextView duration = (TextView)findViewById(R.id.nowPlayingDuration);
			duration.setText(playingVideo.getDuration());
			TextView summary = (TextView)findViewById(R.id.nowPlayingSummary);
			summary.setText(playingVideo.summary);
		} else {
			setContentView(R.layout.now_playing_show);

			TextView showTitle = (TextView)findViewById(R.id.nowPlayingShowTitle);
			showTitle.setText(playingVideo.showTitle);
			TextView episodeTitle = (TextView)findViewById(R.id.nowPlayingEpisodeTitle);
			episodeTitle.setText(playingVideo.title);
			TextView year = (TextView)findViewById(R.id.nowPlayingYear);
			year.setText(playingVideo.year);
			TextView duration = (TextView)findViewById(R.id.nowPlayingDuration);
			duration.setText(playingVideo.getDuration());
			TextView summary = (TextView)findViewById(R.id.nowPlayingSummary);
			summary.setText(playingVideo.summary);
		}

		TextView nowPlayingOnClient = (TextView)findViewById(R.id.nowPlayingOnClient);
		nowPlayingOnClient.setText(getResources().getString(R.string.now_playing_on) + " " + client.name);

		PlexHttpClient.setThumb(playingVideo, (ScrollView)findViewById(R.id.background));
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

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putParcelable("video", playingVideo);
		outState.putParcelable("client", client);
		outState.putParcelable("track", playingTrack);
	}
}
