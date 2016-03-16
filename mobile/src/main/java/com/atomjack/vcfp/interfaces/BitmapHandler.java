package com.atomjack.vcfp.interfaces;

import android.graphics.Bitmap;

public abstract class BitmapHandler implements BitmapHandlerInterface {
	public void onSuccess(Bitmap bitmap) {}
  public void onFailure() {}
}
