package com.atomjack.vcfp.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.support.v4.content.LocalBroadcastManager;

import com.atomjack.shared.Logger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;

public class GDMService extends IntentService {
    public static final String MSG_RECEIVED = ".GDMService.MESSAGE_RECEIVED";
    public static final String SOCKET_CLOSED = ".GDMService.SOCKET_CLOSED";
    public static final String PORT = ".GDMService.PORT";

    public GDMService() {
        super("GDMService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
			try
			{
        int port = intent.getIntExtra(PORT, 32414); // Default port, for Plex Media Servers (Clients use 32412)
        DatagramSocket socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.setBroadcast(true);
        socket.bind(new InetSocketAddress(0));
        String data = "M-SEARCH * HTTP/1.1";
        DatagramPacket packet = new DatagramPacket(data.getBytes(), data.length(), getBroadcastAddress(), port);
        socket.send(packet);
        Logger.i("Search Packet Broadcasted");

        byte[] buf = new byte[8096];
        packet = new DatagramPacket(buf, buf.length);
        socket.setSoTimeout(2000);
        boolean listening = true;
        while (listening) {
          try {
            socket.receive(packet);
            String packetData = new String(packet.getData());
            if (packetData.startsWith("HTTP/1.0 200 OK") ||
                    packetData.startsWith("HELLO * HTTP/1.0")) // A version of the Roku is known to send this invalid response.
            {
              Logger.i("PMS Packet Received");
              //Broadcast Received Packet
              Intent packetBroadcast = new Intent(GDMService.MSG_RECEIVED);
              packetBroadcast.putExtra("data", packetData);
              packetBroadcast.putExtra("ipaddress", packet.getAddress().toString());
              packetBroadcast.putExtra(com.atomjack.shared.Intent.EXTRA_CLASS, intent.getSerializableExtra(com.atomjack.shared.Intent.EXTRA_CLASS));
              LocalBroadcastManager.getInstance(this).sendBroadcast(packetBroadcast);
            }
          } catch (SocketTimeoutException e) {
            Logger.w("Socket Timeout");
            socket.close();
            listening = false;
            Intent socketBroadcast = new Intent(GDMService.SOCKET_CLOSED);
            socketBroadcast.putExtra(com.atomjack.shared.Intent.EXTRA_SILENT, intent.getBooleanExtra(com.atomjack.shared.Intent.EXTRA_SILENT, false));
            socketBroadcast.putExtra(com.atomjack.shared.Intent.EXTRA_CLASS, intent.getSerializableExtra(com.atomjack.shared.Intent.EXTRA_CLASS));
            socketBroadcast.putExtra(com.atomjack.shared.Intent.SCAN_TYPE, intent.getStringExtra(com.atomjack.shared.Intent.SCAN_TYPE));
            socketBroadcast.putExtra(com.atomjack.shared.Intent.EXTRA_CONNECT_TO_CLIENT, intent.getBooleanExtra(com.atomjack.shared.Intent.EXTRA_CONNECT_TO_CLIENT, false));
            LocalBroadcastManager.getInstance(this).sendBroadcast(socketBroadcast);
          }
        }
			}
			catch (IOException e)
			{
				Logger.e(e.toString());
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
