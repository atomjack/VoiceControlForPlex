package com.atomjack.vcfp.interfaces;

import android.graphics.Bitmap;

public interface BitmapHandlerInterface {
  void onSuccess(Bitmap bitmap);
  void onFailure();
}