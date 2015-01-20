package com.atomjack.shared;

import android.content.Context;
import android.net.Uri;

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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;

public class SendToDataLayerThread extends Thread {
  String path;
  DataMap dataMap = new DataMap();
  boolean sendDataItem = false;

  GoogleApiClient googleApiClient = null;

  // Main constructor
  public SendToDataLayerThread(String path, DataMap data, Context context) {
    this.path = path;
    dataMap = data;
    if(googleApiClient == null) {
      googleApiClient = new GoogleApiClient.Builder(context)
              .addApi(Wearable.API)
              .build();
      googleApiClient.connect();
    }
  }

  public SendToDataLayerThread(String path, Context context) {
    this(path, new DataMap(), context);
  }

  public void sendDataItem() {
    sendDataItem = true;
    start();
  }

  public void run() {
    if(sendDataItem) {
      NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
      for (Node node : nodes.getNodes()) {
        // Construct a DataRequest and send over the data layer
        PutDataMapRequest putDMR = PutDataMapRequest.create(path);
        // TODO: Remove this?
        dataMap.putString("timestamp", new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date()));
        putDMR.getDataMap().putAll(dataMap);
        PutDataRequest request = putDMR.asPutDataRequest();
        Wearable.DataApi.putDataItem(googleApiClient, request)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                  @Override
                  public void onResult(DataApi.DataItemResult dataItemResult) {
                    Logger.d("Message: %s putDataItem status: %s", path, dataItemResult.getStatus().toString());
                  }
                });
      }
    } else {
      NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
      for (Node node : nodes.getNodes()) {
        MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), path, dataMap.toByteArray()).await();
        if (result.getStatus().isSuccess()) {
          Logger.d("Message: {" + path + "} sent to: %s", node.getDisplayName());
        } else {
          // Log an error
          Logger.e("ERROR: failed to send Message");
        }
      }
    }
  }
}
