package com.atomjack.vcfp;

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
}