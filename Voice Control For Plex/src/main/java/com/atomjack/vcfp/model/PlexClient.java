package com.atomjack.vcfp.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.v7.media.MediaRouter;

import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.net.PlexHttpClient;
import com.atomjack.vcfp.net.PlexHttpResponseHandler;
import com.google.android.gms.cast.CastDevice;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

import java.util.ArrayList;
import java.util.Arrays;

@Root(name="Server", strict=false)
public class PlexClient extends PlexDevice {
	public boolean isCastClient = false;
	public CastDevice castDevice;

	public PlexClient() {

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

	public void seekTo(int offset, PlexHttpResponseHandler responseHandler) {
		String url = String.format("http://%s:%s/player/playback/seekTo?offset=%s", address, port, offset);
		PlexHttpClient.get(url, responseHandler);
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

	private void adjustPlayback(String which, PlexHttpResponseHandler responseHandler) {
		ArrayList<String> validModes = new ArrayList<String>(Arrays.asList("pause", "play", "stop"));
		if(validModes.indexOf(which) == -1)
			return;
		try {
			String url = String.format("http://%s:%s/player/playback/%s", address, port, which);
			PlexHttpClient.get(url, responseHandler);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
