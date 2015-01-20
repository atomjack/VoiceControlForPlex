package com.atomjack.shared;

import com.atomjack.shared.model.Timeline;

public enum PlayerState {
	STOPPED,
	PLAYING,
	PAUSED,
  BUFFERING;

	public static PlayerState getState(String state) {
    if(state != null) {
      state = state.toLowerCase();
      if (state.equals("playing"))
        return PLAYING;
      else if (state.equals("paused"))
        return PAUSED;
      else if (state.equals("buffering"))
        return BUFFERING;
    }
		return STOPPED;
	}

  public String getWearConstant() {
    String con = WearConstants.MEDIA_STOPPED;
    if(this == PAUSED)
      con = WearConstants.MEDIA_PAUSED;
    else if(this == PLAYING)
      con = WearConstants.MEDIA_PLAYING;
    return con;
  }

  public static PlayerState getState(Timeline t) {
    if(t == null)
      return STOPPED;
    else
      return getState(t.state);
  }

//  public static PlayerState getState(int state) {
//    if(state == MediaStatus.PLAYER_STATE_PLAYING)
//      return PLAYING;
//    else if(state == MediaStatus.PLAYER_STATE_PAUSED)
//      return PAUSED;
//    else if(state == MediaStatus.PLAYER_STATE_BUFFERING)
//      return BUFFERING;
//    else
//      return STOPPED;
//  }
}