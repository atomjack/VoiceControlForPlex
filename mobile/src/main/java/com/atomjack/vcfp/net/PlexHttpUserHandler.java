package com.atomjack.vcfp.net;

import com.atomjack.vcfp.model.PlexError;
import com.atomjack.vcfp.model.PlexUser;

public interface PlexHttpUserHandler {
	void onSuccess(PlexUser user);
	void onFailure(int statusCode);
}
