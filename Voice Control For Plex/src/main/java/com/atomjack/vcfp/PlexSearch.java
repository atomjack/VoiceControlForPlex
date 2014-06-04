package com.atomjack.vcfp;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.speech.RecognizerIntent;
import android.support.v4.content.LocalBroadcastManager;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import us.nineworlds.serenity.GDMReceiver;

public class PlexSearch extends Service {

	public final static String PREFS = MainActivity.PREFS;
	private SharedPreferences mPrefs;
	private String queryText;
	private Feedback feedback;
	private Gson gson = new Gson();

	private ConcurrentHashMap<String, PlexServer> plexmediaServers = new ConcurrentHashMap<String, PlexServer>();
	private int serversScanned = 0;
	private BroadcastReceiver gdmReceiver = new GDMReceiver();
	private Intent mServiceIntent;
	private List<PlexClient> clients;
	private PlexClient client = null;
	private PlexServer specifiedServer = null;
	private int serversSearched = 0;
	private List<PlexVideo> videos = new ArrayList<PlexVideo>();
	private Boolean videoPlayed = false;
	private List<PlexDirectory> shows = new ArrayList<PlexDirectory>();
	private Boolean resumePlayback = false;
	private List<PlexTrack> tracks = new ArrayList<PlexTrack>();
	private List<PlexDirectory> albums = new ArrayList<PlexDirectory>();

	// Callbacks for when we figure out what action the user wishes to take.
	private myRunnable actionToDo;
	private interface myRunnable {
		void run();
	}
	// An instance of this interface will be returned by handleVoiceSearch when no server discovery is needed (e.g. pause/resume/stop playback or offset)
	private interface stopRunnable extends myRunnable {}

	private interface AfterTransientTokenRequest {
		void success(String token);
		void failure();
	}
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Logger.d("PlexSearch: onStartCommand");

		BugSenseHandler.initAndStartSession(PlexSearch.this, MainActivity.BUGSENSE_APIKEY);

		videoPlayed = false;

		if(!VoiceControlForPlexApplication.isWifiConnected(this)) {
			feedback.e(getResources().getString(R.string.no_wifi_connection_message));
			return Service.START_NOT_STICKY;
		}

		if(intent.getAction() != null && intent.getAction().equals(VoiceControlForPlexApplication.Intent.GDMRECEIVE)) {
			// We just scanned for servers and are returning from that, so set the servers we found
			// and then figure out which client to play to
			Logger.d("Got back from scanning for servers.");
			videoPlayed = false;
			plexmediaServers = VoiceControlForPlexApplication.servers;
			setClient();
		} else {
			queryText = null;
			client = null;

			specifiedServer = gson.fromJson(intent.getStringExtra(VoiceControlForPlexApplication.Intent.EXTRA_SERVER), PlexServer.class);
			if(specifiedServer != null)
				Logger.d("specified server %s", specifiedServer);
			PlexClient thisClient = gson.fromJson(intent.getStringExtra(VoiceControlForPlexApplication.Intent.EXTRA_CLIENT), PlexClient.class);
			if(thisClient != null)
				client = thisClient;

			if (intent.getExtras().getStringArrayList(RecognizerIntent.EXTRA_RESULTS) != null) {
				Logger.d("internal query");
				// Received spoken query from the RecognizerIntent
				ArrayList<String> voiceResults = intent.getExtras().getStringArrayList(RecognizerIntent.EXTRA_RESULTS);
				for(String q : voiceResults) {
					Logger.d("q: %s", q);
					if(q.matches(getString(R.string.pattern_recognition))) {
						queryText = q;
						break;
					}
				}
				if(queryText == null)
					feedback.e(getResources().getString(R.string.didnt_understand_that));
			} else {
				// Received spoken query from Google Search API
				Logger.d("Google Search API query");
				queryText = intent.getStringExtra("queryText");
			}

			if(client == null)
				client = gson.fromJson(mPrefs.getString("Client", ""), PlexClient.class);

			if(client == null) {
				// No client set in options, and either none specified in the query or I just couldn't find it.
				feedback.e(getResources().getString(R.string.client_not_found));
				return Service.START_NOT_STICKY;
			}
			if (queryText != null)
				startup();
			else
				feedback.e(getResources().getString(R.string.didnt_understand_that));
		}
		return Service.START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		feedback.destroy();
		if(gdmReceiver != null)
			unregisterReceiver(gdmReceiver);
		super.onDestroy();
	}

	@Override
	public void onCreate() {
		Logger.d("PlexSearch onCreate");
		queryText = null;
		mPrefs = getSharedPreferences(PREFS, MODE_PRIVATE);
		feedback = new Feedback(mPrefs, this);
		if(gdmReceiver != null) {
			IntentFilter filters = new IntentFilter();
			filters.addAction(GDMService.MSG_RECEIVED);
			filters.addAction(GDMService.SOCKET_CLOSED);
			LocalBroadcastManager.getInstance(this).registerReceiver(gdmReceiver,
							filters);
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		Logger.d("PlexSearch: onBind");
		return null;
	}

	private void startup() {
		Logger.d("Starting up with query string: %s", queryText);
		tracks = new ArrayList<PlexTrack>();
		videos = new ArrayList<PlexVideo>();
		shows = new ArrayList<PlexDirectory>();

		Gson gson = new Gson();
		PlexServer defaultServer = gson.fromJson(mPrefs.getString("Server", ""), PlexServer.class);
		if(specifiedServer != null && client != null && !specifiedServer.name.equals(getResources().getString(R.string.scan_all))) {
			// got a specified server and client from a shortcut
			Logger.d("Got hardcoded server and client from shortcut");
			plexmediaServers = new ConcurrentHashMap<String, PlexServer>();
			plexmediaServers.put(specifiedServer.name, specifiedServer);
			setClient();
		} else if(specifiedServer == null && defaultServer != null && !defaultServer.name.equals(getResources().getString(R.string.scan_all))) {
			// Use the server specified in the main settings
			Logger.d("Using server and client specified in main settings");
			plexmediaServers = new ConcurrentHashMap<String, PlexServer>();
			plexmediaServers.put(defaultServer.name, defaultServer);
			setClient();
		} else {
			// Scan All was chosen
			Logger.d("Scan all was chosen");

			// First, see if what needs to be done actually needs to know about the server (i.e. pause/stop/resume playback of offset).
			// If it does, execute the action and return as we don't need to do anything else. However, also check to see if the user
			// has specified a client (using " on <client name>") - if this is the case, we will need to find that client via server
			// discovery
			myRunnable actionToDo = handleVoiceSearch(true);
			if(actionToDo instanceof stopRunnable && !queryText.matches(getString(R.string.pattern_on_client))) {
				actionToDo.run();
				return;
			}


			if(mServiceIntent == null) {
				mServiceIntent = new Intent(this, GDMService.class);
			}
			mServiceIntent.setAction(VoiceControlForPlexApplication.Intent.GDMRECEIVE);
			mServiceIntent.putExtra("class", PlexSearch.class);
			mServiceIntent.putExtra("ORIGIN", "PlexSearch");
			startService(mServiceIntent);
			feedback.m("Scanning for Plex Servers");
		}
	}

	private void setClient() {
		Pattern p = Pattern.compile(getString(R.string.pattern_on_client), Pattern.DOTALL);
		Matcher matcher = p.matcher(queryText);
		if(!matcher.find()) {
			// Client not specified, so use default
			Logger.d("Using default client since none specified in query: %s", client.name);
			actionToDo = handleVoiceSearch();
			actionToDo.run();
		} else {
			// Get available clients
			Logger.d("getting all available clients");
			serversScanned = 0;
			clients = new ArrayList<PlexClient>();
			for(PlexServer server : plexmediaServers.values()) {
				Logger.d("ip: %s", server.address);
				Logger.d("port: %s", server.port);

				PlexHttpClient.get(server, "/clients", new PlexHttpMediaContainerHandler() {
					@Override
					public void onSuccess(MediaContainer mc) {
						serversScanned++;
						Logger.d("Clients: %d", mc.clients.size());
						for (int i = 0; i < mc.clients.size(); i++) {
							clients.add(mc.clients.get(i));
						}
						if (serversScanned == plexmediaServers.size()) {
							handleVoiceSearch().run();
						}
					}

					@Override
					public void onFailure(Throwable error) {
						feedback.e(getResources().getString(R.string.got_error), error.getMessage());
					}
				});
			}
		}
	}

	private myRunnable handleVoiceSearch() {
		return handleVoiceSearch(false);
	}

	private myRunnable handleVoiceSearch(boolean noChange) {
		Logger.d("GOT QUERY: %s", queryText);

		resumePlayback = false;

		Pattern p;
		Matcher matcher;

		if(!noChange) {
			p = Pattern.compile(getString(R.string.pattern_on_client), Pattern.DOTALL);
			matcher = p.matcher(queryText);

			if (matcher.find()) {
				String specifiedClient = matcher.group(2).toLowerCase();

				Logger.d("Clients: %d", clients.size());
				Logger.d("Specified client: %s", specifiedClient);
				for (int i = 0; i < clients.size(); i++) {
					if (clients.get(i).name.toLowerCase().equals(specifiedClient)) {
						client = clients.get(i);
						queryText = queryText.replaceAll(getString(R.string.pattern_on_client), "$1");
						Logger.d("query text now %s", queryText);
						break;
					}
				}
			}

			// Check for a sentence starting with "resume watching"
			p = Pattern.compile(getString(R.string.pattern_resume_watching));
			matcher = p.matcher(queryText);
			if(matcher.find()) {
				resumePlayback = true;
				// Replace "resume watching" with just "watch" so the pattern matching below works
				queryText = matcher.replaceAll(getString(R.string.pattern_watch));
			}
		}

		p = Pattern.compile( getString(R.string.pattern_watch_movie), Pattern.DOTALL);
		matcher = p.matcher(queryText);
		if(matcher.find()) {
			final String queryTerm = matcher.group(1);
			return new myRunnable() {
				@Override
				public void run() {
					doMovieSearch(queryTerm);
				}
			};
		}

		p = Pattern.compile(getString(R.string.pattern_watch_season_episode_of_show));
		matcher = p.matcher(queryText);

		if(matcher.find()) {
			final String queryTerm = matcher.group(3);
			final String season = matcher.group(1);
			final String episode = matcher.group(2);
			return new myRunnable() {
				@Override
				public void run() {
					doShowSearch(queryTerm, season, episode);
				}
			};
		}

		p = Pattern.compile(getString(R.string.pattern_watch_show_season_episode));
		matcher = p.matcher(queryText);

		if(matcher.find()) {
			final String queryTerm = matcher.group(1);
			final String season = matcher.group(2);
			final String episode = matcher.group(3);
			return new myRunnable() {
				@Override
				public void run() {
					doShowSearch(queryTerm, season, episode);
				}
			};
		}

		p = Pattern.compile(getString(R.string.pattern_watch_episode_of_show));
		matcher = p.matcher(queryText);
		if(matcher.find()) {
			final String episodeSpecified = matcher.group(1);
			final String showSpecified = matcher.group(2);
			return new myRunnable() {
				@Override
				public void run() {
					doShowSearch(episodeSpecified, showSpecified);
				}
			};
		}


		p = Pattern.compile(getString(R.string.pattern_watch_next_episode_of_show));
		matcher = p.matcher(queryText);

		if(matcher.find()) {
			final String queryTerm = matcher.group(1);
			return new myRunnable() {
				@Override
				public void run() {
					doNextEpisodeSearch(queryTerm, false);
				}
			};
		}

		p = Pattern.compile(getString(R.string.pattern_watch_latest_episode_of_show));
		matcher = p.matcher(queryText);

		if(matcher.find()) {
			final String queryTerm = matcher.group(2);
			Logger.d("found latest: %s", queryTerm);
			return new myRunnable() {
				@Override
				public void run() {
					doLatestEpisodeSearch(queryTerm);
				}
			};
		}

		p = Pattern.compile(getString(R.string.pattern_watch_show_episode_named));
		matcher = p.matcher(queryText);
		if(matcher.find()) {
			final String episodeSpecified = matcher.group(2);
			final String showSpecified = matcher.group(1);
			return new myRunnable() {
				@Override
				public void run() {
						doShowSearch(episodeSpecified, showSpecified);
				}
			};
		}

		p = Pattern.compile(getString(R.string.pattern_watch2));
		matcher = p.matcher(queryText);

		if(matcher.find()) {
			final String queryTerm = matcher.group(1);
			return new myRunnable() {
				@Override
				public void run() {
					doMovieSearch(queryTerm);
				}
			};
		}

		p = Pattern.compile(getString(R.string.pattern_listen_to_album_by_artist));
		matcher = p.matcher(queryText);
		if(matcher.find()) {
			final String album = matcher.group(1);
			final String artist = matcher.group(2);
			return new myRunnable() {
				@Override
				public void run() {
					searchForAlbum(artist, album);
				}
			};
		}

		p = Pattern.compile(getString(R.string.pattern_listen_to_album));
		matcher = p.matcher(queryText);
		if(matcher.find()) {
			final String album = matcher.group(1);
			return new myRunnable() {
				@Override
				public void run() {
					searchForAlbum("", album);
				}
			};
		}

		p = Pattern.compile(getString(R.string.pattern_listen_to_song_by_artist));
		matcher = p.matcher(queryText);
		if(matcher.find()) {
			final String track = matcher.group(1);
			final String artist = matcher.group(2);
			return new myRunnable() {
				@Override
				public void run() {
					searchForSong(artist, track);
				}
			};
		}

		p = Pattern.compile(getString(R.string.pattern_pause_playback), Pattern.DOTALL);
		matcher = p.matcher(queryText);
		if (matcher.find()) {
			return new stopRunnable() {
				@Override
				public void run() {
					pausePlayback();
				}
			};
		}

		p = Pattern.compile(getString(R.string.pattern_resume_playback), Pattern.DOTALL);
		matcher = p.matcher(queryText);
		if (matcher.find()) {
			Logger.d("resuming playback");
			return new stopRunnable() {
				@Override
				public void run() {
					resumePlayback();
				}
			};
		}

		p = Pattern.compile(getString(R.string.pattern_stop_playback), Pattern.DOTALL);
		matcher = p.matcher(queryText);
		if (matcher.find()) {
			Logger.d("stopping playback");
			return new stopRunnable() {
				@Override
				public void run() {
					stopPlayback();
				}
			};
		}

		p = Pattern.compile(getString(R.string.pattern_offset), Pattern.DOTALL);
		matcher = p.matcher(queryText);
		if (matcher.find()) {
			String groupOne = matcher.group(2) != null && matcher.group(2).matches("two|to") ? "2" : matcher.group(2);
			String groupThree = matcher.group(4) != null && matcher.group(4).matches("two|to") ? "2" : matcher.group(4);
			String groupFive = matcher.group(6) != null && matcher.group(6).matches("two|to") ? "2" : matcher.group(6);
			int hours = 0, minutes = 0, seconds = 0;
			if(matcher.group(5) != null && matcher.group(5).matches(getString(R.string.pattern_minutes)))
				minutes = Integer.parseInt(groupThree);
			else if(matcher.group(3) != null && matcher.group(3).matches(getString(R.string.pattern_minutes)))
				minutes = Integer.parseInt(groupOne);

			if(matcher.group(7) != null && matcher.group(7).matches(getString(R.string.pattern_seconds)))
				seconds = Integer.parseInt(groupFive);
			else if(matcher.group(5) != null && matcher.group(5).matches(getString(R.string.pattern_seconds)))
				seconds = Integer.parseInt(groupThree);
			else if(matcher.group(3).matches(getString(R.string.pattern_seconds)))
				seconds = Integer.parseInt(groupOne);

			if(matcher.group(3).matches(getString(R.string.pattern_hours)))
				hours = Integer.parseInt(groupOne);
			final int h = hours;
			final int m = minutes;
			final int s = seconds;
			return new stopRunnable() {
				@Override
				public void run() {
					seekTo(h, m, s);
				}
			};
		}

		return new myRunnable() {
			@Override
			public void run() {
				feedback.e(getString(R.string.didnt_understand), queryText);
			}
		};
	}

	private void adjustPlayback(String which, final String onFinish) {
		ArrayList<String> validModes = new ArrayList<String>(Arrays.asList("pause", "play", "stop"));
		if(validModes.indexOf(which) == -1)
			return;
		try {
			Logger.d("Host: %s", client.host);
			Logger.d("Port: %s", client.port);
			String url = String.format("http://%s:%s/player/playback/%s", client.host, client.port, which);
			PlexHttpClient.get(url, new PlexHttpResponseHandler()
			{
				@Override
				public void onSuccess(PlexResponse r)
				{
					Boolean passed = true;
					if(r.code != null) {
						if(!r.code.equals("200")) {
							passed = false;
						}
					}
					Logger.d("Playback response: %s", r.code);
					if(passed) {
						feedback.m(onFinish);
					} else {
						feedback.e(getResources().getString(R.string.http_status_code_error), r.code);
					}
				}

				@Override
				public void onFailure(Throwable error) {
					feedback.e(getResources().getString(R.string.got_error), error.getMessage());
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
		Intent stopIntent = new Intent(this, NowPlayingActivity.class);
		stopIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		stopIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		stopIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		stopIntent.putExtra("finish", true);
		startActivity(stopIntent);
	}

	private void seekTo(int hours, int minutes, int seconds) {
		Logger.d("Seeking to %d hours, %d minutes, %d seconds", hours, minutes, seconds);
		int offset = 1000*((hours*60*60)+(minutes*60)+seconds);
		Logger.d("offset: %d milliseconds", offset);

		try {
			Logger.d("Host: %s", client.host);
			Logger.d("Port: %s", client.port);
			String url = String.format("http://%s:%s/player/playback/seekTo?offset=%s", client.host, client.port, offset);
			PlexHttpClient.get(url, new PlexHttpResponseHandler()
			{
				@Override
				public void onSuccess(PlexResponse r)
				{
					Boolean passed = true;
					if(r.code != null) {
						if(!r.code.equals("200")) {
							passed = false;
						}
					}
					Logger.d("Playback response: %s", r.code);
					if(!passed) {
						feedback.e(getResources().getString(R.string.http_status_code_error), r.code);
					}
				}

				@Override
				public void onFailure(Throwable error) {
					feedback.e(getResources().getString(R.string.got_error), error.getMessage());
				}
			});
		} catch (Exception e) {
			Logger.e("Exception trying to play video: %s", e.toString());
			e.printStackTrace();
		}
	}

	private void doMovieSearch(final String queryTerm) {
		Logger.d("Doing movie search. %d servers", plexmediaServers.size());
		serversSearched = 0;
		for(final PlexServer server : plexmediaServers.values()) {
			server.movieSectionsSearched = 0;
			Logger.d("Searching server: %s, %d sections", server.name, server.movieSections.size());
			if(server.movieSections.size() == 0) {
				serversSearched++;
				if(serversSearched == plexmediaServers.size()) {
					onMovieSearchFinished(queryTerm);
				}
			}
			for(int i=0;i<server.movieSections.size();i++) {
				String section = server.movieSections.get(i);
				String path = String.format("/library/sections/%s/search?type=1&query=%s", section, queryTerm);
				PlexHttpClient.get(server, path, new PlexHttpMediaContainerHandler()
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
						feedback.e(getResources().getString(R.string.got_error), error.getMessage());
					}
				});
			}

		}
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

	private void onMovieSearchFinished(String queryTerm) {
		Logger.d("Done searching! Have videos: %d", videos.size());

		if(videos.size() == 1) {
			Logger.d("Chosen video: %s", videos.get(0).title);
			playVideo(videos.get(0));
		} else if(videos.size() > 1) {
			// We found more than one match, but let's see if any of them are an exact match
			Boolean exactMatch = false;
			for(int i=0;i<videos.size();i++) {
				Logger.d("Looking at video %s", videos.get(i).title);
				if(videos.get(i).title.toLowerCase().equals(queryTerm.toLowerCase())) {
					Logger.d("found exact match!");
					exactMatch = true;
					playVideo(videos.get(i));
					break;
				}
			}
			if(!exactMatch) {
				feedback.e(getResources().getString(R.string.found_more_than_one_movie));
				return;
			}
		} else {
			Logger.d("Didn't find a video");
			// Let's also support using this syntax to play the next episode in a tv show. Probably will want to use a different error message if nothing is found, though.
			doNextEpisodeSearch(queryTerm, true);
//			feedback.e(getResources().getString(R.string.couldnt_find), queryTerm);
//			return;
		}
	}

	private void playVideo(final PlexVideo video) {
		if(video.server.owned)
			playVideo(video, null);
		else {
			requestTransientAccessToken(video.server, new AfterTransientTokenRequest() {
				@Override
				public void success(String token) {
					playVideo(video, token);
				}

				@Override
				public void failure() {
					// Just try to play without a transient token
					playVideo(video, null);
				}
			});
		}
	}

	private void requestTransientAccessToken(PlexServer server, final AfterTransientTokenRequest onFinish) {
		String path = "/security/token?type=delegation&scope=all";
		PlexHttpClient.get(server, path, new PlexHttpMediaContainerHandler() {
			@Override
			public void onSuccess(MediaContainer mediaContainer) {
				onFinish.success(mediaContainer.token);
			}

			@Override
			public void onFailure(Throwable error) {
				onFinish.failure();
			}
		});
	}

	private void playVideo(final PlexVideo video, String transientToken) {
		Logger.d("Playing video: %s", video.title);
		try {
			QueryString qs = new QueryString("machineIdentifier", video.server.machineIdentifier);
			qs.add("key", video.key);
			qs.add("port", video.server.port);
			qs.add("address", video.server.address);
			if(mPrefs.getBoolean("resume", false) || resumePlayback)
				qs.add("viewOffset", video.viewOffset);
			if(transientToken != null)
				qs.add("token", transientToken);
			if(video.server.accessToken != null)
				qs.add(MainActivity.PlexHeaders.XPlexToken, video.server.accessToken);

			String url = String.format("http://%s:%s/player/playback/playMedia?%s", client.host, client.port, qs);
			PlexHttpClient.get(url, new PlexHttpResponseHandler()
			{
				@Override
				public void onSuccess(PlexResponse r)
				{
					feedback.m(getResources().getString(R.string.now_watching_video), video.type.equals("movie") ? video.title : video.showTitle, client.name);
					Boolean passed = true;
					if(r.code != null) {
						if(!r.code.equals("200")) {
							passed = false;
						}
					}
					Logger.d("Playback response: %s", r.code);
					if(passed) {
						videoPlayed = true;
						showPlayingVideo(video);
					} else {
						feedback.e(getResources().getString(R.string.http_status_code_error), r.code);
					}
				}

				@Override
				public void onFailure(Throwable error) {
					feedback.e(getResources().getString(R.string.got_error), error.getMessage());
				}
			});
		} catch (Exception e) {
			Logger.e("Exception trying to play video: %s", e.toString());
			e.printStackTrace();
		}
	}

	private void showPlayingVideo(PlexVideo video) {
		Intent nowPlayingIntent = new Intent(this, NowPlayingActivity.class);
		nowPlayingIntent.putExtra("video", video);
		nowPlayingIntent.putExtra("client", client);
		nowPlayingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(nowPlayingIntent);
	}

	private void doNextEpisodeSearch(final String queryTerm, final boolean fallback) {
		serversSearched = 0;
		for(final PlexServer server : plexmediaServers.values()) {
			server.showSectionsSearched = 0;
			if(server.tvSections.size() == 0) {
				serversSearched++;
				if(serversSearched == plexmediaServers.size()) {
					onFinishedNextEpisodeSearch(queryTerm, fallback);
				}
			}
			for(int i=0;i<server.tvSections.size();i++) {
				String section = server.tvSections.get(i);
				String path = String.format("/library/sections/%s/onDeck", section);
				PlexHttpClient.get(server, path, new PlexHttpMediaContainerHandler()
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
								onFinishedNextEpisodeSearch(queryTerm, fallback);
							}
						}
					}

					@Override
					public void onFailure(Throwable error) {
						feedback.e(getResources().getString(R.string.got_error), error.getMessage());
					}
				});
			}
		}
	}

	private void onFinishedNextEpisodeSearch(String queryTerm, boolean fallback) {
		if(videos.size() == 0) {
			feedback.e(getResources().getString(fallback ? R.string.couldnt_find : R.string.couldnt_find_next), queryTerm);
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
					feedback.e(getResources().getString(R.string.found_more_than_one_show));
					return;
				}
			}
		}
	}

	private void doLatestEpisodeSearch(final String queryTerm) {
		Logger.d("doLatestEpisodeSearch: %s", queryTerm);
		serversSearched = 0;
		for(final PlexServer server : plexmediaServers.values()) {
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
				String path = String.format("/library/sections/%s/search?type=2&query=%s", section, queryTerm);
				PlexHttpClient.get(server, path, new PlexHttpMediaContainerHandler()
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
						feedback.e(getResources().getString(R.string.got_error), error.getMessage());
					}
				});
			}
		}
	}

	private void doLatestEpisode(final String queryTerm) {
		if(shows.size() == 0) {
			feedback.e(getResources().getString(R.string.couldnt_find), queryTerm);
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
			feedback.e(getResources().getString(R.string.found_more_than_one_show));
			return;
		}
		final PlexDirectory show = chosenShow;
		String path = String.format("/library/metadata/%s/allLeaves", show.ratingKey);
		PlexHttpClient.get(show.server, path, new PlexHttpMediaContainerHandler()
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
					feedback.e(getResources().getString(R.string.couldnt_find), queryTerm);
					return;
				}
			}

			@Override
			public void onFailure(Throwable error) {
				feedback.e(getResources().getString(R.string.got_error), error.getMessage());
			}
		});
	}

	private void doShowSearch(String episodeSpecified, final String showSpecified) {
		serversSearched = 0;
		for(final PlexServer server : plexmediaServers.values()) {
			server.showSectionsSearched = 0;
			if(server.tvSections.size() == 0) {
				serversSearched++;
				if(serversSearched == plexmediaServers.size()) {
					playSpecificEpisode(showSpecified);
				}
			}
			for(int i=0;i<server.tvSections.size();i++) {
				String section = server.tvSections.get(i);
				String path = String.format("/library/sections/%s/search?type=4&query=%s", section, episodeSpecified);
				PlexHttpClient.get(server, path, new PlexHttpMediaContainerHandler()
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
						feedback.e(getResources().getString(R.string.got_error), error.getMessage());
					}
				});
			}
		}
	}

	private void playSpecificEpisode(String showSpecified) {
		if(videos.size() == 0) {
			feedback.e(getResources().getString(R.string.couldnt_find_episode));
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
				feedback.e(getResources().getString(R.string.found_more_than_one_show));
				return;
			}
		}
	}

	private void doShowSearch(final String queryTerm, final String season, final String episode) {
		Logger.d("doShowSearch: %s s%s e%s", queryTerm, season, episode);
		serversSearched = 0;
		for(final PlexServer server : plexmediaServers.values()) {
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
				String path = String.format("/library/sections/%s/search?type=2&query=%s", section, queryTerm);
				PlexHttpClient.get(server, path, new PlexHttpMediaContainerHandler()
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
						feedback.e(getResources().getString(R.string.got_error), error.getMessage());
					}
				});
			}
		}
	}

	private void doEpisodeSearch(String queryTerm, final String season, final String episode) {
		Logger.d("Found shows: %d", shows.size());
		serversSearched = 0;
		for(final PlexServer server : plexmediaServers.values()) {
			if(shows.size() == 0 && serversSearched == plexmediaServers.size()) {
				serversSearched++;
				feedback.e(getResources().getString(R.string.couldnt_find), queryTerm);
				return;
			} else if(shows.size() == 1) {
				final PlexDirectory show = shows.get(0);
				Logger.d("Show key: %s", show.key);
				PlexHttpClient.get(server, show.key, new PlexHttpMediaContainerHandler()
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
							feedback.e(getResources().getString(R.string.couldnt_find_season));
							return;
						} else if(foundSeason != null) {
							PlexHttpClient.get(server, foundSeason.key, new PlexHttpMediaContainerHandler()
							{
								@Override
								public void onSuccess(MediaContainer mc)
								{
									Boolean foundEpisode = false;
									Logger.d("Looking for episode %s", episode);
									Logger.d("videoPlayed: %s", videoPlayed);
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
										feedback.e(getResources().getString(R.string.couldnt_find_episode));
										return;
									}
								}

								@Override
								public void onFailure(Throwable error) {
									feedback.e(getResources().getString(R.string.got_error), error.getMessage());
								}
							});
						}
					}

					@Override
					public void onFailure(Throwable error) {
						feedback.e(getResources().getString(R.string.got_error), error.getMessage());
					}
				});
			} else {
				feedback.e(getResources().getString(R.string.couldnt_find), queryTerm);
			}
		}
	}

	private void searchForAlbum(final String artist, final String album) {
		Logger.d("Searching for album %s by %s.", album, artist);
		serversSearched = 0;
		Logger.d("Servers: %d", plexmediaServers.size());
		for(final PlexServer server : plexmediaServers.values()) {
			server.musicSectionsSearched = 0;
			if(server.musicSections.size() == 0) {
				serversSearched++;
				if(serversSearched == plexmediaServers.size()) {
					if(albums.size() == 1) {
						playAlbum(albums.get(0));
					} else {
						feedback.e(getResources().getString(albums.size() > 1 ? R.string.found_more_than_one_album : R.string.couldnt_find_album));
						return;
					}
				}
			}
			for(int i=0;i<server.musicSections.size();i++) {
				String section = server.musicSections.get(i);
				String path = String.format("/library/sections/%s/search?type=9&query=%s", section, album);
				PlexHttpClient.get(server, path, new PlexHttpMediaContainerHandler()
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
										feedback.e(getResources().getString(albums.size() > 1 ? R.string.found_more_than_one_album : R.string.couldnt_find_album));
										return;
									}
								}
							}

						}
					}

					@Override
					public void onFailure(Throwable error) {
						feedback.e(getResources().getString(R.string.got_error), error.getMessage());
					}
				});
			}
		}
	}

	private void searchForSong(final String artist, final String track) {
		serversSearched = 0;
		Logger.d("Servers: %d", plexmediaServers.size());
		for(final PlexServer server : plexmediaServers.values()) {
			server.musicSectionsSearched = 0;
			if(server.musicSections.size() == 0) {
				serversSearched++;
				if(serversSearched == plexmediaServers.size()) {
					if(tracks.size() > 0) {
						playTrack(tracks.get(0));
					} else {
						feedback.e(getResources().getString(R.string.couldnt_find_track));
						return;
					}
				}
			}
			for(int i=0;i<server.musicSections.size();i++) {
				String section = server.musicSections.get(i);
				String path = String.format("/library/sections/%s/search?type=10&query=%s", section, track);
				PlexHttpClient.get(server, path, new PlexHttpMediaContainerHandler()
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
										feedback.e(getResources().getString(R.string.couldnt_find_track));
										return;
									}
								}
							}
						}
					}

					@Override
					public void onFailure(Throwable error) {
						feedback.e(getResources().getString(R.string.got_error), error.getMessage());
					}
				});
			}
		}
	}

	private void playAlbum(final PlexDirectory album) {
		PlexHttpClient.get(album.server, album.key, new PlexHttpMediaContainerHandler()
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
					feedback.e(getResources().getString(R.string.couldnt_find_album));
					return;
				}
			}

			@Override
			public void onFailure(Throwable error) {
				feedback.e(getResources().getString(R.string.got_error), error.getMessage());
			}
		});
	}

	private void playTrack(final PlexTrack track) {
		playTrack(track, null);
	}

	private void playTrack(final PlexTrack track, final PlexDirectory album) {
		QueryString qs = new QueryString("machineIdentifier", track.server.machineIdentifier);
		qs.add("key", track.key);
		qs.add("port", track.server.port);
		qs.add("address", track.server.address);
		if(album != null)
			qs.add("containerKey", album.key);
		if(mPrefs.getBoolean("resume", false) || resumePlayback)
			qs.add("viewOffset", track.viewOffset);
		qs.add(MainActivity.PlexHeaders.XPlexTargetClientIdentifier, client.machineIdentifier);
		String url = String.format("http://%s:%s/player/playback/playMedia?%s", client.host, client.port, qs);

		PlexHttpClient.get(url, new PlexHttpResponseHandler()
		{
			@Override
			public void onSuccess(PlexResponse r)
			{
				Boolean passed = true;
				if(r.code != null) {
					if(!r.code.equals("200")) {
						passed = false;
					}
				}
				Logger.d("Playback response: %s", r.code);
				if(passed) {
					showPlayingTrack(track);
				} else {
					feedback.e(getResources().getString(R.string.http_status_code_error), r.code);
				}
			}

			@Override
			public void onFailure(Throwable error) {
				feedback.e(getResources().getString(R.string.got_error), error.getMessage());
			}
		});
	}

	private void showPlayingTrack(PlexTrack track) {
		Intent nowPlayingIntent = new Intent(this, NowPlayingActivity.class);
		nowPlayingIntent.putExtra("track", track);
		nowPlayingIntent.putExtra("client", client);
		nowPlayingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(nowPlayingIntent);
	}
}
