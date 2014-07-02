package com.atomjack.vcfp.activities;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.media.MediaRouter;
import android.view.View;
import android.widget.SeekBar;

import com.atomjack.vcfp.AfterTransientTokenRequest;
import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.Preferences;
import com.atomjack.vcfp.QueryString;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VCFPCastConsumer;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.PlexMedia;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.model.PlexVideo;
import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.common.images.WebImage;
import com.google.sample.castcompanionlibrary.cast.VideoCastManager;
import com.google.sample.castcompanionlibrary.widgets.MiniController;

import org.json.JSONObject;

import java.util.Timer;
import java.util.TimerTask;

public class CastActivity extends PlayerActivity {
	private static VideoCastManager castManager = null;
	private VCFPCastConsumer castConsumer;
	private MiniController miniController;

	protected MediaInfo remoteMediaInformation;

	private Timer durationTimer;

	private int currentState = MediaStatus.PLAYER_STATE_UNKNOWN;

	private String transientToken;

	// This gets set to true when we do a seek, so after we receive a message from the receiver that
	// we have stopped, we'll resume with the new offset.
	private boolean seekDone = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Preferences.setContext(this);

		if(getIntent().getAction().equals(VoiceControlForPlexApplication.Intent.CAST_MEDIA)) {
			PlexVideo video = getIntent().getParcelableExtra("video");
			client = getIntent().getParcelableExtra("client");
			PlexTrack track = getIntent().getParcelableExtra("track");

			if(video != null)
				nowPlayingMedia = video;
			else if(track != null)
				nowPlayingMedia = track;
			else {
				// TODO: something here
			}
			resumePlayback = getIntent().getBooleanExtra("resume", false);

			Logger.d("Casting %s", nowPlayingMedia.title);

			setCastConsumer();

			if(castManager == null) {
				castManager = getCastManager(this);
				castManager.addVideoCastConsumer(castConsumer);
				castManager.incrementUiCounter();
			}
			castManager.setDevice(client.castDevice, false);


			miniController = (MiniController) findViewById(R.id.miniController1);
			castManager.addMiniController(miniController);

			showNowPlaying(nowPlayingMedia, client);


			seekBar = (SeekBar)findViewById(R.id.seekBar);
			seekBar.setOnSeekBarChangeListener(this);
			Logger.d("setting progress to %d", getOffset(nowPlayingMedia));
			seekBar.setMax(nowPlayingMedia.duration);
			seekBar.setProgress(getOffset(nowPlayingMedia)*1000);

			setCurrentTimeDisplay(getOffset(nowPlayingMedia));
			durationDisplay.setText(VoiceControlForPlexApplication.secondsToTimecode(nowPlayingMedia.duration / 1000));

			currentState = castManager.getPlaybackStatus();
			if (Preferences.getString(Preferences.PLEX_USERNAME) != null) {
				nowPlayingMedia.server.requestTransientAccessToken(new AfterTransientTokenRequest() {
					@Override
					public void success(String token) {
						Logger.d("Got transient token: %s", token);
						transientToken = token;
						beginPlayback();
					}

					@Override
					public void failure() {
						Logger.d("Failed to get transient access token");
						// Failed to get an access token, so let's try without one
						beginPlayback();
					}
				});
			} else {
				beginPlayback();
			}
		} else {
			// TODO: Something here
		}
	}

	private MediaInfo getMediaInfo(String url) {
		MediaInfo mediaInfo = buildMediaInfo(
			nowPlayingMedia.getTitle(),
			nowPlayingMedia.getSummary(),
			nowPlayingMedia.getEpisodeTitle(),
			url,
			nowPlayingMedia.getArtUri(),
			nowPlayingMedia.getThumbUri(),
			nowPlayingMedia.duration,
			getOffset(nowPlayingMedia)
		);
		return mediaInfo;
	}

	private void beginPlayback() {
		String url = getTranscodeUrl(nowPlayingMedia, transientToken);
		Logger.d("url: %s", url);
		Logger.d("duration: %s", nowPlayingMedia.duration);
		final MediaInfo mediaInfo = getMediaInfo(url);

		Logger.d("offset is %d", getOffset(nowPlayingMedia));
		if(castManager.isConnected()) {
			try {
				castManager.loadMedia(mediaInfo, true, getOffset(nowPlayingMedia) * 1000);
			} catch (Exception ex) {}
		} else {
			castConsumer.setOnConnected(new Runnable() {
				@Override
				public void run() {
					try {
						Logger.d("loading media");
						castManager.loadMedia(mediaInfo, true, getOffset(nowPlayingMedia) * 1000);
//						startDurationTimer();
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			});
		}
	}

	private void stopDurationTimer() {
		if(durationTimer != null)
			durationTimer.cancel();
	}

	private void startDurationTimer() {
		durationTimer = new Timer();
		durationTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				try {
					if(!isSeeking) {
						final long position = castManager.getCurrentMediaPosition();
//					Logger.d("position: %d", position);
						seekBar.setProgress((int) position);
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								setCurrentTimeDisplay(position/1000);
							}
						});

//					Logger.d("Progress now %d", seekBar.getProgress());
					}
				} catch (Exception ex) {
					// silent
					ex.printStackTrace();
				}
			}
		}, 1000, 1000);
	}

	private void setCastConsumer() {
		castConsumer = new VCFPCastConsumer() {
			private boolean launched = false;
			private Runnable onConnectedRunnable;

			@Override
			public void onRemoteMediaPlayerMetadataUpdated() {
				super.onRemoteMediaPlayerMetadataUpdated();

			}

			@Override
			public void onRemoteMediaPlayerStatusUpdated() {
				super.onRemoteMediaPlayerStatusUpdated();
				Logger.d("onRemoteMediaPlayerStatusUpdated");
				try {
					remoteMediaInformation = castManager.getRemoteMediaInformation();
					MediaMetadata metadata = remoteMediaInformation.getMetadata();
					int lastState = currentState;
					currentState = castManager.getPlaybackStatus();


					Logger.d("currentState: %d", currentState);


					if(currentState == MediaStatus.PLAYER_STATE_IDLE) {
						Logger.d("idle reason: %d", castManager.getIdleReason());

						// If we stopped because a seek was done, resume playback at the new offset.
						if(seekDone) {
							seekDone = false;
							Logger.d("resuming playback with an offset of %s", nowPlayingMedia.viewOffset);
							beginPlayback();
						} else {
							if (durationTimer != null)
								stopDurationTimer();
							finish();
						}
					} else if(currentState == MediaStatus.PLAYER_STATE_PAUSED) {
						setState(MediaStatus.PLAYER_STATE_PAUSED);
						stopDurationTimer();
					} else if(currentState == MediaStatus.PLAYER_STATE_PLAYING) {
						setState(MediaStatus.PLAYER_STATE_PLAYING);
						startDurationTimer();
					}




//					Logger.d("metadata: %s", metadata);
				} catch (Exception ex) {
					// silent
					ex.printStackTrace();
				}
			}

			@Override
			public void setOnConnected(Runnable runnable) {
				onConnectedRunnable = runnable;
			}

			@Override
			public void onFailed(int resourceId, int statusCode) {
				Logger.d("castConsumer failed: %d", statusCode);
			}

			@Override
			public void onConnectionSuspended(int cause) {
				Logger.d("onConnectionSuspended() was called with cause: " + cause);
//					com.google.sample.cast.refplayer.utils.Utils.
//									showToast(VideoBrowserActivity.this, R.string.connection_temp_lost);
			}

			@Override
			public void onApplicationConnected(ApplicationMetadata appMetadata,
																				 String sessionId, boolean wasLaunched) {
				Logger.d("onApplicationConnected()");
				Logger.d("metadata: %s", appMetadata);
				Logger.d("sessionid: %s", sessionId);
				Logger.d("was launched: %s", wasLaunched);
				if(!launched || true) {
					launched = true;
					if(onConnectedRunnable != null)
						onConnectedRunnable.run();
				}

			}

			@Override
			public void onConnectivityRecovered() {
//					com.google.sample.cast.refplayer.utils.Utils.
//									showToast(VideoBrowserActivity.this, R.string.connection_recovered);
			}

			@Override
			public void onApplicationStatusChanged(String appStatus) {
				Logger.d("CastActivity onApplicationStatusChanged: %s", appStatus);
			}

			@Override
			public void onCastDeviceDetected(final MediaRouter.RouteInfo info) {
				Logger.d("onCastDeviceDetected: %s", info);
			}
		};
	}

	public static VideoCastManager getCastManager(Context context) {
		if (null == castManager) {
			castManager = VideoCastManager.initialize(context, MainActivity.CHROMECAST_APP_ID,
							null, null);
			castManager.enableFeatures(
							VideoCastManager.FEATURE_NOTIFICATION |
											VideoCastManager.FEATURE_LOCKSCREEN |
											VideoCastManager.FEATURE_DEBUGGING);

		}
		castManager.setContext(context);
//		String destroyOnExitStr = Utils.getStringFromPreference(context,
//						CastPreference.TERMINATION_POLICY_KEY);
		castManager.setStopOnDisconnect(false);
		return castManager;
	}

	private static MediaInfo buildMediaInfo(String title, String summary, String episodeName,
				String url, String imgUrl, String bigImageUrl, int duration, int offset) {
		MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);

//		movieMetadata.putString(MediaMetadata.KEY_SUBTITLE, summary);
//		movieMetadata.putString(MediaMetadata.KEY_TITLE, title);
//		movieMetadata.putString(MediaMetadata.KEY_STUDIO, episodeName);
		movieMetadata.addImage(new WebImage(Uri.parse(imgUrl)));
		movieMetadata.addImage(new WebImage(Uri.parse(bigImageUrl)));

		JSONObject customData = new JSONObject();
		try {
			customData.put("duration", duration/1000);
			customData.put("position", offset);

			customData.put("title", title);
			customData.put("summary", summary);
			customData.put("episodeName", episodeName);

		} catch(Exception ex) {}
		return new MediaInfo.Builder(url)
						.setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
						.setStreamDuration((long)duration)
						.setCustomData(customData)
						.setContentType("video/mp4")
						.setMetadata(movieMetadata)
						.build();
	}

	private String getTranscodeUrl(PlexMedia media, String transientToken) {
		String url = media.server.activeConnection.uri;
		url += "/video/:/transcode/universal/start?";
		QueryString qs = new QueryString("path", String.format("http://127.0.0.1:32400%s", media.key));
		qs.add("mediaIndex", "0");
		qs.add("partIndex", "0");
		qs.add("protocol", "http");
		qs.add("offset", Integer.toString(getOffset(media)));
		qs.add("fastSeek", "1");
		qs.add("directPlay", "0");
		qs.add("directStream", "1");
		qs.add("videoQuality", "60");
		qs.add("videoResolution", "1024x768");
		qs.add("maxVideoBitrate", "2000");
		qs.add("subtitleSize", "100");
		qs.add("audioBoost", "100");
		qs.add("session", Preferences.getUUID());
		qs.add(PlexHeaders.XPlexClientIdentifier, Preferences.getUUID());
		qs.add(PlexHeaders.XPlexProduct, String.format("%s Chromecast", getString(R.string.app_name)));
		qs.add(PlexHeaders.XPlexDevice, client.castDevice.getModelName());
		qs.add(PlexHeaders.XPlexDeviceName, client.castDevice.getModelName());
		qs.add(PlexHeaders.XPlexPlatform, client.castDevice.getModelName());
		if(transientToken != null)
			qs.add(PlexHeaders.XPlexToken, transientToken);
		qs.add(PlexHeaders.XPlexPlatformVersion, "1.0");
		try {
			qs.add(PlexHeaders.XPlexVersion, getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		// TODO: Fix this
		if(Preferences.getString(Preferences.PLEX_USERNAME) != null)
			qs.add(PlexHeaders.XPlexUsername, Preferences.getString(Preferences.PLEX_USERNAME));
		return url + qs.toString();
	}

	@Override
	protected void onResume() {
		Logger.d("CastActivity onResume");
		castManager = getCastManager(this);
		if (null != castManager) {
			castManager.addVideoCastConsumer(castConsumer);
			castManager.incrementUiCounter();
		}

		super.onResume();
	}

	@Override
	protected void onPause() {
		castManager.decrementUiCounter();
		castManager.removeVideoCastConsumer(castConsumer);
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		Logger.d("onDestroy is called");
		stopDurationTimer();
		if (null != castManager) {
			if(miniController != null) {
				miniController.removeOnMiniControllerChangedListener(castManager);
				castManager.removeMiniController(miniController);
			}
			castManager.clearContext(this);
		}
		super.onDestroy();
	}

	public void doPlayPause(View v) {
		try {
			if(currentState ==  MediaStatus.PLAYER_STATE_PAUSED) {
				castManager.play();
			} else if(currentState ==  MediaStatus.PLAYER_STATE_PLAYING) {
				castManager.pause();
			}
		} catch (Exception ex) {}
	}

	public void doRewind(View v) {

	}

	public void doForward(View v) {
	}

	public void doStop(View v) {
		try {
			castManager.stop();
			stopDurationTimer();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void onStopTrackingTouch(SeekBar _seekBar) {
		Logger.d("stopped changing progress");
		try {
			seekDone = true;
			nowPlayingMedia.viewOffset = Integer.toString(_seekBar.getProgress());
			stopDurationTimer();
			castManager.stop();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		isSeeking = false;
	}

	private void setState(int newState) {
		currentState = newState;
		if(currentState ==  MediaStatus.PLAYER_STATE_PAUSED) {
			playPauseButton.setImageResource(R.drawable.button_play);
		} else if(currentState ==  MediaStatus.PLAYER_STATE_PLAYING) {
			playPauseButton.setImageResource(R.drawable.button_pause);
		}
	}
}
