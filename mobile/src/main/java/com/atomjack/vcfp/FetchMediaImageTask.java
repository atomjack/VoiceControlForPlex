package com.atomjack.vcfp;

import android.graphics.Bitmap;
import android.os.AsyncTask;

import com.atomjack.vcfp.interfaces.BitmapHandler;
import com.atomjack.vcfp.interfaces.PlexMediaHandler;
import com.atomjack.vcfp.model.PlexMedia;

public class FetchMediaImageTask extends AsyncTask<Void, Void, Void> {
  PlexMedia media;
  int width, height;
  PlexMediaHandler onFinish = null;
  String whichThumb;
  String imageKey;

  public FetchMediaImageTask(PlexMedia media, int width, int height, String whichThumb, String imageKey, PlexMediaHandler onFinish) {
    this.media = media;
    this.width = width;
    this.height = height;
    this.onFinish = onFinish;
    this.whichThumb = whichThumb;
    this.imageKey = imageKey;
  }

  public FetchMediaImageTask(PlexMedia media, int width, int height, String whichThumb, String imageKey) {
    this.media = media;
    this.width = width;
    this.height = height;
    this.whichThumb = whichThumb;
    this.imageKey = imageKey;
  }

  @Override
  protected Void doInBackground(Void... params) {
    VoiceControlForPlexApplication.getInstance().fetchMediaThumb(media, width, height, whichThumb, imageKey, new BitmapHandler() {
      @Override
      public void onSuccess(Bitmap bitmap) {
        if(onFinish != null)
          onFinish.onFinish(media);
      }

      @Override
      public void onFailure() {
        if(onFinish != null)
          onFinish.onFinish(media);
      }
    });
    return null;
  }
}
