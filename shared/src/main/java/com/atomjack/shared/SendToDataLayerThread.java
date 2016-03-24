package com.atomjack.shared;

import android.content.Context;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.text.SimpleDateFormat;
import java.util.Date;

public class SendToDataLayerThread extends Thread {
  private NewLogger logger;
  String path;
  DataMap dataMap = new DataMap();
  boolean sendDataItem = false;

  GoogleApiClient googleApiClient = null;

  // Main constructor
  public SendToDataLayerThread(String path, DataMap data, Context context) {
    logger = new NewLogger(this);
    this.path = path;
    dataMap = data;
    if(googleApiClient == null) {
      googleApiClient = new GoogleApiClient.Builder(context)
              .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                @Override
                public void onConnected(Bundle connectionHint) {
                  logger.d("onConnected: " + connectionHint);
                  // Now you can use the Data Layer API
                }
                @Override
                public void onConnectionSuspended(int cause) {
                  logger.d("onConnectionSuspended: " + cause);
                }
              })
              .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                @Override
                public void onConnectionFailed(ConnectionResult result) {
                  logger.d("onConnectionFailed: " + result);
                }
              })
              .addApi(Wearable.API)
              .build();
      googleApiClient.connect();
    }
  }

  public SendToDataLayerThread(String path, Context context) {
    this(path, new DataMap(), context);
  }

  public void sendDataItem() {
    logger = new NewLogger(this);
    sendDataItem = true;
    start();
  }

  public void run() {
    if(sendDataItem) {
      NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
      for (Node node : nodes.getNodes()) {
        // Construct a DataRequest and send over the data layer
        PutDataMapRequest putDMR = PutDataMapRequest.create(path);
        dataMap.putString("timestamp", new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date()));
        putDMR.getDataMap().putAll(dataMap);
        PutDataRequest request = putDMR.asPutDataRequest();
        Wearable.DataApi.putDataItem(googleApiClient, request)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                  @Override
                  public void onResult(DataApi.DataItemResult dataItemResult) {
                    logger.d("Message: %s putDataItem status: %s", path, dataItemResult.getStatus().toString());
                  }
                });
      }
    } else {
      NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
      for (Node node : nodes.getNodes()) {
        MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), path, dataMap.toByteArray()).await();
        if (result.getStatus().isSuccess()) {
          logger.d("Message: {" + path + "} sent to: %s", node.getDisplayName());
        } else {
          // Log an error
          logger.e("ERROR: failed to send Message");
        }
      }
    }
  }
}
