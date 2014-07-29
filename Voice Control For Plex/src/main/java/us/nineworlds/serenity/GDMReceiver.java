package us.nineworlds.serenity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atomjack.vcfp.GDMService;
import com.atomjack.vcfp.Logger;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.activities.VCFPActivity;
import com.atomjack.vcfp.model.Connection;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexServer;

public class GDMReceiver extends BroadcastReceiver {
  private boolean cancel = false;

  public static final String ACTION_CANCEL = ".GDMReceiver.ACTION_CANCEL";

	private ArrayList<PlexClient> clients = new ArrayList<PlexClient>();
	@Override
	public void onReceive(Context context, Intent intent) {
		Logger.d("GDMReceiver onReceive: %s", intent.getAction());

    if (intent.getAction().equals(GDMService.MSG_RECEIVED)) {
			String message = intent.getStringExtra("data").trim();
			String ipAddress = intent.getStringExtra("ipaddress").substring(1);

			Logger.d("message: %s", message);
			HashMap<String, String> responseMap = processResponse(message);

			if(responseMap.get("resource-identifier") != null) {
				if(responseMap.get("content-type").equals("plex/media-server")) {
					PlexServer server = new PlexServer();
					server.port = responseMap.get("port");
					server.name = responseMap.get("name");
					server.address = ipAddress;
					server.machineIdentifier = responseMap.get("resource-identifier");
					server.version = responseMap.get("version");
					server.local = true;
					Connection connection = new Connection("http", server.address, server.port);
					server.connections = new ArrayList<Connection>();
					server.connections.add(connection);
					VoiceControlForPlexApplication.addPlexServer(server);
				} else if(responseMap.get("content-type").equals("plex/media-player") && responseMap.get("protocol") != null && responseMap.get("protocol").equals("plex")) {
					PlexClient client = new PlexClient();
					client.port = responseMap.get("port");
					client.name = responseMap.get("name");
					client.address = ipAddress;
					client.machineIdentifier = responseMap.get("resource-identifier");
					client.version = responseMap.get("version");

					client.product = responseMap.get("product");
					clients.add(client);
				}
      }
		} else if (intent.getAction().equals(GDMService.SOCKET_CLOSED)) {
			Logger.i("Finished Searching");
      if(cancel) {
        cancel = false;
        Logger.d("[GDMReceiver] canceling");
        return;
      }
        // Send the reply back to whichever class called for it.
      Class theClass = (Class) intent.getSerializableExtra(VoiceControlForPlexApplication.Intent.EXTRA_CLASS);
      Intent i = new Intent(context, theClass);
      i.setAction(VoiceControlForPlexApplication.Intent.GDMRECEIVE);
      i.putExtra(VoiceControlForPlexApplication.Intent.SHOWRESOURCE, intent.getBooleanExtra(VoiceControlForPlexApplication.Intent.SHOWRESOURCE, false));

      String scanType = intent.getStringExtra(VoiceControlForPlexApplication.Intent.SCAN_TYPE);
      if (clients.size() > 0 && scanType.equals(VoiceControlForPlexApplication.Intent.SCAN_TYPE_CLIENT))
        i.putParcelableArrayListExtra(VoiceControlForPlexApplication.Intent.EXTRA_CLIENTS, clients);

      i.putExtra(VoiceControlForPlexApplication.Intent.SCAN_TYPE, intent.getStringExtra(VoiceControlForPlexApplication.Intent.SCAN_TYPE));
      i.putExtra(VoiceControlForPlexApplication.Intent.EXTRA_CONNECT_TO_CLIENT, intent.getBooleanExtra(VoiceControlForPlexApplication.Intent.EXTRA_CONNECT_TO_CLIENT, false));

      i.addFlags(Intent.FLAG_FROM_BACKGROUND);
      i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      if (theClass.getSuperclass() == Service.class) {
        context.startService(i);
      } else if (theClass.getSuperclass() == VCFPActivity.class) {
        Logger.d("Sending to activity");
        context.startActivity(i);
      }
		} else if(intent.getAction().equals(ACTION_CANCEL)) {
      Logger.d("[GDMReceiver] cancel");
      cancel = true;
    }
	}

	private HashMap<String, String> processResponse(String response) {
		HashMap<String, String> responseMap = new HashMap<String, String>();
		String[] lines = response.split("[\n\r]");

		Pattern p = Pattern.compile("([^:]+): ([^\r^\n]+)");
		Matcher matcher;
		for(String line : lines) {
			matcher = p.matcher(line);
			if(matcher.find()) {
				responseMap.put(matcher.group(1).toLowerCase(), matcher.group(2));
			}
		}
		return responseMap;
	}
}
