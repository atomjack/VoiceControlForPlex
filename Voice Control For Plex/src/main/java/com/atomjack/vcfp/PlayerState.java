package com.atomjack.vcfp;

import com.google.android.gms.cast.MediaStatus;

public enum PlayerState {
	STOPPED,
	PLAYING,
	PAUSED,
  BUFFERING;

	public static PlayerState getState(String state) {
	 	if(state.equals("playing"))
			return PLAYING;
		else if(state.equals("paused"))
			return PAUSED;
		else if(state.equals("buffering"))
      return BUFFERING;
    else
			return STOPPED;
	}

  public static PlayerState getState(int state) {
    if(state == MediaStatus.PLAYER_STATE_PLAYING)
      return PLAYING;
    else if(state == MediaStatus.PLAYER_STATE_PAUSED)
      return PAUSED;
    else if(state == MediaStatus.PLAYER_STATE_BUFFERING)
      return BUFFERING;
    else
      return STOPPED;
  }
}