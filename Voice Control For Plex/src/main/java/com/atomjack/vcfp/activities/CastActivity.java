package com.atomjack.vcfp.activities;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.media.MediaRouter;
import android.util.Log;

import com.atomjack.vcfp.AfterTransientTokenRequest;
import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.PlexHeaders;
import com.atomjack.vcfp.Preferences;
import com.atomjack.vcfp.QueryString;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VCFPCastConsumer;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexTrack;
import com.atomjack.vcfp.model.PlexVideo;
import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.common.images.WebImage;
import com.google.sample.castcompanionlibrary.cast.BaseCastManager;
import com.google.sample.castcompanionlibrary.cast.VideoCastManager;
import com.google.sample.castcompanionlibrary.cast.callbacks.IVideoCastConsumer;
import com.google.sample.castcompanionlibrary.cast.callbacks.VideoCastConsumerImpl;
import com.google.sample.castcompanionlibrary.utils.LogUtils;
import com.google.sample.castcompanionlibrary.widgets.MiniController;

public class CastActivity extends NowPlayingActivity {
	private PlexVideo playingVideo; // The video currently playing
	private PlexTrack playingTrack; // The track currently playing
	private PlexClient client = null;

	private boolean resumePlayback;
	private static VideoCastManager castManager = null;
	private VCFPCastConsumer castConsumer;
	private MiniController miniController;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Preferences.setContext(this);

		if(getIntent().getAction().equals(VoiceControlForPlexApplication.Intent.CAST_MEDIA)) {
			playingVideo = getIntent().getParcelableExtra("video");
			client = getIntent().getParcelableExtra("client");
			playingTrack = getIntent().getParcelableExtra("track");

			resumePlayback = getIntent().getBooleanExtra("resume", false);

			Logger.d("Casting %s", playingVideo.title);

			setContentView(R.layout.now_playing_movie);

			setCastConsumer();

			if(castManager == null) {
				castManager = getCastManager(this);
				castManager.addVideoCastConsumer(castConsumer);
				castManager.incrementUiCounter();
			}
			castManager.setDevice(client.castDevice, false);


			miniController = (MiniController) findViewById(R.id.miniController1);
			castManager.addMiniController(miniController);

			NowPlayingActivity.showNowPlaying(CastActivity.this, playingVideo, client);

			if(Preferences.getString(Preferences.PLEX_USERNAME) != null) {
				playingVideo.server.requestTransientAccessToken(new AfterTransientTokenRequest() {
					@Override
					public void success(String token) {
						Logger.d("Got transient token: %s", token);
						beginPlayback(token);
					}

					@Override
					public void failure() {
						Logger.d("Failed to get transient access token");
						// Failed to get an access token, so let's try without one
						beginPlayback(null);
					}
				});
			} else {
				beginPlayback(null);
			}
		} else {
			// TODO: Something here
		}
	}

	private void beginPlayback(String token) {
		String url = getTranscodeUrl(playingVideo, token);
		Logger.d("url: %s", url);
		final MediaInfo mediaInfo = buildMediaInfo(
			playingVideo.type.equals("movie") ? playingVideo.title : playingVideo.showTitle,
			playingVideo.summary,
			playingVideo.type.equals("movie") ? "" : playingVideo.title,
			url,
			playingVideo.getArtUri(),
			playingVideo.getThumbUri()
		);

		castConsumer.setOnConnected(new Runnable() {
			@Override
			public void run() {
				try {
					Logger.d("loading media");
					castManager.loadMedia(mediaInfo, true, 0);

				} catch(Exception ex) {
					ex.printStackTrace();
				}
			}
		});
	}

	private void setCastConsumer() {
		castConsumer = new VCFPCastConsumer() {
			private boolean launched = false;
			private Runnable onConnectedRunnable;

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

	private static MediaInfo buildMediaInfo(String title, String summary, String episodeName, String url, String imgUrl, String bigImageUrl) {
		MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);

		movieMetadata.putString(MediaMetadata.KEY_SUBTITLE, summary);
		movieMetadata.putString(MediaMetadata.KEY_TITLE, title);
		movieMetadata.putString(MediaMetadata.KEY_STUDIO, episodeName);
		movieMetadata.addImage(new WebImage(Uri.parse(imgUrl)));
		movieMetadata.addImage(new WebImage(Uri.parse(bigImageUrl)));

		return new MediaInfo.Builder(url)
						.setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
						.setContentType("video/mp4")
						.setMetadata(movieMetadata)
						.build();
	}

	private String getTranscodeUrl(PlexVideo video, String transientToken) {
		String url = video.server.activeConnection.uri;
		url += "/video/:/transcode/universal/start?";
		QueryString qs = new QueryString("path", String.format("http://127.0.0.1:32400%s", video.key));
		qs.add("mediaIndex", "0");
		qs.add("partIndex", "0");
		qs.add("protocol", "http");
//		qs.add("offset")
		if((Preferences.get(Preferences.RESUME, false) || resumePlayback) && video.viewOffset != null)
			qs.add("offset", Integer.toString(Integer.parseInt(video.viewOffset) / 1000));
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
		Logger.d("onResume() was called");
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
		if (null != castManager) {
//			mMini.removeOnMiniControllerChangedListener(castManager);
//			castManager.removeMiniController(mMini);
			castManager.clearContext(this);
		}
		super.onDestroy();
	}
}
