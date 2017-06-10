package de.htw.ds.tcpswitch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.sun.net.httpserver.HttpServer;
import de.sb.toolbox.Copyright;
import de.sb.toolbox.net.HttpFileHandler;


/**
 * HTTP server app-entry facade serving both external and embedded file content.
 */
@Copyright(year=2014, holders="Sascha Baumeister")
public final class HttpServers {

	/**
	 * Prevents external instantiation.
	 */
	private HttpServers () {}


	/**
	 * Application entry point. The given argument is expected to be a service port and a resource
	 * directory path. Example usage: 8001 resources/self-html
	 * @param args the runtime arguments
	 * @throws IllegalArgumentException if the given port is not a valid port number, or if the
	 *         given directory is not a directory
	 * @throws IOException if there is an I/O related problem
	 * 
	 */
	static public void main (final String[] args) throws IllegalArgumentException, IOException {
		final InetSocketAddress serviceAddress = new InetSocketAddress(Integer.parseInt(args[0]));
		final Path resourceDirectory = Paths.get(args[1]).toAbsolutePath();
		System.out.println(args[1]);
		System.out.println(resourceDirectory);
		if (!Files.isDirectory(resourceDirectory)) throw new IllegalArgumentException();

		final HttpServer server = HttpServer.create(serviceAddress, 0);
		final HttpFileHandler internalFileHandler = HttpFileHandler.newInstance("/internal");
		
		final HttpFileHandler externalFileHandler = HttpFileHandler.newInstance("/external", resourceDirectory);
		System.out.println(externalFileHandler.getContextPath());
		server.createContext(internalFileHandler.getContextPath(), internalFileHandler);
		server.createContext(externalFileHandler.getContextPath(), externalFileHandler);
		server.start();
		try {
			System.out.format("HTTP server running on service address %s:%s, enter \"quit\" to stop.\n", serviceAddress.getHostName(), serviceAddress.getPort());
			System.out.format("Service path \"%s\" is configured for class loader access.\n", internalFileHandler.getContextPath());
			System.out.format("Service path \"%s\" is configured for file system access within \"%s\".\n", externalFileHandler.getContextPath(), resourceDirectory);
			final BufferedReader charSource = new BufferedReader(new InputStreamReader(System.in));
			while (!"quit".equals(charSource.readLine()));
		} finally {
			server.stop(0);
		}		
	}
}