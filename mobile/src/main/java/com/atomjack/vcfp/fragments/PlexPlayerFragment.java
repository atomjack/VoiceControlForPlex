package com.atomjack.vcfp.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import com.atomjack.shared.Logger;
import com.atomjack.shared.PlayerState;
import com.atomjack.shared.model.Timeline;
import com.atomjack.vcfp.PlexSubscription;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.interfaces.PlexSubscriptionListener;
import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexResponse;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpMediaContainerHandler;
import com.atomjack.vcfp.net.PlexHttpResponseHandler;

import java.util.List;

public class PlexPlayerFragment extends PlayerFragment {
  public PlexPlayerFragment() {
    super();
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return super.onCreateView(inflater, container, savedInstanceState);
  }

  @Override
  protected void doRewind() {
    if(position > -1) {
      client.seekTo((position * 1000) - 15000, null);
    }
  }

  @Override
  protected void doForward() {
    if(position > -1) {
      client.seekTo((position * 1000) + 30000, null);
    }
  }

  @Override
  protected void doPlayPause() {
    if(currentState == PlayerState.PLAYING) {
      client.pause(new PlexHttpResponseHandler() {
        @Override
        public void onSuccess(PlexResponse response) {
//          setState(PlayerState.PAUSED);
        }

        @Override
        public void onFailure(Throwable error) {
          // TODO: Handle this
        }
      });
    } else if(currentState == PlayerState.PAUSED) {
      client.play(new PlexHttpResponseHandler() {
        @Override
        public void onSuccess(PlexResponse response) {
//          setState(PlayerState.PLAYING);
        }

        @Override
        public void onFailure(Throwable error) {
          // TODO: Handle this
        }
      });
    }
  }

  @Override
  protected void doStop() {
    client.stop(new PlexHttpResponseHandler() {
      @Override
      public void onSuccess(PlexResponse response) {
//        if(plexSubscriptionListener != null)
//          plexSubscriptionListener.onStopped();
      }

      @Override
      public void onFailure(Throwable error) {

      }
    });
  }

  @Override
  protected void doNext() {
    client.next(null);
  }

  @Override
  protected void doPrevious() {
    client.previous(null);
  }

  @Override
  public void onStopTrackingTouch(SeekBar _seekBar) {
    client.seekTo(_seekBar.getProgress()*1000, new PlexHttpResponseHandler() {
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
