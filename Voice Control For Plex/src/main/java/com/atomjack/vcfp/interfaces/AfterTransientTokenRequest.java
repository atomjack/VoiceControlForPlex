package com.atomjack.vcfp.interfaces;

public interface AfterTransientTokenRequest {
	void success(String token);
	void failure();
}
