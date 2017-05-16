package de.htw.ds.ftp;

import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.sb.toolbox.Copyright;
import de.sb.toolbox.net.InetAddresses;


/**
 * This class implements a simple FTP client. It demonstrates the use of TCP connections, and the
 * Java Logging API. Note that this class is declared final because it provides an application entry
 * point, and therefore not supposed to be extended.
 */
@Copyright(year=2011, holders="Sascha Baumeister")
public final class FtpClient implements AutoCloseable {
	static private final Charset ASCII = Charset.forName("US-ASCII");
	private static final int BUFFER_SIZE=0x10000;

	private final InetSocketAddress serverAddress;
	private volatile Socket controlConnection;
	private volatile BufferedWriter controlConnectionSink;
	private volatile BufferedReader controlConnectionSource;


	/**
	 * Creates a new instance able to connect to the given FTP server address.
	 * @param serverAddress the TCP socket-address of an FTP server
	 * @throws IOException if there is an I/O related problem
	 */
	public FtpClient (final InetSocketAddress serverAddress) throws IOException {
		if (serverAddress == null) throw new NullPointerException();

		this.serverAddress = serverAddress;
	}


	/**
	 * Closes an FTP control connection.
	 * @throws IOException if there is an I/O related problem
	 */
	public synchronized void close () throws IOException {
		if (this.isClosed()) return;

		try {
			final FtpResponse response = this.sendRequest("QUIT");
			if (response.getCode() != 221) throw new ProtocolException(response.toString());
		} finally {
			try { this.controlConnection.close(); } catch (final IOException exception) {}

			this.controlConnection = null;
			this.controlConnectionSink = null;
			this.controlConnectionSource = null;
		}
	}


	/**
	 * Returns the server address used for TCP control connections.
	 * @return the server address
	 */
	public InetSocketAddress getServerAddress () {
		return this.serverAddress;
	}


	/**
	 * Returns whether or not this client is closed.
	 * @return {@code true} if this client is closed, {@code false} otherwise
	 */
	public boolean isClosed () {
		return this.controlConnection == null;
	}


	/**
	 * Opens the FTP control connection.
	 * @param alias the user-ID
	 * @param password the password
	 * @param binaryMode true for binary transmission, false for ASCII
	 * @throws IllegalStateException if this client is already open
	 * @throws SecurityException if the given alias or password is invalid
	 * @throws IOException if there is an I/O related problem
	 */
	public synchronized void open (final String alias, final String password, final boolean binaryMode) throws IOException {
		if (!this.isClosed()) throw new IllegalStateException();

		try {
			this.controlConnection = new Socket(this.serverAddress.getHostString(), this.serverAddress.getPort());
			this.controlConnectionSink = new BufferedWriter(new OutputStreamWriter(this.controlConnection.getOutputStream(), ASCII));
			this.controlConnectionSource = new BufferedReader(new InputStreamReader(this.controlConnection.getInputStream(), ASCII));

			FtpResponse response = this.receiveResponse();
			if (response.getCode() != 220) throw new ProtocolException(response.toString());

			response = this.sendRequest("USER " + (alias == null ? "guest" : alias));
			if (response.getCode() == 331) {
				response = this.sendRequest("PASS " + (password == null ? "" : password));
			}
			if (response.getCode() != 230) throw new SecurityException(response.toString());

			response = this.sendRequest("TYPE " + (binaryMode ? "I" : "A"));
			if (response.getCode() != 200) throw new ProtocolException(response.toString());
		} catch (final Exception exception) {
			try {
				this.close();
			} catch (final Exception nestedException) {
				exception.addSuppressed(nestedException);
			}
			throw exception;
		}
	}


	/**
	 * Stores the given file on the FTP client side using a separate data connection. Note that the
	 * source file resides on the server side and must therefore be a relative path (relative to the
	 * FTP server context directory), while the target directory resides on the client side and can
	 * be a global path.
	 * @param sourceFile the source file (server side)
	 * @param sinkDirectory the sink directory (client side)
	 * @throws NullPointerException if the target directory is {@code null}
	 * @throws IllegalStateException if this client is closed
	 * @throws NotDirectoryException if the source or target directory does not exist
	 * @throws NoSuchFileException if the source file does not exist
	 * @throws AccessDeniedException if the source file cannot be read, or the sink directory cannot
	 *         be written
	 * @throws IOException if there is an I/O related problem
	 */
	public synchronized void receiveFile (final Path sourceFile, final Path sinkDirectory) throws IOException {
		if (this.isClosed())
			throw new IllegalStateException();
		if (!Files.isDirectory(sinkDirectory))
			throw new NotDirectoryException(sinkDirectory.toString());
		FtpResponse response;

		Path outputFilePath = sinkDirectory.resolve(sourceFile.getFileName());
		try (OutputStream fos = Files.newOutputStream(outputFilePath)) {

			if (sourceFile.getParent() != null) {
				// CWD - change working directory. If parent is null- file is in root.
				response = this.sendRequest("CWD " + sourceFile.getParent());
				if (response.getCode() != 250)
					throw new NotDirectoryException(response.toString());
			}
			// PASV - Passive Mode,request new connection for data transfer
			response = this.sendRequest("PASV");
			if (response.getCode() != 227)
				throw new ProtocolException(response.toString());
			final InetSocketAddress newConnection = response.decodeDataPort();

			// Open a data connection with the given parameters
			try (Socket socket = new Socket(newConnection.getAddress(), newConnection.getPort())) {
				response = this.sendRequest("RETR " + sourceFile.getFileName());
				if (response.getCode() != 150)
					throw new ProtocolException(response.toString());
				
				try (InputStream is = socket.getInputStream()) {
					final byte[] buffer = new byte[BUFFER_SIZE];
					for (int bytesRead = is.read(buffer); bytesRead != -1; bytesRead = is.read(buffer)) {
						fos.write(buffer, 0, bytesRead);
					}
				}
			}
			response = this.receiveResponse();
			if (response.getCode() != 226)
				throw new ProtocolException(response.toString());
		}
	}



	/**
	 * Parses a single FTP response from the control connection. Note that some kinds of FTP
	 * requests will cause multiple FTP responses over time.
	 * @param request the FTP request
	 * @return an FTP response
	 * @throws IllegalStateException if this client is closed
	 * @throws IOException if there is an I/O related problem
	 */
	protected synchronized FtpResponse receiveResponse () throws IOException {
		if (this.isClosed()) throw new IllegalStateException();

		final FtpResponse response = FtpResponse.parse(this.controlConnectionSource);
		Logger.getGlobal().log(Level.INFO, response.toString());
		return response;
	}


	/**
	 * Stores the given file on the FTP server side using a separate data connection. Note that the
	 * source file resides on the client side and can therefore be a global path, while the target
	 * directory resides on the server side and must be a relative path (relative to the FTP server
	 * context directory), or {@code null}.
	 * @param sourceFile the source file (client side)
	 * @param sinkDirectory the sink directory (server side), may be empty
	 * @throws NullPointerException if the source file is {@code null}
	 * @throws IllegalStateException if this client is closed
	 * @throws NotDirectoryException if the sink directory does not exist
	 * @throws AccessDeniedException if the source file cannot be read, or the sink directory cannot
	 *         be written
	 * @throws IOException if there is an I/O related problem
	 */
	public synchronized void sendFile(final Path sourceFile, final Path sinkDirectory) throws IOException {
		if (this.isClosed())
			throw new IllegalStateException();
		if (!Files.isReadable(sourceFile))
			throw new NoSuchFileException(sourceFile.toString());

		// If the target directory is not null, issue a CWD message to the
		// FTP server
		// using sendRequest(), setting it's current working directory to the
		// target directory.

		if (sinkDirectory == null)
			throw new NotDirectoryException("target directory");

		// CWD stands for change working directory
		FtpResponse response = this.sendRequest("CWD " + sinkDirectory);

		// Send a PASV message to query the socket-address to be used for the
		// data transfer; ask
		// the response for the socket address returned using
		// FtpResponse#decodeDataPort().
		// PASV - Passive Mode
		response = this.sendRequest("PASV");

		InetSocketAddress newConnectionAddress = response.decodeDataPort();
		InetAddress address = newConnectionAddress.getAddress();
		int port = newConnectionAddress.getPort();

		// Open a data connection to the socket-address using "new Socket(host,
		// port)".
		try (Socket socket = new Socket(address, port)) {
			// Send a STOR message over the control connection.
			response = this.sendRequest("STOR " + sourceFile.getFileName());

			// After receiving the first part of its response (code 150),
			// transport the source file content to the data connection's
			// OUTPUT stream, closing it once there is no more data.
			if (response.getCode() != 150)
				throw new ProtocolException(response.toString());

			try (InputStream fis = Files.newInputStream(sourceFile)) {
				OutputStream outputStream = socket.getOutputStream();
				final byte[] buffer = new byte[BUFFER_SIZE];
				for (int bytesRead = fis.read(buffer); bytesRead != -1; bytesRead = fis.read(buffer)) {
					outputStream.write(buffer, 0, bytesRead);
				}
			}
		}
		// Then receive the second part of the STOR response (code 226)
		// using receiveResponse(). Make sure the source file
		// and the data connection are closed in any case.
		response = this.receiveResponse();
		if (response.getCode() != 226)
			throw new ProtocolException(response.toString());
	}

	/**
	 * Sends an FTP request and returns it's initial response. Note that some kinds of FTP requests
	 * (like {@code PORT} and {@code PASV}) will cause multiple FTP responses over time, therefore
	 * all but the first need to be received separately using {@link #receiveResponse()}.
	 * @param request the FTP request
	 * @return an FTP response
	 * @throws NullPointerException if the given request is {@code null}
	 * @throws IllegalStateException if this client is closed
	 * @throws IOException if there is an I/O related problem
	 */
	protected synchronized FtpResponse sendRequest (final String request) throws IOException {
		if (this.isClosed()) throw new IllegalStateException();

		Logger.getGlobal().log(Level.INFO, request.startsWith("PASS") ? "PASS xxxxxxxx" : request);
		this.controlConnectionSink.write(request);
		this.controlConnectionSink.newLine();
		this.controlConnectionSink.flush();

		return this.receiveResponse();
	}


	/**
	 * Application entry point. The given runtime parameters must be a server address, an alias, a
	 * password, a boolean indicating binary or ASCII transfer mode, STORE or RETRIEVE transfer
	 * direction, a source file path, and a target directory path.
	 * 
	 * Example usage: " 127.0.0.1:21 root password true RETRIEVE /myFile.txt /home/root/myDir/"
	 * 
	 * @param args the given runtime arguments
	 * @throws IOException if the given port is already in use
	 */
	static public void main (final String[] args) throws IOException {
		System.out.printf("Command line arguments: %s \n",Arrays.toString(args));
		final InetSocketAddress serverAddress = InetAddresses.toSocketAddress(args[0]);
		final String alias = args[1];
		final String password = args[2];
		final boolean binaryMode = Boolean.parseBoolean(args[3]);
		final String transferDirection = args[4];
		final Path sourcePathOnClient = Paths.get(args[5]).normalize();
		
		System.out.println(sourcePathOnClient);
		
		final Path targetPathOnServer = Paths.get(args[6]).normalize();

		try (FtpClient client = new FtpClient(serverAddress)) {
			client.open(alias, password, binaryMode);

			if (transferDirection.equals("STORE")) {
				client.sendFile(sourcePathOnClient, targetPathOnServer);
			} else if (transferDirection.equals("RETRIEVE")) {
				client.receiveFile(sourcePathOnClient, targetPathOnServer);
			} else {
				throw new IllegalArgumentException(transferDirection);
			}
		}
	}
}