package com.atomjack.vcfp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.SeekBar;

import com.atomjack.vcfp.BuildConfig;
import com.atomjack.vcfp.Feedback;
import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.Preferences;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.PlexResponse;
import com.atomjack.vcfp.model.Timeline;
import com.atomjack.vcfp.net.PlexHttpResponseHandler;
import com.bugsense.trace.BugSenseHandler;

import org.codechimp.apprater.AppRater;

public class NowPlayingActivity extends PlayerActivity {
	private boolean subscribed = false;

	PlayerState state = PlayerState.STOPPED;
	enum PlayerState {
		PLAYING,
		STOPPED,
		PAUSED
	};

	private Feedback feedback;

	protected void onCreate(Bundle savedInstanceState) {
		Logger.d("on create NowPlayingActivity");
		super.onCreate(savedInstanceState);

		Preferences.setContext(this);

		feedback = new Feedback(this);

    plexSubscription.setListener(this);

		if(BuildConfig.USE_BUGSENSE)
			BugSenseHandler.initAndStartSession(NowPlayingActivity.this, MainActivity.BUGSENSE_APIKEY);

		setContentView(R.layout.play_media);

		AppRater.app_launched(this);

		if(savedInstanceState != null) {
			Logger.d("found saved instance state");
			nowPlayingMedia = savedInstanceState.getParcelable(VoiceControlForPlexApplication.Intent.EXTRA_MEDIA);
			client = savedInstanceState.getParcelable("client");
		} else {
			nowPlayingMedia = getIntent().getParcelableExtra(VoiceControlForPlexApplication.Intent.EXTRA_MEDIA);
			client = getIntent().getParcelableExtra("client");
		}

		if(client == null || nowPlayingMedia == null)
			finish();

    Logger.d("client: %s", client);
    Logger.d("nowPlayingMedia: %s", nowPlayingMedia);
		state = PlayerState.PLAYING;
		showNowPlaying(nowPlayingMedia, client);
		seekBar = (SeekBar)findViewById(R.id.seekBar);
    seekBar.setOnSeekBarChangeListener(this);
    seekBar.setProgress(0);
    seekBar.setMax(nowPlayingMedia.duration);
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
			client.seekTo(position - 15000, null);
		}
	}

	public void doForward(View v) {
		if(position > -1) {
			client.seekTo(position + 30000, null);
		}
	}

	public void doStop(View v) {
		client.stop(null);
	}

	@Override
	protected void onPause() {
		super.onPause();
		VoiceControlForPlexApplication.applicationPaused();
	}

	@Override
	protected void onResume() {
		super.onResume();
		VoiceControlForPlexApplication.applicationResumed();
    plexSubscription.setListener(this);
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
		outState.putParcelable("client", client);
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

  @Override
  protected void onSubscriptionMessage(Timeline timeline) {
    if(!isSeeking)
      seekBar.setProgress(timeline.time);
//    Logger.d("NowPlayingActivity message: %s", timeline.state);
    if(timeline.state.equals("stopped")) {
      // TODO: unsub here?
      finish();
    } else if(timeline.state.equals("playing")) {
      setState(PlayerState.PLAYING);
    } else if(timeline.state.equals("paused")) {
      setState(PlayerState.PAUSED);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu _menu) {
    super.onCreateOptionsMenu(_menu);
    getMenuInflater().inflate(R.menu.menu_playing, _menu);
    menu = _menu;
    if(plexSubscription.isSubscribed())
      onSubscribed();

    return true;
  }

  @Override
  public void onSubscribed() {
    super.onSubscribed();
  }

  @Override
  public void onUnsubscribed() {
    super.onUnsubscribed();
    finish();
  }
}
