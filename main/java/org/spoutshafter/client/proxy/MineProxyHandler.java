package org.spoutshafter.client.proxy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.regex.Matcher;
import org.spoutshafter.client.util.Streams;
import org.spoutshafter.client.util.SimpleRequest;

public class MineProxyHandler extends Thread {
	private DataInputStream fromClient;
	private DataOutputStream toClient;
	private Socket connection;
	
	private MineProxy proxy;
	
	private static String[] BLACKLISTED_HEADERS = new String[]{"Connection", "Proxy-Connection", "Transfer-Encoding"};
	
	public MineProxyHandler(MineProxy proxy, Socket conn) throws IOException {
		setName("MineProxyHandler Thread");
		
		this.proxy = proxy;
		
		connection = conn;
		fromClient = new DataInputStream(conn.getInputStream());
		toClient = new DataOutputStream(conn.getOutputStream());
	}
	
	public void run() {
		HashMap<String, String> headers = new HashMap<String, String>();

	    String[] requestLine = readUntil(fromClient, '\n').split(" ");
	    String method = requestLine[0].trim().toUpperCase();
	    String url = requestLine[1].trim();
	    
	 	System.out.println("Request: " + method + " " + url);
	 	
	 	String header;
		do {
			header = readUntil(fromClient, '\n').trim();
			System.out.println("H: " + header + ", " + header.length());
			int splitPoint = header.indexOf(':');
			if (splitPoint != -1) {
				headers.put(header.substring(0, splitPoint).toLowerCase()
						.trim(), header.substring(splitPoint + 1).trim());
			}

		} while (header.length() > 0);
		
		// run matchers
		Matcher skinMatcher = MineProxy.SKIN_URL.matcher(url);
		Matcher cloakMatcher = MineProxy.CLOAK_URL.matcher(url);
		Matcher getversionMatcher = MineProxy.GETVERSION_URL.matcher(url);
		Matcher joinserverMatcher = MineProxy.JOINSERVER_URL.matcher(url);
		Matcher checkserverMatcher = MineProxy.CHECKSERVER_URL.matcher(url);
		Matcher audiofix_url = MineProxy.AUDIOFIX_URL.matcher(url);
		Matcher dl_bukkit = MineProxy.DL_BUKKIT.matcher(url);
		Matcher client_snoop = MineProxy.CLIENT_SNOOP.matcher(url);
		Matcher server_snoop = MineProxy.SERVER_SNOOP.matcher(url);
		Matcher launcherSkinMatcher = MineProxy.LAUNCHER_SKIN_URL.matcher(url);
		Matcher dl_spout = MineProxy.DL_SPOUT.matcher(url);
		
		
		byte[] data = null;
		String contentType = null;
		String params;
		
		// If Skin Request
		if(skinMatcher.matches()) {
			System.out.println("Skin");
			
			String username = skinMatcher.group(1);
			if(proxy.skinCache.containsKey(username)) { // Is the skin in the cache?
				System.out.println("Skin from cache");
				
				data = proxy.skinCache.get(username);  // Then get it from there
			} else {
				// Change this later due to the skin issues
				url = "http://mineshafter.appspot.com/skin/" + username + ".png";
				
				System.out.println("To: " + url);
				
				data = getRequest(url); // Then get it...
				//System.out.println("Response length: " + data.length); // Not testing. Not needed.
				
				proxy.skinCache.put(username, data); // And put it in there
			}
			
		} 
		// If Cloak Request
		else if(cloakMatcher.matches()) {
			System.out.println("Cloak");
			
			String username = cloakMatcher.group(1);
			if(proxy.cloakCache.containsKey(username)) {
				System.out.println("Cloak from cache");
				data = proxy.cloakCache.get(username);
			} else {
				// Change this later due to the capes issues
				url = "http://mineshafter.appspot.com/cloak/get.jsp?user=" + username;

				
				System.out.println("To: " + url);
				
				data = getRequest(url);
				//System.out.println("Response length: " + data.length); // Not testing. Not needed.
				
				proxy.cloakCache.put(username, data);
			}
			
		} 
		// If Version Request
		else if(getversionMatcher.matches()) {
			System.out.println("GetVersion");
			
			String updateInfo = new String(SimpleRequest.get("http://www.mineshaftersquared.com/update/client/0"));
			String[] updateInfoArray = updateInfo.split(":");
			
			url = "http://mineshafter.appspot.com/game/getversion.jsp?proxy=" + updateInfoArray[0];
			//System.out.println("http://mineshafter.appspot.com/game/getversion.jsp?proxy=" + updateInfoArray[0]);
			System.out.println("To: " + url);
			
			try {
				int postlen = Integer.parseInt(headers.get("content-length"));
				char[] postdata = new char[postlen];
				InputStreamReader reader = new InputStreamReader(fromClient);
				reader.read(postdata);
				
				data = postRequest(url, new String(postdata), "application/x-www-form-urlencoded");
				
			} catch(IOException e) {
				System.out.println("Unable to read POST data from getversion request");
				
				e.printStackTrace();
			}
			
		} 
		// If JoinServer Request
		else if(joinserverMatcher.matches()) {
			System.out.println("JoinServer");
			
			params = joinserverMatcher.group(1);
			//url = "http://" + MineProxy.authServer + "/game/joinserver.php" + params;
			url = "http://mineshafter.appspot.com/game/joinserver.jsp" + params;
			System.out.println("To: " + url);
			data = getRequest(url);
			contentType = "text/plain";
			// TODO There may be a bug here, keeps causing a hang in the MC thread that tries to read the data from it
		}
		// If Check Server Request
		else if(checkserverMatcher.matches()) {
			System.out.println("CheckServer");
			
			params = checkserverMatcher.group(1);
			//url = "http://" + MineProxy.authServer + "/game/checkserver.php" + params;
			url = "http://mineshafter.appspot.com/game/checkserver.jsp" + params;
			System.out.println("To: " + url);
			data = getRequest(url);
			
		} else if(audiofix_url.matches()) {  // this is to fix the audio problems
			System.out.println("Audio Fix");
			url = "http://s3.amazonaws.com/MinecraftResources/";
			System.out.println("To: " + url);
			data = getRequest(url);
		} else if(dl_bukkit.matches()) {
			System.out.println("Bukkit Fix");
			data = getRequest(url);
		} else if(client_snoop.matches()) // tmp for now since else does not seem to handle these dont have time to look into it
		{
			params = client_snoop.group(1);
			url = "http://snoop\\.minecraft\\.net/client"+params;
			
			System.out.println("To: " + url);
			
			try {
				int postlen = Integer.parseInt(headers.get("content-length"));
				char[] postdata = new char[postlen];
				InputStreamReader reader = new InputStreamReader(fromClient);
				reader.read(postdata);
				
				data = postRequest(url, new String(postdata), "application/x-www-form-urlencoded");
				
			} catch(IOException e) {
				System.out.println("Unable to read POST data from getversion request");
				
				e.printStackTrace();
			}
		} else if(server_snoop.matches())
		{
			params = server_snoop.group(1);
			url = "http://snoop\\.minecraft\\.net/server"+params;
			
			System.out.println("To: " + url);
			
			try {
				int postlen = Integer.parseInt(headers.get("content-length"));
				char[] postdata = new char[postlen];
				InputStreamReader reader = new InputStreamReader(fromClient);
				reader.read(postdata);
				
				data = postRequest(url, new String(postdata), "application/x-www-form-urlencoded");
				
			} catch(IOException e) {
				System.out.println("Unable to read POST data from getversion request");
				
				e.printStackTrace();
			}
		} else if(dl_spout.matches())
		{
			data = getRequest(url);
		} else if(launcherSkinMatcher.matches())
		{
			String username = launcherSkinMatcher.group(1);
			url = "http://mineshafter.appspot.com/game/getskin/" + username + ".png";
			System.out.println("To: " + url);
			
			data = getRequest(url); // Then get it...
			System.out.println("Response length: " + data.length);
		}
		// If Any other network request
		else {
			System.out.println("No handler. Piping.");
			
			try {
				if(!url.startsWith("http://") && !url.startsWith("https://")) {
					url = "http://" + url;
				}
				URL u = new URL(url);
				if(method.equals("CONNECT")) {
					int port = u.getPort();
					if(port == -1) port = 80;
					Socket sock = new Socket(u.getHost(), port);
					
					Streams.pipeStreamsActive(sock.getInputStream(), toClient);
					Streams.pipeStreamsActive(connection.getInputStream(), sock.getOutputStream());
					// TODO Maybe put POST here instead, less to do, but would it work?
					
				} else if(method.equals("GET")  || method.equals("POST")) {
					HttpURLConnection c = (HttpURLConnection) u.openConnection(Proxy.NO_PROXY);
					c.setRequestMethod(method);
					boolean post = method.equals("POST");
					
					for(String k : headers.keySet()) {
						c.setRequestProperty(k, headers.get(k)); // TODO Might need to blacklist these as well later
					}
					
					if (post) {
						c.setDoInput(true);
						c.setDoOutput(true);
						c.setUseCaches(false);
						c.connect();
						int postlen = Integer.parseInt(headers.get("content-length"));
						byte[] postdata = new byte[postlen];
						fromClient.read(postdata);
						DataOutputStream os = new DataOutputStream(c.getOutputStream());
						os.write(postdata);
					}
					
					int responseCode = c.getResponseCode();
					String res = "HTTP/1.0 " + responseCode + " " + c.getResponseMessage() + "\r\n";
					res += "Connection: close\r\nProxy-Connection: close\r\n";
					
					java.util.Map<String, java.util.List<String>> h = c.getHeaderFields();
					headerloop:
					for(String k : h.keySet()) {
						if(k == null) continue;
						
						k = k.trim();
						
						for(String forbiddenHeader : BLACKLISTED_HEADERS)
						{
							if(k.equalsIgnoreCase(forbiddenHeader)) 
								continue headerloop;
						}
						
						java.util.List<String> vals = h.get(k);
						for(String v : vals) 
						{
							res += k + ": " + v + "\r\n";
						}
					}
					res += "\r\n";
					
					//System.out.println(res);
					
					int size = 0;
					if (responseCode / 100 != 5) {
						toClient.writeBytes(res);
						size = Streams.pipeStreams(c.getInputStream(), toClient);
					}
					
					toClient.close();
					connection.close();
					
					System.out.println("Piping finished, data size: " + size);
					
				} else if (method.equals("HEAD")) {
					HttpURLConnection c = (HttpURLConnection) u.openConnection(Proxy.NO_PROXY);
					c.setRequestMethod("HEAD");
					
					for (String k : headers.keySet()){
						c.setRequestProperty(k, headers.get(k));
					}
					
					String res = "HTTP/1.0 " + c.getResponseCode() + " " + c.getResponseMessage() + "\r\n";
					res += "Proxy-Connection: close\r\n";
					
					java.util.Map<String, java.util.List<String>> h = c.getHeaderFields();
					for (String k : h.keySet()) {
						if(k == null) continue;
						java.util.List<String> vals = h.get(k);
						for(String v : vals) {
							res += k + ": " + v + "\r\n";
						}
					}
					res += "\r\n";
					
					//System.out.println(res);
					
					toClient.writeBytes(res); // TODO Occasional exception socket write error
					toClient.close();
					connection.close();
				} else {
					System.out.println("UNEXPECTED REQUEST TYPE: " + method);
				}
				
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			return;
		}
		
		try {
			if (data != null) {
				this.toClient.writeBytes("HTTP/1.0 200 OK\r\nConnection: close\r\nProxy-Connection: close\r\nContent-Length: " + data.length + "\r\n");
				
				if (contentType != null) {
					toClient.writeBytes("Content-Type: " + contentType + "\r\n");
				}

				toClient.writeBytes("\r\n");
				toClient.write(data);
				toClient.flush();
			}
			this.toClient.close();
			this.connection.close();
			fromClient.close();
			toClient.close();
			connection.close();
			//System.out.println(data.length);
			//System.out.println(new String(data));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static byte[] getRequest(String url) {
		try {
			HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(Proxy.NO_PROXY);
			// People under estimate Linux. Without it, this world will be one whole virus.
			// Popularity boost ;)
			c.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:13.0) Gecko/20100101 Firefox/13.0.1");
			int code = c.getResponseCode();
			System.out.println("Response: " + code);
			if(code / 100 == 4) {
				return new byte[0];
			}
			
			BufferedInputStream in = new BufferedInputStream(c.getInputStream());
			
			return grabData(in);
			
		} catch (MalformedURLException e) {
			System.out.println("Bad URL in getRequest:");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("IO error during a getRequest:");
			e.printStackTrace();
		}
		
		return new byte[0];
	}
	
	public static byte[] postRequest(String url, String postdata, String contentType) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		OutputStreamWriter writer = new OutputStreamWriter(out);
		//System.out.println("Postdata: " + postdata);
		
		try {
			writer.write(postdata);
			writer.flush();
		} catch(IOException e) {
			e.printStackTrace();
		}
		
		byte[] rd = postRequest(url, out.toByteArray(), contentType);
		
		return rd;
	}
	
	public static byte[] postRequest(String url, byte[] postdata, String contentType) {
		try {
			URL u = new URL(url);
			
			HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(Proxy.NO_PROXY);
			c.setDoOutput(true);
			c.setRequestMethod("POST");
			
			//System.out.println("Postdata_bytes: " + new String(postdata));
			
			c.setRequestProperty("Host", u.getHost());
			c.setRequestProperty("Content-Length", Integer.toString(postdata.length));
			c.setRequestProperty("Content-Type", contentType);
			
			BufferedOutputStream out = new BufferedOutputStream(c.getOutputStream());
			out.write(postdata);
			out.flush();
			out.close();
			
			byte[] data = grabData(new BufferedInputStream(c.getInputStream()));
			return data;
			
		} catch(java.net.UnknownHostException e) {
			System.out.println("Unable to resolve remote host, returning null");
		} catch (MalformedURLException e) {
			System.out.println("Bad URL when doing postRequest:");
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	public static byte[] grabData(InputStream in) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		
		while(true) {
			int len;
			try {
				len = in.read(buffer);
				if(len == -1) break;
			} catch(IOException e) {
				break;
			}
			out.write(buffer, 0, len);
		}
		
		return out.toByteArray();
	}
	

	public static String readUntil(DataInputStream is, String endSequence) {
		return readUntil(is, endSequence.getBytes());
	}

	public static String readUntil(DataInputStream is, char endSequence) {
		return readUntil(is, new byte[] { (byte) endSequence });
	}

	public static String readUntil(DataInputStream is, byte endSequence) {
		return readUntil(is, new byte[] { endSequence });
	}

	public static String readUntil(DataInputStream is, byte[] endSequence) { // If there is an edge case, make sure we can see it
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		String r = null;

		try {
			int i = 0;
			
			while (true) {
				boolean end = false;
				byte b = is.readByte(); // Read a byte
				if (b == endSequence[i]) { // If equal to current byte of endSequence
					if (i == endSequence.length - 1) {
						end = true; // If we hit the end of endSequence, we're done
					}
					
					i++; // Increment for next round
				} else {
					i = 0; // Reset
				}

				out.write(b);
				if (end)
					break;
			}
		} catch (IOException ex) {
			System.out.println("readUntil unable to read from InputStream, endSeq: " + new String(endSequence));
			ex.printStackTrace();
		}

		try {
			r = out.toString("UTF-8");
		} catch (java.io.UnsupportedEncodingException ex) {
			System.out.println("readUntil unable to encode data: " + out.toString());
			ex.printStackTrace();
		}
		
		return r;
	}
}
