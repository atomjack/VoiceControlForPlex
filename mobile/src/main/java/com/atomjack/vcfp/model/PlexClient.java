package com.atomjack.vcfp.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.atomjack.shared.Logger;
import com.atomjack.vcfp.Utils;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpResponseHandler;
import com.google.android.gms.cast.CastDevice;

import org.simpleframework.xml.Root;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import retrofit.Call;
import retrofit.Callback;
import retrofit.Response;

@Root(name="Server", strict=false)
public class PlexClient extends PlexDevice {
	public boolean isCastClient = false;
//  public List<MediaRouter.RouteInfo> castRoutes;
  public CastDevice castDevice;
	public boolean isAudioOnly = false;

	public PlexClient() {
    super();
	}

	public static PlexClient fromDevice(Device device) {
		PlexClient client = new PlexClient();
		client.name = device.name;
		client.address = device.connections.get(0).address;
		client.port = device.connections.get(0).port;
		client.version = device.productVersion;
		return client;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel parcel, int i) {
		parcel.writeString(name);
		parcel.writeString(port);
		parcel.writeString(version);
		parcel.writeString(product);
		parcel.writeString(address);
		parcel.writeString(machineIdentifier);
		parcel.writeInt(isCastClient ? 1 : 0);
		parcel.writeInt(isAudioOnly ? 1 : 0);
		parcel.writeParcelable(castDevice, i);
	}

	public PlexClient(Parcel in) {
		name = in.readString();
		port = in.readString();
		version = in.readString();
		product = in.readString();
		address = in.readString();
		machineIdentifier = in.readString();
		isCastClient = in.readInt() == 1;
		isAudioOnly = in.readInt() == 1;
		castDevice = in.readParcelable(CastDevice.class.getClassLoader());
	}

	public static final Parcelable.Creator<PlexClient> CREATOR = new Parcelable.Creator<PlexClient>() {
		public PlexClient createFromParcel(Parcel in) {
			return new PlexClient(in);
		}

		public PlexClient[] newArray(int size) {
			return new PlexClient[size];
		}
	};

	public void seekTo(int offset, String type, PlexHttpResponseHandler responseHandler) {
		PlexHttpClient.get(String.format("http://%s:%s", address, port), String.format("player/playback/seekTo?commandID=0&type=%s&offset=%s", type, offset), responseHandler);
	}

  public void pause(PlexHttpResponseHandler responseHandler) {
    adjustPlayback("pause", responseHandler);
  }

  public void stop(PlexHttpResponseHandler responseHandler) {
    adjustPlayback("stop", responseHandler);
  }

  public void play(PlexHttpResponseHandler responseHandler) {
    adjustPlayback("play", responseHandler);
  }

  public void next(PlexHttpResponseHandler responseHandler) {
    adjustPlayback("skipNext", responseHandler);
  }

  public void previous(PlexHttpResponseHandler responseHandler) {
    adjustPlayback("skipPrevious", responseHandler);
  }

	// asynchronous
	private void adjustPlayback(String which, PlexHttpResponseHandler responseHandler) {
		ArrayList<String> validModes = new ArrayList<String>(Arrays.asList("pause", "play", "stop", "skipNext", "skipPrevious"));
		if(validModes.indexOf(which) == -1)
			return;
		try {
			PlexHttpClient.get(String.format("http://%s:%s", address, port), String.format("player/playback/%s?commandID=0", which), responseHandler);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// synchronous
  private PlexResponse adjustPlayback(String which) {
		Logger.d("Adjusting playback with %s", which);
    try {
      PlexHttpClient.PlexHttpService service = PlexHttpClient.getService(String.format("http://%s:%s", address, port));
      Logger.d("Seeking with uuid %s", VoiceControlForPlexApplication.getInstance().getUUID());
      Call<PlexResponse> call = service.adjustPlayback(which, "0", VoiceControlForPlexApplication.getInstance().getUUID());
      return call.execute().body();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public PlexResponse pause() {
    return adjustPlayback("pause");
  }

  public PlexResponse stop() {
    return adjustPlayback("stop");
  }

  public PlexResponse play() {
    return adjustPlayback("play");
  }

  public PlexResponse seekTo(int offset) {
    try {
      PlexHttpClient.PlexHttpService service = PlexHttpClient.getService(String.format("http://%s:%s", address, port));
			Logger.d("Seeking with uuid %s", VoiceControlForPlexApplication.getInstance().getUUID());
      Call<PlexResponse> call = service.seekTo(offset, "0", VoiceControlForPlexApplication.getInstance().getUUID());
      return call.execute().body();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public boolean isLocalDevice() {
    String localip = Utils.getIPAddress(true);
    return localip.equals(address);
  }

	public String toString() {
		String output = String.format("Name: %s", name);

		return output;
	}

  public void setStream(Stream stream) {
    if(isCastClient) {
      VoiceControlForPlexApplication.getInstance().castPlayerManager.setActiveStream(stream);
    } else {
      PlexHttpClient.PlexHttpService service = PlexHttpClient.getService(String.format("http://%s:%s", address, port));
      HashMap<String, String> qs = new HashMap<>();
      if (stream.streamType == Stream.AUDIO) {
        qs.put("audioStreamID", stream.id);
      } else if (stream.streamType == Stream.SUBTITLE) {
        qs.put("subtitleStreamID", stream.id);
      }
      Call<PlexResponse> call = service.setStreams(qs, "0", VoiceControlForPlexApplication.getInstance().getUUID());
      call.enqueue(new Callback<PlexResponse>() {
        @Override
        public void onResponse(Response<PlexResponse> response) {
          if(response.body() != null)
            Logger.d("setStream response: %s", response.body().status);
        }

        @Override
        public void onFailure(Throwable t) {

        }
      });

    }
  }
}
