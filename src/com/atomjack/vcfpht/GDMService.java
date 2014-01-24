package com.atomjack.vcfpht;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class GDMService extends IntentService {
    public static final String MSG_RECEIVED = ".GDMService.MESSAGE_RECEIVED";
    public static final String SOCKET_CLOSED = ".GDMService.SOCKET_CLOSED";
    
    public GDMService() {
        super("GDMService");
    }
    @Override
    protected void onHandleIntent(Intent intent) {
  	try
		{
  	  String origin = intent.getStringExtra("ORIGIN") == null ? "" : intent.getStringExtra("ORIGIN");
  	  String queryText = intent.getStringExtra("queryText");
			DatagramSocket socket = new DatagramSocket(32420);
			socket.setBroadcast(true);
			String data = "M-SEARCH * HTTP/1.0";
			DatagramPacket packet = new DatagramPacket(data.getBytes(), data.length(), getBroadcastAddress(), 32414);
			socket.send(packet);
			Log.i("GDMService", "Search Packet Broadcasted");
			
			byte[] buf = new byte[256];
			packet = new DatagramPacket(buf, buf.length);
			socket.setSoTimeout(2000);
			boolean listening = true;
			while (listening)
			{
				try 
				{
					socket.receive(packet);
					String packetData = new String(packet.getData());
					if (packetData.contains("HTTP/1.0 200 OK"))
					{
						Log.i("GDMService", "PMS Packet Received");
						//Broadcast Received Packet
						Intent packetBroadcast = new Intent(GDMService.MSG_RECEIVED);
						packetBroadcast.putExtra("data", packetData);
						packetBroadcast.putExtra("ipaddress", packet.getAddress().toString());
						packetBroadcast.putExtra("ORIGIN", origin);
						LocalBroadcastManager.getInstance(this).sendBroadcast(packetBroadcast);
					}
				}
				catch (SocketTimeoutException e)
				{
					Log.w("GDMService", "Socket Timeout");
					socket.close();
					listening = false;
					Intent socketBroadcast = new Intent(GDMService.SOCKET_CLOSED);
					socketBroadcast.putExtra("ORIGIN", origin);
					if(queryText != null) {
						socketBroadcast.putExtra("queryText", queryText);
					}
					LocalBroadcastManager.getInstance(this).sendBroadcast(socketBroadcast);
				}
				
			}
		}
		catch (IOException e)
		{
			Log.e("GDMService", e.toString());
      e.printStackTrace();
		} 
    	
    }
    
    //Builds the broadcast address based on the local network
	protected InetAddress getBroadcastAddress() throws IOException 
	{
	    WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
	    DhcpInfo dhcp = wifi.getDhcpInfo();
	    // handle null somehow

	    int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
	    byte[] quads = new byte[4];
	    for (int k = 0; k < 4; k++)
	      quads[k] = (byte) (broadcast >> k * 8);
	    return InetAddress.getByAddress(quads);
	}
	
}
