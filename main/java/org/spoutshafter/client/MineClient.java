package org.spoutshafter.client;

import java.applet.Applet;
import sun.applet.*;
import java.awt.Frame;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.swing.JOptionPane;
import org.spoutshafter.client.proxy.MineProxy;
import org.spoutshafter.client.util.Resources;
import org.spoutshafter.client.util.SimpleRequest;
import org.spoutshafter.client.util.Streams;
import java.io.BufferedInputStream;
import java.security.MessageDigest;

@SuppressWarnings("restriction")
public class MineClient extends Applet {
	private static final long serialVersionUID = 1L;
	protected static float VERSION = 2.0f;
	
	protected static String launcherDownloadURL = "http://mineshafter.tr0l.it/version/Spoutcraft-Launcher.jar";
	protected static String currentVersionNumberURL = "http://mineshafter.tr0l.it/version/Spoutshafter-Squared";
	protected static String currentLauncherVersionMD5URL = "http://mineshafter.tr0l.it/version/Spoutshafter-Squared";
	protected static String normalLauncherFilename = "spoutcraft.jar";
	protected static String hackedLauncherFilename = "spoutcraft_modified.jar";
	protected static String MANIFEST_TEXT = "Manifest-Version: 1.0\nBuilt-By: jenkins\nBuild-Jdk: 1.6.0_32\nCreated-By: Apache Maven\nMain-Class: org.spoutcraft.launcher.Main\nSplashScreen-Image: org/spoutcraft/launcher/resources/splash.png\nArchiver-Version: Plexus Archiver\n";
	
	/* Added For MineshafterSquared */
	protected static String authServer = Resources.loadString("auth").trim();
	protected static String mineshaftersquaredPath;
	protected static String gamePath;
	protected static String versionPath;
	
	public void init() {
		MineClient.main(new String[0]);
	}
	
	public static void main(String[] args) {
		try {
			// Get Update Info
			String[] gamePaths = getGameFilePaths(); // test
			gamePath = gamePaths[0];
			versionPath = gamePaths[1];
			mineshaftersquaredPath = gamePaths[2];
			
			// check to make sure mineshaftersquaredPath exists if not create it
			File msFilePath = new File(mineshaftersquaredPath);
			if(!msFilePath.exists())
			{
				msFilePath.mkdir();
			}
			
			// set minecraft downloads to Mineshafter Squared dir
			MineClient.normalLauncherFilename = mineshaftersquaredPath + MineClient.normalLauncherFilename;
			MineClient.hackedLauncherFilename = mineshaftersquaredPath + MineClient.hackedLauncherFilename;
			
			// updateInfo string for use with the open mineshaftersquared auth server is "http://" + authServer + "/update.php?name=client&build=" + buildNumber
			String updateInfo = new String(SimpleRequest.get(currentVersionNumberURL));
			String launcherMD5 = new String(SimpleRequest.get(currentLauncherVersionMD5URL));
			
			// make sure updateInfo is 0 if it is empty
			if(updateInfo.isEmpty()) {
				updateInfo = "0";
			}
			
			// parse out updateInfo into an integer
			float version;
			try {
				version = Float.parseFloat(updateInfo);
			} 
			catch(Exception e) {
				version = 0;
			}
			
			// Print Proxy Version Numbers to Console
			System.out.println("Current proxy version: " + VERSION);
			System.out.println("Gotten proxy version: " + version);
			
			if(VERSION < version) {
				JOptionPane.showMessageDialog(null, "A new version of Spoutshafter Squared is available at http://mineshafter.tr0l.it\nGo get it.", "Update Available", JOptionPane.PLAIN_MESSAGE);
				System.exit(0);
			}
			
			MessageDigest md5 = MessageDigest.getInstance("MD5");
			BufferedInputStream file = new BufferedInputStream(new FileInputStream(normalLauncherFilename));
			if(new File(normalLauncherFilename).exists()) {
				int theByte = 0;
				while((theByte = file.read()) != -1) {
					md5.update((byte)theByte);
				}
				file.close();
				
				byte[] theDigest = md5.digest();
				StringBuffer sb = new StringBuffer();
				for(byte b:theDigest) {
					sb.append(Integer.toHexString((int)(b & 0xff)));
				}
				
				if(!sb.toString().equals(launcherMD5)) {
					new File(normalLauncherFilename).delete();
				}
			}
            
		} 
		catch(Exception e) {
			// if errors
			System.out.println("Error while updating:");
			e.printStackTrace();
			/* System.exit(1); */
		}
		
		try {
			MineProxy proxy = new MineProxy(VERSION, authServer); // create proxy - authServer
			proxy.start(); // launch proxy
			int proxyPort = proxy.getPort();
			
			System.setProperty("http.proxyHost", "127.0.0.1");
			System.setProperty("http.proxyPort", Integer.toString(proxyPort));
			
			//System.setProperty("https.proxyHost", "127.0.0.1");
			//System.setProperty("https.proxyPort", Integer.toString(proxyPort));
			
			// Make sure we have a fresh launcher every time
			File hackedFile = new File(hackedLauncherFilename);
			if(hackedFile.exists()){ 
				hackedFile.delete();
			}
			
			// start the game launcher
			startLauncher(args);
			
		} catch(Exception e) {
			System.out.println("Something bad happened:");
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	public static void startLauncher(String[] args) {
		try {
			// if hacked game exists
			if(new File(hackedLauncherFilename).exists()) {
				URL u = new File(hackedLauncherFilename).toURI().toURL();
				URLClassLoader cl = new URLClassLoader(new URL[]{u}, Main.class.getClassLoader());
				
				@SuppressWarnings("unchecked")
				Class<Frame> launcherFrame = (Class<Frame>) cl.loadClass("org.spoutcraft.launcher.Main");
				
				String[] nargs;
				try{
					nargs = new String[args.length - 1];
					System.arraycopy(args, 1, nargs, 0, nargs.length); // Transfer the arguments from the process call so that the launcher gets them
				} catch(Exception e){
					nargs = new String[0];
				}
				
				Method main = launcherFrame.getMethod("main", new Class[]{ String[].class });
				main.invoke(launcherFrame, new Object[]{ nargs });
			}
			// if the normal game exists
			else if(new File(normalLauncherFilename).exists()) {
				editLauncher();
				startLauncher(args);
			}
			// 
			else {
				try{
					byte[] data = SimpleRequest.get(launcherDownloadURL);
					OutputStream out = new FileOutputStream(normalLauncherFilename);
					out.write(data);
					out.flush();
					out.close();
					startLauncher(args);
					
				} catch(Exception ex) {
					System.out.println("Error downloading launcher:");
					ex.printStackTrace();
					return;
				}
			}
		} catch(Exception e1) {
			System.out.println("Error starting launcher:");
			e1.printStackTrace();
		}
	}
	
	public static void editLauncher() {
		try {
			ZipInputStream in = new ZipInputStream(new FileInputStream(normalLauncherFilename));
			ZipOutputStream out = new ZipOutputStream(new FileOutputStream(hackedLauncherFilename));
			ZipEntry entry;
			String n;
			InputStream dataSource;
			while((entry = in.getNextEntry()) != null) {
				n = entry.getName();
				if(n.contains(".svn")) continue;
				out.putNextEntry(entry);
				if(n.equals("META-INF/MANIFEST.MF")) dataSource = new ByteArrayInputStream(MANIFEST_TEXT.getBytes());
				else if(n.equals("org/spoutcraft/launcher/api/util/Utils.class")) dataSource = Resources.load("Utils.class");
				else if(n.equals("org/spoutcraft/launcher/skin/LegacyLoginFrame.class")) dataSource = Resources.load("LegacyLoginFrame.class");
				else if(n.equals("org/spoutcraft/launcher/StartupParameters.class")) dataSource = Resources.load("StartupParameters.class");
				else dataSource = in;
				Streams.pipeStreams(dataSource, out);
				out.flush();
			}
			in.close();
			out.close();
		} catch(Exception e) {
			System.out.println("Editing launcher failed:");
			e.printStackTrace();
		}
	}
	
	private static String[] getGameFilePaths(){
		String[] paths = new String[3];
		
		String os = System.getProperty("os.name").toLowerCase();
        Map<String, String> enviornment = System.getenv();
        String basePath;
        if (os.contains("windows")) {
        	basePath = enviornment.get("APPDATA");
            paths[0] = basePath + "\\.minecraft\\bin";
            paths[1] = paths[0] + "\\version";
            paths[2] = basePath + "\\.spoutshaftersquared\\";
        } else if (os.contains("mac")) {
        	basePath = "/Users/" + enviornment.get("USER") + "/Library/Application Support";
        	paths[0] = basePath + "/minecraft/bin";
        	paths[1] = paths[0] + "/version";
        	paths[2] = basePath + "/spoutshaftersquared/";
        } else if(os.contains("linux")){
        	basePath = enviornment.get("HOME");
        	paths[0] = basePath+ "/.minecraft/bin";
        	paths[1] = paths[0] + "/version";
        	paths[2] = basePath + "/.spoutshaftersquared/";
        }
        
        return paths;
	}
}