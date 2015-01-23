package com.atomjack.vcfp.activities;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.SeekBar;

import com.atomjack.shared.SendToDataLayerThread;
import com.atomjack.shared.WearConstants;
import com.atomjack.vcfp.interfaces.AfterTransientTokenRequest;
import com.atomjack.shared.Logger;
import com.atomjack.shared.PlayerState;
import com.atomjack.shared.Preferences;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VCFPCastConsumer;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexMedia;
import com.google.sample.castcompanionlibrary.cast.VideoCastManager;

import java.util.Arrays;
import java.util.List;

public class CastActivity extends PlayerActivity {
	private static VideoCastManager castManager = null;
	private VCFPCastConsumer castConsumer;

	private PlayerState currentState = PlayerState.STOPPED;

  private List<PlexMedia> nowPlayingAlbum;

  private Dialog connectingDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

    mClient = getIntent().getParcelableExtra(com.atomjack.shared.Intent.EXTRA_CLIENT);
    Logger.d("[CastActivity] set mClient: %s", mClient);
    nowPlayingMedia = getIntent().getParcelableExtra(com.atomjack.shared.Intent.EXTRA_MEDIA);
    nowPlayingAlbum = getIntent().getParcelableArrayListExtra(com.atomjack.shared.Intent.EXTRA_ALBUM);
    resumePlayback = getIntent().getBooleanExtra("resume", false);
    castManager = castPlayerManager.getCastManager();

    if(getIntent().getBooleanExtra(WearConstants.FROM_WEAR, false)) {
      new SendToDataLayerThread(WearConstants.FINISH, this).start();
    }
    // If just playing a single track, put the media into an array
    if(nowPlayingMedia.isMusic() && nowPlayingAlbum == null)
      nowPlayingAlbum = Arrays.asList(nowPlayingMedia);

    Logger.d("[CastActivity] starting up, action: %s, current state: %s", getIntent().getAction(), castPlayerManager.getCurrentState());
    Logger.d("mClient: %s", mClient);
		if(getIntent().getAction() != null && getIntent().getAction().equals(com.atomjack.shared.Intent.CAST_MEDIA)) {

			Logger.d("Casting %s", nowPlayingMedia.title);

      showNowPlaying();
      if(castPlayerManager.isSubscribed()) {
        init();
      } else {
        showConnectingDialog();
        castPlayerManager.subscribe(mClient);
      }
		} else {
      Logger.d("[CastActivity] No action found.");
      if(castPlayerManager.getCurrentState().equals(PlayerState.STOPPED))
        finish();
      else {
        showNowPlaying();
      }
		}
	}

  @Override
  public void showNowPlaying() {
    super.showNowPlaying();
    setupUI();
  }

  @Override
  public void onCastConnected(PlexClient _client) {
    super.onCastConnected(_client);
    hideConnectingDialog();
    init();
  }

  private void setupUI() {
    Logger.d("setting progress to %d", getOffset(nowPlayingMedia));
    seekBar = (SeekBar)findViewById(R.id.seekBar);
    seekBar.setOnSeekBarChangeListener(this);
    seekBar.setMax(nowPlayingMedia.duration);
    seekBar.setProgress(getOffset(nowPlayingMedia)*1000);

    setCurrentTimeDisplay(getOffset(nowPlayingMedia));
    durationDisplay.setText(VoiceControlForPlexApplication.secondsToTimecode(nowPlayingMedia.duration / 1000));
  }

  private void init() {
    currentState = castPlayerManager.getCurrentState();
    Logger.d("castPlayerManager.getCurrentState(): %s", castPlayerManager.getCurrentState());
    if(castPlayerManager.getCurrentState() != PlayerState.STOPPED)
      return;
    if (VoiceControlForPlexApplication.getInstance().prefs.getString(Preferences.PLEX_USERNAME) != null) {
      nowPlayingMedia.server.requestTransientAccessToken(new AfterTransientTokenRequest() {
        @Override
        public void success(String token) {
          Logger.d("Got transient token: %s", token);
          castPlayerManager.setTransientToken(token);
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
  }

  private void showConnectingDialog() {
    if(connectingDialog == null) {
      connectingDialog = new Dialog(this);
    }
    connectingDialog.setContentView(R.layout.search_popup);
    connectingDialog.setTitle(getResources().getString(R.string.connecting));

    // TODO: Re-enable this
//    connectingDialog.setCancelable(false);

    connectingDialog.show();
  }

  private void hideConnectingDialog() {
    if(connectingDialog != null && connectingDialog.isShowing()) {
      connectingDialog.dismiss();
    }
  }

	private void beginPlayback() {
		Logger.d("duration: %s", nowPlayingMedia.duration);

		Logger.d("offset is %d", getOffset(nowPlayingMedia));



		if(castManager.isConnected()) {
        castPlayerManager.loadMedia(nowPlayingMedia, nowPlayingAlbum, getOffset(nowPlayingMedia));
		}
	}

	@Override
	protected void onResume() {
		Logger.d("CastActivity onResume");
		castManager = castPlayerManager.getCastManager();
    castPlayerManager.setListener(this);
		if (null != castManager) {
			castManager.addVideoCastConsumer(castConsumer);
			castManager.incrementUiCounter();
		}
		super.onResume();
	}

	@Override
	protected void onPause() {
    if(castManager != null) {
      castManager.decrementUiCounter();
      castManager.removeVideoCastConsumer(castConsumer);
    }
    VoiceControlForPlexApplication.applicationPaused();
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		Logger.d("[CastActivity] onDestroy");
		if (null != castManager) {
			castManager.clearContext(this);
		}
		super.onDestroy();
	}

  @Override
	public void doPlayPause(View v) {
		try {
      Logger.d("doPlayPause, currentState: %s", currentState);
			if(currentState !=  PlayerState.PLAYING) {
				castPlayerManager.play();
			} else if(currentState ==  PlayerState.PLAYING) {
        castPlayerManager.pause();
			}
		} catch (Exception ex) {}
	}

	public void doRewind(View v) {
    if(position > -1) {
      nowPlayingMedia.viewOffset = Integer.toString(position - 15000);
      if (Integer.parseInt(nowPlayingMedia.viewOffset) < 0) {
        position = 0;
        nowPlayingMedia.viewOffset = "0";
      }
      castPlayerManager.seekTo(Integer.parseInt(nowPlayingMedia.viewOffset) / 1000);
    }
	}

	public void doForward(View v) {
    if(position > -1) {
      nowPlayingMedia.viewOffset = Integer.toString(position + 30000);
      castPlayerManager.seekTo(Integer.parseInt(nowPlayingMedia.viewOffset) / 1000);
    }
	}

  @Override
	public void doStop(View v) {
		try {
      castPlayerManager.stop();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

  public void doNext(View v) {
    Logger.d("doNext");
    castPlayerManager.doNext();
  }

  public void doPrevious(View v) {
    Logger.d("doPrevious");
    castPlayerManager.doPrevious();
  }

	@Override
	public void onStopTrackingTouch(SeekBar _seekBar) {
		Logger.d("stopped changing progress: %d", _seekBar.getProgress());
		try {
			nowPlayingMedia.viewOffset = Integer.toString(_seekBar.getProgress());
      castPlayerManager.seekTo(Integer.parseInt(nowPlayingMedia.viewOffset) / 1000);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
    isSeeking = false;
	}

	private void setState(PlayerState state) {
		currentState = state;
		if(currentState ==  PlayerState.PAUSED) {
			playPauseButton.setImageResource(R.drawable.button_play);
		} else if(currentState ==  PlayerState.PLAYING) {
			playPauseButton.setImageResource(R.drawable.button_pause);
		}
	}

  @Override
  public boolean onCreateOptionsMenu(Menu _menu) {
    super.onCreateOptionsMenu(_menu);
    getMenuInflater().inflate(R.menu.menu_playing, _menu);
    menu = _menu;
    if(castPlayerManager.isSubscribed())
      onSubscribed(mClient);

    return true;
  }

  @Override
  public void onCastPlayerStateChanged(PlayerState state) {
    super.onCastPlayerStateChanged(state);
    Logger.d("[CastActivity] onCastPlayerStateChanged: %s", state);
    if(isSeeking)
      isSeeking = false;
    if(state == PlayerState.STOPPED) {
      Logger.d("[CastActivity] media player is idle, finishing");
      VoiceControlForPlexApplication.getInstance().cancelNotification();
      finish();
    } else
      setState(state);
  }

  @Override
  public void onUnsubscribed() {
    super.onUnsubscribed();
    finish();
  }

  @Override
  public void onCastPlayerTimeUpdate(int seconds) {
    position = seconds * 1000;
    if(!isSeeking)
      seekBar.setProgress(seconds*1000);
  }

  @Override
  public void onCastPlayerPlaylistAdvance(PlexMedia media) {
    nowPlayingMedia = media;
    setupUI();
    showNowPlaying(false);
  }
}
