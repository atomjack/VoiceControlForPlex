package us.nineworlds.serenity;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atomjack.shared.Logger;
import com.atomjack.vcfp.activities.MainActivity;
import com.atomjack.vcfp.model.Connection;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.services.GDMService;
import com.atomjack.vcfp.services.PlexScannerService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GDMReceiver extends BroadcastReceiver {
  private boolean cancel = false;

  public static final String ACTION_CANCEL = ".GDMReceiver.ACTION_CANCEL";

	private ArrayList<PlexClient> clients = new ArrayList<PlexClient>();
  private ArrayList<PlexServer> servers = new ArrayList<>();

	@Override
	public void onReceive(Context context, Intent intent) {
		Logger.d("GDMReceiver onReceive: %s", intent.getAction());

    if (intent.getAction().equals(GDMService.MSG_RECEIVED)) {
			String message = intent.getStringExtra("data").trim();
			String ipAddress = intent.getStringExtra("ipaddress").substring(1);

			Logger.d("[GDMReceiver] message: %s", message);
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
					server.connections = new ArrayList<>();
					server.connections.add(connection);
          servers.add(server);
				} else if(responseMap.get("content-type").equals("plex/media-player")
								&& responseMap.get("protocol") != null
								&& responseMap.get("protocol").equals("plex")
								&& !responseMap.get("product").equals("Plex Web")) {
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

      String scanType = intent.getStringExtra(com.atomjack.shared.Intent.SCAN_TYPE);

        // Send the reply back to whichever class called for it.
      Class theClass = (Class) intent.getSerializableExtra(com.atomjack.shared.Intent.EXTRA_CLASS);
      Intent i = new Intent(context, theClass);

      Logger.d("Scantype: %s, class: %s", scanType, theClass);
      i.setAction(scanType.equals(com.atomjack.shared.Intent.SCAN_TYPE_SERVER) ? PlexScannerService.ACTION_SERVER_SCAN_FINISHED : PlexScannerService.ACTION_CLIENT_SCAN_FINISHED);

      i.putExtra(com.atomjack.shared.Intent.SHOWRESOURCE, intent.getBooleanExtra(com.atomjack.shared.Intent.SHOWRESOURCE, false));


      if (clients.size() > 0 && scanType.equals(com.atomjack.shared.Intent.SCAN_TYPE_CLIENT))
        i.putParcelableArrayListExtra(com.atomjack.shared.Intent.EXTRA_CLIENTS, clients);
      if(scanType.equals(com.atomjack.shared.Intent.SCAN_TYPE_SERVER))
        i.putParcelableArrayListExtra(com.atomjack.shared.Intent.EXTRA_SERVERS, servers);

      i.putExtra(com.atomjack.shared.Intent.SCAN_TYPE, intent.getStringExtra(com.atomjack.shared.Intent.SCAN_TYPE));
      i.putExtra(com.atomjack.shared.Intent.EXTRA_CONNECT_TO_CLIENT, intent.getBooleanExtra(com.atomjack.shared.Intent.EXTRA_CONNECT_TO_CLIENT, false));
      i.putExtra(com.atomjack.shared.Intent.EXTRA_CLASS, theClass);
      i.putExtra(com.atomjack.shared.Intent.EXTRA_SILENT, intent.getBooleanExtra(com.atomjack.shared.Intent.EXTRA_SILENT, false));

      i.addFlags(Intent.FLAG_FROM_BACKGROUND);
      i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      if (theClass.getSuperclass() == Service.class) {
        context.startService(i);
      } else if (theClass == MainActivity.class) {
        Logger.d("Sending to activity");
        context.startActivity(i);
      }
      // Clear the list of servers & clients so the next scan sends a reinitialized list.
      clients = new ArrayList<>();
      servers = new ArrayList<>();
		} else if(intent.getAction().equals(ACTION_CANCEL)) {
      Logger.d("[GDMReceiver] cancel");
      cancel = true;
      clients = new ArrayList<>();
      servers = new ArrayList<>();
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
				Logger.d("%s: %s", matcher.group(1).toLowerCase(), matcher.group(2));
        if(!responseMap.containsKey(matcher.group(1).toLowerCase()))
  				responseMap.put(matcher.group(1).toLowerCase(), matcher.group(2));
			}
		}
		return responseMap;
	}
}
