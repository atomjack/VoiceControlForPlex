package com.atomjack.vcfp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class Utils {

  private static SecureRandom random = new SecureRandom();
	/**
	 * Convert byte array to hex string
	 * @param bytes
	 * @return
	 */
	public static String bytesToHex(byte[] bytes) {
		StringBuilder sbuf = new StringBuilder();
		for(int idx=0; idx < bytes.length; idx++) {
			int intVal = bytes[idx] & 0xff;
			if (intVal < 0x10) sbuf.append("0");
			sbuf.append(Integer.toHexString(intVal).toUpperCase());
		}
		return sbuf.toString();
	}

	/**
	 * Get utf8 byte array.
	 * @param str
	 * @return  array of NULL if error was found
	 */
	public static byte[] getUTF8Bytes(String str) {
		try { return str.getBytes("UTF-8"); } catch (Exception ex) { return null; }
	}

	/**
	 * Load UTF8withBOM or any ansi text file.
	 * @param filename
	 * @return
	 * @throws java.io.IOException
	 */
	public static String loadFileAsString(String filename) throws java.io.IOException {
		final int BUFLEN=1024;
		BufferedInputStream is = new BufferedInputStream(new FileInputStream(filename), BUFLEN);
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream(BUFLEN);
			byte[] bytes = new byte[BUFLEN];
			boolean isUTF8=false;
			int read,count=0;
			while((read=is.read(bytes)) != -1) {
				if (count==0 && bytes[0]==(byte)0xEF && bytes[1]==(byte)0xBB && bytes[2]==(byte)0xBF ) {
					isUTF8=true;
					baos.write(bytes, 3, read-3); // drop UTF8 bom marker
				} else {
					baos.write(bytes, 0, read);
				}
				count+=read;
			}
			return isUTF8 ? new String(baos.toByteArray(), "UTF-8") : new String(baos.toByteArray());
		} finally {
			try{ is.close(); } catch(Exception ex){}
		}
	}

	/**
	 * Returns MAC address of the given interface name.
	 * @param interfaceName eth0, wlan0 or NULL=use first interface
	 * @return  mac address or empty string
	 */
	public static String getMACAddress(String interfaceName) {
		try {
			List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
			for (NetworkInterface intf : interfaces) {
				if (interfaceName != null) {
					if (!intf.getName().equalsIgnoreCase(interfaceName)) continue;
				}
				byte[] mac = intf.getHardwareAddress();
				if (mac==null) return "";
				StringBuilder buf = new StringBuilder();
				for (int idx=0; idx<mac.length; idx++)
					buf.append(String.format("%02X:", mac[idx]));
				if (buf.length()>0) buf.deleteCharAt(buf.length()-1);
				return buf.toString();
			}
		} catch (Exception ex) { } // for now eat exceptions
		return "";
        /*try {
            // this is so Linux hack
            return loadFileAsString("/sys/class/net/" +interfaceName + "/address").toUpperCase().trim();
        } catch (IOException ex) {
            return null;
        }*/
	}

	/**
	 * Get IP address from first non-localhost interface
	 * @param useIPv4  true=return ipv4, false=return ipv6
	 * @return  address or empty string
	 */
	public static String getIPAddress(boolean useIPv4) {
		try {
			List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
			for (NetworkInterface intf : interfaces) {
				List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
				for (InetAddress addr : addrs) {
					if (!addr.isLoopbackAddress()) {
						String sAddr = addr.getHostAddress().toUpperCase();
						boolean isIPv4 = addr instanceof Inet4Address;// InetAddressUtils.isIPv4Address(sAddr);
						if (useIPv4) {
							if (isIPv4)
								return sAddr;
						} else {
							if (!isIPv4) {
								int delim = sAddr.indexOf('%'); // drop ip6 port suffix
								return delim<0 ? sAddr : sAddr.substring(0, delim);
							}
						}
					}
				}
			}
		} catch (Exception ex) { } // for now eat exceptions
		return "";
	}

  public static String generateRandomString() {
    return generateRandomString(16);
  }

  public static String generateRandomString(int length) {
    return new BigInteger(130, random).toString(32).substring(0, length);
  }

  public static final String md5(final String s) {
    if(s == null)
      return "";
    final String MD5 = "MD5";
    try {
      // Create MD5 Hash
      MessageDigest digest = java.security.MessageDigest
              .getInstance(MD5);
      digest.update(s.getBytes());
      byte messageDigest[] = digest.digest();

      // Create Hex String
      StringBuilder hexString = new StringBuilder();
      for (byte aMessageDigest : messageDigest) {
        String h = Integer.toHexString(0xFF & aMessageDigest);
        while (h.length() < 2)
          h = "0" + h;
        hexString.append(h);
      }
      return hexString.toString();

    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
    return "";
  }

  /*
  *  Convenience method to add a specified number of minutes to a Date object
  *  From: http://stackoverflow.com/questions/9043981/how-to-add-minutes-to-my-date
  *  @param  minutes  The number of minutes to add
  *  @param  beforeTime  The time that will have minutes added to it
  *  @return  A date object with the specified number of minutes added to it
  */
  public static Date addMinutesToDate(int minutes, Date beforeTime){
    final long ONE_MINUTE_IN_MILLIS = 60000;//millisecs

    long curTimeInMs = beforeTime.getTime();
    Date afterAddingMins = new Date(curTimeInMs + (minutes * ONE_MINUTE_IN_MILLIS));
    return afterAddingMins;
  }

  // Return a resize bitmap, keeping aspect ratio, using provided max width and height.
  // If this algorithm is ever updated, make sure to update VoiceControlForPlex.currentImageCacheVersion
  // so that users' caches are cleared
  public static byte[] resizeImage(InputStream original, int maxWidth, int maxHeight) {
    Bitmap originalBitmap = BitmapFactory.decodeStream(original);
    double scale = determineImageScale(originalBitmap.getWidth(), originalBitmap.getHeight(), maxWidth, maxHeight);
    Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, (int)(scale*originalBitmap.getWidth()), (int)(scale*originalBitmap.getHeight()), true);
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
    byte[] bytes = stream.toByteArray();
    return bytes;
  }

  private static double determineImageScale(int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
    double scalex = (double) targetWidth / sourceWidth;
    double scaley = (double) targetHeight / sourceHeight;
    return Math.min(scalex, scaley);
  }
}