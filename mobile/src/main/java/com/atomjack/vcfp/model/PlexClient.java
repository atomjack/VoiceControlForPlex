package com.atomjack.vcfp.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.atomjack.shared.Logger;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.Utils;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpResponseHandler;
import com.google.android.gms.cast.CastDevice;

import org.simpleframework.xml.Root;

import java.util.ArrayList;
import java.util.Arrays;

import retrofit.Call;
import retrofit.Callback;
import retrofit.Response;
import retrofit.Retrofit;

@Root(name="Server", strict=false)
public class PlexClient extends PlexDevice {
	public boolean isCastClient = false;
//  public List<MediaRouter.RouteInfo> castRoutes;
  public CastDevice castDevice;
	public boolean isAudioOnly = false;
  public boolean isLocalClient = false;

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

  public static PlexClient getLocalPlaybackClient() {
    PlexClient client = new PlexClient();
    client.isLocalClient = true;
    client.name = VoiceControlForPlexApplication.getInstance().getString(R.string.this_device);
    client.product = VoiceControlForPlexApplication.getInstance().getString(R.string.app_name);
    client.machineIdentifier = VoiceControlForPlexApplication.getInstance().getUUID();
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
    parcel.writeInt(isLocalClient ? 1 : 0);
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
    isLocalClient = in.readInt() == 1;
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

  public void pause(String mediaType, PlexHttpResponseHandler responseHandler) {
    adjustPlayback("pause", mediaType, responseHandler);
  }

  public void stop(String mediaType, PlexHttpResponseHandler responseHandler) {
    adjustPlayback("stop", mediaType, responseHandler);
  }

  public void play(String mediaType, PlexHttpResponseHandler responseHandler) {
    adjustPlayback("play", mediaType, responseHandler);
  }

  public void next(String mediaType, PlexHttpResponseHandler responseHandler) {
    adjustPlayback("skipNext", mediaType, responseHandler);
  }

  public void previous(String mediaType, PlexHttpResponseHandler responseHandler) {
    adjustPlayback("skipPrevious", mediaType, responseHandler);
  }

	// asynchronous
	private void adjustPlayback(String which, String mediaType, PlexHttpResponseHandler responseHandler) {
		ArrayList<String> validModes = new ArrayList<String>(Arrays.asList("pause", "play", "stop", "skipNext", "skipPrevious"));
		if(validModes.indexOf(which) == -1)
			return;
		try {
			PlexHttpClient.getDebug(String.format("http://%s:%s", address, port), String.format("player/playback/%s?commandID=0&type=%s", which, mediaType), responseHandler);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

  private void adjustPlayback(String which) {
		Logger.d("Adjusting playback with %s", which);
    try {
      PlexHttpClient.PlexHttpService service = PlexHttpClient.getService(String.format("http://%s:%s", address, port));
      Logger.d("Seeking with uuid %s", VoiceControlForPlexApplication.getInstance().getUUID());
      Call<PlexResponse> call = service.adjustPlayback(which, "0", VoiceControlForPlexApplication.getInstance().getUUID());
      call.enqueue(new Callback<PlexResponse>() {
        @Override
        public void onResponse(Response<PlexResponse> response, Retrofit retrofit) {

        }

        @Override
        public void onFailure(Throwable t) {

        }
      });
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void pause() {
    adjustPlayback("pause");
  }

  public void stop() {
    adjustPlayback("stop");
  }

  public void play() {
    adjustPlayback("play");
  }

  public void seekTo(int offset) {
    try {
      PlexHttpClient.PlexHttpService service = PlexHttpClient.getService(String.format("http://%s:%s", address, port));
			Logger.d("Seeking with uuid %s", VoiceControlForPlexApplication.getInstance().getUUID());
      Call<PlexResponse> call = service.seekTo(offset, "0", VoiceControlForPlexApplication.getInstance().getUUID());
      call.enqueue(new Callback<PlexResponse>() {
        @Override
        public void onResponse(Response<PlexResponse> response, Retrofit retrofit) {

        }

        @Override
        public void onFailure(Throwable t) {

        }
      });
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public boolean isLocalDevice() {
    String localip = Utils.getIPAddress(true);
    return localip.equals(address);
  }

	public String toString() {
		String output = String.format("Name: %s", name);

		return output;
	}
}
