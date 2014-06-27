package com.atomjack.vcfp;

public enum PlayerState {
	STOPPED,
	PLAYING,
	PAUSED;

	public static PlayerState getState(String state) {
	 	if(state.equals("playing"))
			return PLAYING;
		else if(state.equals("paused"))
			return PAUSED;
		else
			return STOPPED;
	}
}