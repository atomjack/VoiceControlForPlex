package com.atomjack.vcfp.interfaces;

public interface ServerFindHandler {
	void onSuccess();
	void onFailure(int statusCode);
}
