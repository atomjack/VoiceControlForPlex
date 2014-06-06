package com.atomjack.vcfp.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.QueryString;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexResponse;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.model.PlexVideo;
import com.atomjack.vcfp.model.Timeline;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpResponseHandler;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.codechimp.apprater.AppRater;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NowPlayingActivity extends Activity {
	private PlexVideo playingVideo; // The video currently playing
	private PlexTrack playingTrack; // The track currently playing
	private PlexClient client = null;

	private int commandId = 0;
	private int subscriptionPort = 7777;
	private ServerSocket serverSocket;
	Thread serverThread = null;
	Handler updateConversationHandler;

	private static Serializer serial = new Persister();

	private SharedPreferences mPrefs;

	protected void onCreate(Bundle savedInstanceState) {
		Logger.d("on create NowPlayingActivity");
		super.onCreate(savedInstanceState);

		mPrefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);

//		BugSenseHandler.initAndStartSession(NowPlayingActivity.this, MainActivity.BUGSENSE_APIKEY);

		setContentView(R.layout.play_media);

		AppRater.app_launched(this);

		if(savedInstanceState != null) {
			Logger.d("found saved instance state");
			playingVideo = savedInstanceState.getParcelable("video");
			playingTrack = savedInstanceState.getParcelable("track");
			client = savedInstanceState.getParcelable("client");
		} else {
			playingVideo = getIntent().getParcelableExtra("video");
			client = getIntent().getParcelableExtra("client");
			playingTrack = getIntent().getParcelableExtra("track");
		}

		if(client == null)
			finish();

		if(playingVideo != null) {
			Logger.d("now playing %s", playingVideo.title);
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

			PlexHttpClient.setThumb(playingVideo, (ScrollView) findViewById(R.id.background));
			startSubscription();
		} else if(playingTrack != null) {

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
			startSubscription();
		} else {
			finish();
		}
	}

	private void startSubscription() {
		if(updateConversationHandler == null) {
			updateConversationHandler = new Handler();
			serverThread = new Thread(new ServerThread());
			serverThread.start();
		}
		// Wait 2 seconds and then kick off the subscribe request
		final Handler handler = new Handler();
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				Logger.d("subscribing");
				subscribe();
			}
		}, 2000);
	}

	class ServerThread implements Runnable {

		public void run() {
			Socket socket = null;
			try {
				serverSocket = new ServerSocket(subscriptionPort);
			} catch (IOException e) {
				e.printStackTrace();
			}
			while (!Thread.currentThread().isInterrupted()) {

				try {

					socket = serverSocket.accept();

					Map<String, String> headers = new HashMap<String, String>();
					String line;
					Pattern p = Pattern.compile("^([^:]+): (.+)$");
					Matcher matcher;
					BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					while ((line = reader.readLine()) != null)
					{
						matcher = p.matcher(line);
						if(matcher.find()) {
							headers.put(matcher.group(1), matcher.group(2));
						}
						if(line.equals(""))
						{
							break; // and don't get the next line!
						}
					}
					int contentLength = Integer.parseInt(headers.get("Content-Length"));

					StringBuilder requestContent = new StringBuilder();
					for (int i = 0; i < contentLength; i++)
					{
						requestContent.append((char) reader.read());
					}


					String xml = requestContent.toString();
					MediaContainer mediaContainer = new MediaContainer();

					try {
						mediaContainer = serial.read(MediaContainer.class, xml);
					} catch (Resources.NotFoundException e) {
						e.printStackTrace();
					} catch (Exception e) {
						e.printStackTrace();
					}
					updateConversationHandler.post(new updateUIThread(headers, mediaContainer));

					// Send a response
					String response = "Failure: 200 OK";
					PrintStream output = new PrintStream(socket.getOutputStream());
					output.flush();
					output.println("HTTP/1.1 200 OK");
					output.println("Content-Type: text/plain; charset=UTF-8");
					output.println("Access-Control-Allow-Origin: *");
					output.println("Access-Control-Max-Age: 1209600");
					output.println("");
					output.println(response);

					output.close();
					reader.close();
				} catch (IOException e) {
				} finally {
					try {
						if(socket != null)
							socket.close();
					} catch (Exception ex) {
					}
				}
			}
		}
	}

	class updateUIThread implements Runnable {
		private MediaContainer mc;
		private Map<String, String> headers;

		public updateUIThread(Map<String, String> _headers, MediaContainer _mc) {
			headers = _headers;
			mc = _mc;
		}

		@Override
		public void run() {
			String type = null;
			if(playingVideo != null)
				type = "video";
			else if(playingTrack != null)
				type = "music";
			if(type != null) {
				Timeline timeline = mc.getTimeline(type);
				// If the playing media has stopped, unsubscribe then exit from this activity.
				if(timeline.state.equals("stopped")) {
					unsubscribe(new Runnable() {
						@Override
						public void run() {
							finish();
						}
					});
				}
			}
		}
	}

	private void subscribe() {
		QueryString qs = new QueryString("port", String.valueOf(subscriptionPort));
		qs.add("commandID", String.valueOf(commandId));
		qs.add("protocol", "http");

		Header[] headers = {
						new BasicHeader(PlexHeaders.XPlexClientIdentifier, VoiceControlForPlexApplication.getUUID(mPrefs)),
						new BasicHeader(PlexHeaders.XPlexDeviceName, getString(R.string.app_name))
		};
		Logger.d("Setting client id: %s", VoiceControlForPlexApplication.getUUID(mPrefs));
		PlexHttpClient.get(NowPlayingActivity.this, String.format("http://%s:%s/player/timeline/subscribe?%s", client.host, client.port, qs), headers, new PlexHttpResponseHandler() {
			@Override
			public void onSuccess(PlexResponse response) {
				Logger.d("Subscribed");
				commandId++;
			}

			@Override
			public void onFailure(Throwable error) {

			}
		});
	}

	private void unsubscribe() {
		unsubscribe(null);
	}

	private void unsubscribe(final Runnable onFinish) {
		QueryString qs = new QueryString("commandID", String.valueOf(commandId));
		Header[] headers = {
				new BasicHeader(PlexHeaders.XPlexClientIdentifier, VoiceControlForPlexApplication.getUUID(mPrefs)),
				new BasicHeader(PlexHeaders.XPlexDeviceName, getString(R.string.app_name)),
				new BasicHeader(PlexHeaders.XPlexTargetClientIdentifier, client.machineIdentifier)

		};
		PlexHttpClient.get(NowPlayingActivity.this, String.format("http://%s:%s/player/timeline/unsubscribe?%s", client.host, client.port, qs), headers, new PlexHttpResponseHandler() {
			@Override
			public void onSuccess(PlexResponse response) {
				Logger.d("Unsubscribed");
				commandId++;
				if(onFinish != null)
					onFinish.run();
			}

			@Override
			public void onFailure(Throwable error) {

			}
		});
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		try {
			unsubscribe();
			serverThread.interrupt();
			serverSocket.close();
		} catch(Exception ex) {
			ex.printStackTrace();
		}

	}

	@Override
	protected void onStop() {
		Logger.d("NowPlaying onStop");

		super.onStop();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Logger.d("Saving instance state");
		outState.putParcelable("video", playingVideo);
		outState.putParcelable("client", client);
		outState.putParcelable("track", playingTrack);
	}

	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent);
		if(intent.getExtras().getBoolean("finish") == true)
			finish();
	}
}
