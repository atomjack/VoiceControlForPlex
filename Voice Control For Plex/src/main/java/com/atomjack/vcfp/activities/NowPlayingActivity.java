package com.atomjack.vcfp.activities;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.SeekBar;

import com.atomjack.vcfp.BuildConfig;
import com.atomjack.vcfp.Feedback;
import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.Preferences;
import com.atomjack.vcfp.QueryString;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexResponse;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.model.PlexVideo;
import com.atomjack.vcfp.model.Timeline;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpResponseHandler;
import com.bugsense.trace.BugSenseHandler;

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

public class NowPlayingActivity extends PlayerActivity {
	private int commandId = 0;
	private int subscriptionPort = 59409;
	private boolean subscribed = false;
	private boolean subscriptionHasStarted = false;
	private ServerSocket serverSocket;
	Thread serverThread = null;
	Handler updateConversationHandler;

	private int position = -1;

	PlayerState state = PlayerState.STOPPED;
	enum PlayerState {
		PLAYING,
		STOPPED,
		PAUSED
	};

	private Feedback feedback;

	private static Serializer serial = new Persister();

	protected void onCreate(Bundle savedInstanceState) {
		Logger.d("on create NowPlayingActivity");
		super.onCreate(savedInstanceState);

		Preferences.setContext(this);
		state = PlayerState.STOPPED;

		feedback = new Feedback(this);


		if(BuildConfig.USE_BUGSENSE)
			BugSenseHandler.initAndStartSession(NowPlayingActivity.this, MainActivity.BUGSENSE_APIKEY);

		setContentView(R.layout.play_media);

		AppRater.app_launched(this);

		if(savedInstanceState != null) {
			Logger.d("found saved instance state");
			playingMedia = savedInstanceState.getParcelable(VoiceControlForPlexApplication.Intent.EXTRA_MEDIA);
			client = savedInstanceState.getParcelable("client");
		} else {
			playingMedia = getIntent().getParcelableExtra(VoiceControlForPlexApplication.Intent.EXTRA_MEDIA);
			client = getIntent().getParcelableExtra("client");
		}

		if(client == null || playingMedia == null)
			finish();

		state = PlayerState.PLAYING;
		showNowPlaying(playingMedia, client);
		seekBar = (SeekBar)findViewById(R.id.seekBar);
		seekBar.setOnSeekBarChangeListener(this);
		seekBar.setProgress(0);
		seekBar.setMax(playingMedia.duration);
		setCurrentTimeDisplay(getOffset(playingMedia));
		durationDisplay.setText(VoiceControlForPlexApplication.secondsToTimecode(playingMedia.duration / 1000));
		startSubscription();
	}


	private void setState(PlayerState newState) {
		state = newState;
		if(state == PlayerState.PAUSED) {
			playPauseButton.setImageResource(R.drawable.button_play);
		} else if(state == PlayerState.PLAYING) {
			playPauseButton.setImageResource(R.drawable.button_pause);
		}
	}

	public void doPlayPause(View v) {
		Logger.d("play pause clicked");
		if(state == PlayerState.PLAYING) {
			client.pause(new PlexHttpResponseHandler() {
				@Override
				public void onSuccess(PlexResponse response) {
					setState(PlayerState.PAUSED);
				}

				@Override
				public void onFailure(Throwable error) {
					// TODO: Handle this
				}
			});
		} else if(state == PlayerState.PAUSED) {
			client.play(new PlexHttpResponseHandler() {
				@Override
				public void onSuccess(PlexResponse response) {
					setState(PlayerState.PLAYING);
				}

				@Override
				public void onFailure(Throwable error) {
					// TODO: Handle this
				}
			});
		}
	}

	public void doRewind(View v) {
		if(position > -1) {
			client.seekTo(position - 15000, new PlexHttpResponseHandler() {
				@Override
				public void onSuccess(PlexResponse response) {

				}

				@Override
				public void onFailure(Throwable error) {

				}
			});
		}
	}

	public void doForward(View v) {
		if(position > -1) {
			client.seekTo(position + 30000, new PlexHttpResponseHandler() {
				@Override
				public void onSuccess(PlexResponse response) {

				}

				@Override
				public void onFailure(Throwable error) {

				}
			});
		}
	}

	public void doStop(View v) {
		client.stop(null);
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

					if(serverSocket == null)
						return;
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

					/*
					    <Timeline address="192.168.1.101" audioStreamID="158"
					    containerKey="/library/metadata/14"
					    controllable="playPause,stop,shuffle,repeat,volume,stepBack,stepForward,seekTo,subtitleStream,audioStream"
					    duration="9266976" guid="com.plexapp.agents.imdb://tt0090605?lang=en"
					    key="/library/metadata/14" location="fullScreenVideo"
					    machineIdentifier="a667225557b46d69d2d037fbd42c9a639928780c" mute="0" playQueueItemID="14"
					    port="32400" protocol="http" ratingKey="14" repeat="0" seekRange="0-9266976" shuffle="0"
					    state="playing" subtitleStreamID="-1" time="4087" type="video" volume="1" />
					 */

					String xml = requestContent.toString();
					MediaContainer mediaContainer = new MediaContainer();

//					Logger.d("xml: %s", xml);
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
			if(playingMedia instanceof PlexVideo)
				type = "video";
			else if(playingMedia instanceof PlexTrack)
				type = "music";
			if(type != null) {
				Timeline timeline = mc.getTimeline(type);
				position = timeline.time;
				if(timeline.state.equals("stopped"))
					state = PlayerState.STOPPED;
				else if(timeline.state.equals("playing"))
					setState(PlayerState.PLAYING);
				else if(timeline.state.equals("paused"))
					setState(PlayerState.PAUSED);
				// If the playing media has stopped, unsubscribe then exit from this activity.
				if(timeline.state.equals("stopped") && subscriptionHasStarted) {
					unsubscribe(new Runnable() {
						@Override
						public void run() {
							subscriptionHasStarted = false;
							serverThread.interrupt();
							try {
								if (serverSocket != null) {
									serverSocket.close();
								}
							} catch (Exception ex) {
								ex.printStackTrace();
							}
							if(VoiceControlForPlexApplication.isApplicationVisible())
								finish();
						}
					});
				} else if(timeline.state.equals("playing")) {
					if(!isSeeking)
						seekBar.setProgress(timeline.time);
					subscriptionHasStarted = true;
				}
			}
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		VoiceControlForPlexApplication.applicationPaused();
		Logger.d("now playing paused");
	}

	@Override
	protected void onResume() {
		super.onResume();
		Logger.d("now playing resumed");
		VoiceControlForPlexApplication.applicationResumed();
	}

	private void subscribe() {
		QueryString qs = new QueryString("port", String.valueOf(subscriptionPort));
		qs.add("commandID", String.valueOf(commandId));
		qs.add("protocol", "http");

		Header[] headers = {
			new BasicHeader(PlexHeaders.XPlexClientIdentifier, Preferences.getUUID()),
			new BasicHeader(PlexHeaders.XPlexDeviceName, getString(R.string.app_name))
		};
		PlexHttpClient.get(NowPlayingActivity.this, String.format("http://%s:%s/player/timeline/subscribe?%s", client.address, client.port, qs), headers, new PlexHttpResponseHandler() {
			@Override
			public void onSuccess(PlexResponse response) {
				Logger.d("Subscribed");
				commandId++;
				subscribed = true;
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
			new BasicHeader(PlexHeaders.XPlexClientIdentifier, Preferences.getUUID()),
			new BasicHeader(PlexHeaders.XPlexDeviceName, getString(R.string.app_name)),
			new BasicHeader(PlexHeaders.XPlexTargetClientIdentifier, client.machineIdentifier)
		};
		PlexHttpClient.get(NowPlayingActivity.this, String.format("http://%s:%s/player/timeline/unsubscribe?%s", client.address, client.port, qs), headers, new PlexHttpResponseHandler() {
			@Override
			public void onSuccess(PlexResponse response) {
				Logger.d("Unsubscribed");
				subscribed = false;
				commandId++;
				if(onFinish != null)
					onFinish.run();
			}

			@Override
			public void onFailure(Throwable error) {
				// TODO: Handle failure here?
				Logger.d("failure unsubscribing");
			}
		});
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		try {
			if(subscribed) {
				unsubscribe();
				serverThread.interrupt();
				if(serverSocket != null) {
					serverSocket.close();
					Logger.d("Closed serverSocket");
				} else {
					Logger.d("ServerSocket was null");
				}
			}
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
		outState.putParcelable(VoiceControlForPlexApplication.Intent.EXTRA_MEDIA, playingMedia);
		outState.putParcelable("client", client);
	}

	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent);
		if(intent.getExtras().getBoolean("finish") == true)
			finish();
	}

	@Override
	public void onStopTrackingTouch(SeekBar _seekBar) {
		client.seekTo(_seekBar.getProgress(), new PlexHttpResponseHandler() {
			@Override
			public void onSuccess(PlexResponse response) {
				isSeeking = false;
			}

			@Override
			public void onFailure(Throwable error) {
				isSeeking = false;
				feedback.e(String.format(getString(R.string.error_seeking), error.getMessage()));
			}
		});

	}
}
