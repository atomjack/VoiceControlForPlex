package com.atomjack.wear;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.atomjack.shared.Logger;
import com.atomjack.shared.WearConstants;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class NotificationActivity extends Activity {
//  private ImageView mImageView;
  private TextView mTextView;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Logger.d("[NotificationActivity] onCreate");
    setContentView(R.layout.activity_notification);

//    mImageView = (ImageView) findViewById(R.id.image_view);
    mTextView = (TextView) findViewById(R.id.text_view);

    Intent intent = getIntent();
    if (intent != null) {
      String title = intent.getStringExtra(WearConstants.MEDIA_TITLE);
      Logger.d("[NotificationActivity] title: %s", title);
      mTextView.setText(title + " (a)");

//      final Asset asset = intent.getParcelableExtra(WearConstants.IMAGE);

//      loadBitmapFromAsset(this, asset, mImageView);
    }
  }

  public static void loadBitmapFromAsset(final Context context,
                                         final Asset asset, final ImageView target) {
    if (asset == null) {
      throw new IllegalArgumentException("Asset must be non-null");
    }

    new AsyncTask<Asset, Void, Bitmap>() {
      @Override
      protected Bitmap doInBackground(Asset... assets) {
        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(context)
                .addApi(Wearable.API)
                .build();
        ConnectionResult result =
                googleApiClient.blockingConnect(
                        1000, TimeUnit.MILLISECONDS);
        if (!result.isSuccess()) {
          return null;
        }

        // convert asset into a file descriptor and block until it's ready
        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                googleApiClient, assets[0]).await().getInputStream();
        googleApiClient.disconnect();

        if (assetInputStream == null) {
          Logger.d("Requested an unknown Asset.");
          return null;
        }

        // decode the stream into a bitmap
        return BitmapFactory.decodeStream(assetInputStream);
      }

      @Override
      protected void onPostExecute(Bitmap bitmap) {
        if (bitmap != null) {
          target.setImageBitmap(bitmap);
        }
      }
    }.execute(asset);
  }
}
