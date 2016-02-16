package com.atomjack.vcfp.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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

  /*
  @Override
  public void onSubscribed(PlexClient client) {
    Logger.d("[PlexPlayerFragment] onSubscribed");
  }

  @Override
  public void onUnsubscribed() {
    Logger.d("[PlexPlayerFragment] Unsubscribed");
//    if(activityListener != null)
//      activityListener.onUnsubscribed();
  }

  @Override
  public void onStopped() {

  }

  @Override
  public void onTimelineReceived(MediaContainer mc) {
    List<Timeline> timelines = mc.timelines;
    if(timelines != null) {
      for (Timeline timeline : timelines) {
        if (timeline.key != null) {
          if(timeline.state == null)
            timeline.state = "stopped";
//          Logger.d("[PlexPlayerFragment] onTimelineReceived: %s", timeline.state);
//          Logger.d("nowPlayingMedia: %s", nowPlayingMedia);
          // Get this media's info
          PlexServer server = null;
          for(PlexServer s : VoiceControlForPlexApplication.servers.values()) {
            if(s.machineIdentifier.equals(timeline.machineIdentifier)) {
              server = s;
              break;
            }
          }
          if((!timeline.state.equals("stopped") && nowPlayingMedia == null) || continuing) {
            // TODO: Might need to refresh server?
            if(server != null)
              getPlayingMedia(server, timeline);
          }

          if(nowPlayingMedia != null) {
//            Logger.d("timeline key: %s, now playing key: %s", timeline.key, nowPlayingMedia.key);
            if(timeline.key != null && timeline.key.equals(nowPlayingMedia.key)) {
              // Found an update for the currently playing media
              PlayerState oldState = currentState;
              currentState = PlayerState.getState(timeline.state);
              nowPlayingMedia.viewOffset = Integer.toString(timeline.time);
              if(oldState != currentState) {
                sendWearPlaybackChange();
                if(currentState == PlayerState.PLAYING) {
                  Logger.d("client is now playing");
                } else if(currentState == PlayerState.PAUSED) {
                  Logger.d("client is now paused");
                } else if(currentState == PlayerState.STOPPED) {
                  Logger.d("client is now stopped");
                  if(!continuing) {
                    VoiceControlForPlexApplication.getInstance().cancelNotification();
                    nowPlayingMedia = null;
                  }
                }
                if(currentState != PlayerState.STOPPED && oldState != currentState && VoiceControlForPlexApplication.getInstance().getNotificationStatus() != VoiceControlForPlexApplication.NOTIFICATION_STATUS.initializing) {
                  Logger.d("onTimelineReceived setting notification with %s", currentState);
                  VoiceControlForPlexApplication.getInstance().setNotification(client, currentState, nowPlayingMedia);
                }
              }
            } else if(timeline.key != null) {
              // A different piece of media is playing
              getPlayingMedia(server, timeline);
            }
            position = timeline.time;
          }
            onSubscriptionMessage(timeline);
        }
      }
    }
  }

  @Override
  public void onSubscribeError(String errorMessage) {

  }

  @Override
  protected void onMediaChange() {
    Logger.d("[PlexPlayerFragment] onMediaChange: %s, duration %d", nowPlayingMedia.title, nowPlayingMedia.duration);
    seekBar.setMax(nowPlayingMedia.duration);
    durationDisplay.setText(VoiceControlForPlexApplication.secondsToTimecode(nowPlayingMedia.duration / 1000));
    Logger.d("[PlexPlayerFragment] Setting thumb in onSubscriptionMessage");
    setThumb();
    showNowPlaying(false);
  }
  */

  protected void onSubscriptionMessage(Timeline timeline) {
    Logger.d("[NowPlaying] onSubscriptionMessage: %d, Continuing: %s", timeline.time, continuing);
    if(!isSeeking)
      seekBar.setProgress(timeline.time);

    if(continuing) {
      onMediaChange();
      continuing = false;
    }

    if(timeline.state.equals("stopped")) {
      Logger.d("PlexPlayerFragment stopping");
      if(timeline.continuing != null && timeline.continuing.equals("1")) {
        Logger.d("Continuing to next track");
      } else {
        VoiceControlForPlexApplication.getInstance().cancelNotification();
//        if(activityListener != null)
//          activityListener.onStopped();
//        else
//          Logger.d("activitylistener is null");
      }
    } else if(timeline.state.equals("playing")) {
      setState(PlayerState.PLAYING);
    } else if(timeline.state.equals("paused")) {
      setState(PlayerState.PAUSED);
    }
  }

//  private void setState(PlayerState newState) {
//
//  }

  @Override
  protected void doRewind() {
    if(position > -1) {
      client.seekTo(position - 15000, null);
    }
  }

  @Override
  protected void doForward() {
    if(position > -1) {
      client.seekTo(position + 30000, null);
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
  protected void doMediaOptions() {

  }

  @Override
  protected void doMic() {

  }
}
