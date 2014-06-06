package com.atomjack.vcfp.net;


import com.atomjack.vcfp.model.Pin;

public interface PlexPinResponseHandler {
	void onSuccess(Pin pin);
	void onFailure(Throwable error);
}
