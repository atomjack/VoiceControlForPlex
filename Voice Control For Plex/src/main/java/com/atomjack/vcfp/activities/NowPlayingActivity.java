package com.atomjack.vcfp.activities;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.View;
import android.widget.SeekBar;

import com.atomjack.vcfp.BuildConfig;
import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.Preferences;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexResponse;
import com.atomjack.vcfp.model.Timeline;
import com.atomjack.vcfp.net.PlexHttpResponseHandler;
import com.bugsense.trace.BugSenseHandler;

import org.codechimp.apprater.AppRater;

public class NowPlayingActivity extends PlayerActivity {
	private boolean subscribed = false;
  private Handler mHandler;

	PlayerState state = PlayerState.STOPPED;
	enum PlayerState {
		PLAYING,
		STOPPED,
		PAUSED
	};

	protected void onCreate(Bundle savedInstanceState) {
		Logger.d("on create NowPlayingActivity");
		super.onCreate(savedInstanceState);

		Preferences.setContext(this);

    plexSubscription.setListener(this);

		if(BuildConfig.USE_BUGSENSE)
			BugSenseHandler.initAndStartSession(NowPlayingActivity.this, MainActivity.BUGSENSE_APIKEY);

		setContentView(R.layout.play_media);

		AppRater.app_launched(this);

		if(savedInstanceState != null) {
			Logger.d("found saved instance state");
			nowPlayingMedia = savedInstanceState.getParcelable(VoiceControlForPlexApplication.Intent.EXTRA_MEDIA);
      mClient = savedInstanceState.getParcelable(VoiceControlForPlexApplication.Intent.EXTRA_CLIENT);
		} else {
			nowPlayingMedia = getIntent().getParcelableExtra(VoiceControlForPlexApplication.Intent.EXTRA_MEDIA);
      mClient = getIntent().getParcelableExtra(VoiceControlForPlexApplication.Intent.EXTRA_CLIENT);
		}

		if(mClient == null || nowPlayingMedia == null)
			finish();


    Logger.d("mClient: %s", mClient);
    Logger.d("nowPlayingMedia: %s", nowPlayingMedia);
		state = PlayerState.PLAYING;
		showNowPlaying();
		seekBar = (SeekBar)findViewById(R.id.seekBar);
    seekBar.setOnSeekBarChangeListener(this);
    seekBar.setMax(nowPlayingMedia.duration);
    seekBar.setProgress(Integer.parseInt(nowPlayingMedia.viewOffset));

		setCurrentTimeDisplay(getOffset(nowPlayingMedia));
		durationDisplay.setText(VoiceControlForPlexApplication.secondsToTimecode(nowPlayingMedia.duration / 1000));


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
			mClient.pause(new PlexHttpResponseHandler() {
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
			mClient.play(new PlexHttpResponseHandler() {
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
			mClient.seekTo(position - 15000, null);
		}
	}

	public void doForward(View v) {
		if(position > -1) {
			mClient.seekTo(position + 30000, null);
		}
	}

	public void doStop(View v) {
    mClient.stop(null);
	}

	@Override
	protected void onPause() {
		super.onPause();
    Logger.d("NowPlaying onPause");
		VoiceControlForPlexApplication.applicationPaused();
	}

	@Override
	protected void onResume() {
		super.onResume();
    Logger.d("NowPlaying onResume");
		VoiceControlForPlexApplication.applicationResumed();
    plexSubscription.setListener(this);
    if(menu != null) {
      Logger.d("Now subscribing");
      plexSubscription.subscribe(mClient);
    }
	}

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    Logger.d("now playing onconfigchanged: %d", newConfig.orientation);

//    if(nowPlayingMedia instanceof PlexVideo)
      Logger.d("[NowPlayingActivity] Setting thumb in onConfigChanged");
      setThumb();
//    else if(nowPlayingMedia instanceof PlexTrack)
//      setThumb((PlexTrack)nowPlayingMedia, newConfig.orientation, (ImageView)findViewById(R.id.nowPlayingImage));
  }

  @Override
	protected void onDestroy() {
		super.onDestroy();
		try {
			if(subscribed) {
        // TODO: Unsubscribe
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
		outState.putParcelable(VoiceControlForPlexApplication.Intent.EXTRA_MEDIA, nowPlayingMedia);
		outState.putParcelable(VoiceControlForPlexApplication.Intent.EXTRA_CLIENT, mClient);
	}

	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent);
		if(intent.getExtras() != null && intent.getExtras().getBoolean("finish") == true)
			finish();
	}

  @Override
  public void onStartTrackingTouch(SeekBar seekBar) {
    super.onStartTrackingTouch(seekBar);
    isSeeking = true;
  }

  @Override
	public void onStopTrackingTouch(SeekBar _seekBar) {
		mClient.seekTo(_seekBar.getProgress(), new PlexHttpResponseHandler() {
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

  @Override
  protected void onSubscriptionMessage(Timeline timeline) {
//    Logger.d("NowPlaying onSubscriptionMessage: %d", timeline.time);
    if(!isSeeking)
      seekBar.setProgress(timeline.time);

    if(continuing) {
      showNowPlaying(false);
      // Need to update the duration
      seekBar.setMax(nowPlayingMedia.duration);
      durationDisplay.setText(VoiceControlForPlexApplication.secondsToTimecode(nowPlayingMedia.duration / 1000));
      Logger.d("[NowPlayingActivity] Setting thumb in onSubscriptionMessage");
      setThumb();
      continuing = false;
    }

    if(timeline.state.equals("stopped")) {
      Logger.d("NowPlayingActivity stopping");
      if(timeline.continuing != null && timeline.continuing.equals("1")) {
        Logger.d("Continuing to next track");
      } else {
        finish();
      }
    } else if(timeline.state.equals("playing")) {
      setState(PlayerState.PLAYING);
    } else if(timeline.state.equals("paused")) {
      setState(PlayerState.PAUSED);
    }
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu _menu) {
    Logger.d("NowPlaying onPrepareOptionsMenu");
    menu = _menu;
    return super.onPrepareOptionsMenu(_menu);
  }

  @Override
   public boolean onCreateOptionsMenu(Menu _menu) {
    super.onCreateOptionsMenu(_menu);
    Logger.d("NowPlaying onCreateOptionsMenu: %s", _menu);
    getMenuInflater().inflate(R.menu.menu_playing, _menu);
    menu = _menu;
    if(plexSubscription.isSubscribed())
      setCastIconActive();
    else
      plexSubscription.subscribe(mClient);
    return true;
  }

  @Override
  public void onSubscribed(PlexClient _client) {
    Logger.d("NowPlayingActivity onSubscribed: %s", _client);
    super.onSubscribed(_client);
  }

  @Override
  public void onUnsubscribed() {
    super.onUnsubscribed();
    finish();
  }
}
