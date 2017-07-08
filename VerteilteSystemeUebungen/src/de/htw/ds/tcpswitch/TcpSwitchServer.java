package de.htw.ds.tcpswitch;

import java.io.Closeable;
import java.io.IOException;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.sb.toolbox.Copyright;
import de.sb.toolbox.io.IOStreams;

/**
 * This class models a TCP switch server, i.e. a "spray" server for all kinds of TCP oriented
 * protocol connections. It redirects incoming client connections to it's given set of redirect
 * servers, either randomly selected, or determined by known session association. Note that while
 * this implementation routes all kinds of TCP protocols, a single instance is only able to route
 * one protocol type unless it's child servers support multi-protocol requests.
 * Session association is determined by receiving subsequent requests from the same client, which
 * may or may not be interpreted as being part of the same session by the protocol server selected.
 * However, two requests can never be part of the same session if they do not share the same request
 * client address! Note that this algorithm allows for protocol independence, but does not work with
 * clients that dynamically change their IP-address during a session's lifetime.
 */
@Copyright(year = 2008, holders = "Sascha Baumeister")
public class TcpSwitchServer implements Runnable, Closeable {

	private final ExecutorService threadPool;
	private final ServerSocket host;
	private final InetSocketAddress[] redirectHostAddresses;
	private final boolean sessionAware;

	/**
	 * Creates a new instance.
	 * @param servicePort the service port
	 * @param redirectHostAddresses the redirect host addresses
	 * @param sessionAware true if the server is aware of sessions, false otherwise
	s	 * @throws NullPointerException if any of the given addresses is {@code null}
	 * @throws IllegalArgumentException if the given service port is outside range [0, 0xFFFF], or
	 *         the given socket-addresses array is empty
	 * @throws IOException if the given port is already in use, or cannot be bound
	 */
	public TcpSwitchServer(final int servicePort, final InetSocketAddress[] redirectHostAddresses, final boolean sessionAware) throws IOException {
		if (redirectHostAddresses.length == 0)
			throw new IllegalArgumentException();

		this.threadPool = Executors.newCachedThreadPool();
		this.host = new ServerSocket(servicePort);
		this.redirectHostAddresses = redirectHostAddresses;
		this.sessionAware = sessionAware;
	}

	/**
	 * Closes this server.
	 * @throws IOException {@inheritDoc}
	 */
	public void close() throws IOException {
		try {
			this.host.close();
		} finally {
			this.threadPool.shutdown();
		}
	}

	/**
	 * Returns the redirect host addresses.
	 * @return the redirect host addresses
	 */
	public InetSocketAddress[] getRedirectHostAddresses() {
		return this.redirectHostAddresses;
	}

	/**
	 * Returns the service port.
	 * @return the service port
	 */
	public int getServicePort() {
		return this.host.getLocalPort();
	}

	/**
	 * Returns the session awareness.
	 * @return the session awareness
	 */
	public boolean getSessionAware() {
		return this.sessionAware;
	}

	/**
	 * Periodically blocks until a request arrives, handles the latter subsequently.
	 */
	public void run() {
		while (true) {
			Socket clientConnection = null;
			try {
				clientConnection = this.host.accept();
				this.threadPool.execute(new ConnectionHandler(this, clientConnection));
			} catch (final SocketException exception) {
				break;
			} catch (final Throwable exception) {
				try {
					clientConnection.close();
				} catch (final Throwable nestedException) {
					exception.addSuppressed(nestedException);
				}
				Logger.getGlobal().log(Level.WARNING, exception.getMessage(), exception);
			}
		}
	}

	/**
	 * Instances of this inner class handle TCP client connections accepted by a TCP switch.
	 */
	static private class ConnectionHandler implements Runnable {

		private static final int BUFFER_SIZE = 0x10000;
		
		private final TcpSwitchServer parent;
		private final Socket clientConnection;

		/**
		 * Creates a new instance from a given client connection.
		 * @parent the parent switch
		 * @param clientConnection the connection
		 * @throws NullPointerException if any of the given arguments is {@code null}
		 */
		public ConnectionHandler(final TcpSwitchServer parent, final Socket clientConnection) {
			if (parent == null | clientConnection == null)
				throw new NullPointerException();
			this.parent = parent;
			this.clientConnection = clientConnection;
		}

		private InetSocketAddress scramble() {
			
			int seed=clientConnection.getInetAddress().hashCode();
			//Thread.LocalRandom 
			Random r = parent.sessionAware ? new Random(seed) : new Random();
			int pos = r.nextInt(parent.redirectHostAddresses.length);
			return parent.redirectHostAddresses[pos];
		}

		/**
		 * Handles the client connection by transporting all data to a new server connection, and
		 * vice versa. Closes all connections upon completion.
		 */
		public void run() {
			InetSocketAddress redictAddress = scramble();
			System.out.println("Server Address: " + redictAddress);
			
			try (Socket clientConnection = this.clientConnection) {
				try (Socket serverConnection = new Socket(redictAddress.getHostName(), redictAddress.getPort())) {

					final Future<?> clientThreadFuture = parent.threadPool
							.submit(() -> IOStreams.copy(clientConnection.getInputStream(), serverConnection.getOutputStream(), BUFFER_SIZE));

					final Future<?> serverThreadFuture = parent.threadPool
							.submit(() -> IOStreams.copy(serverConnection.getInputStream(), clientConnection.getOutputStream(), BUFFER_SIZE));
					try {
						clientThreadFuture.get();
						serverThreadFuture.get();
					}catch(ExecutionException e){
						final Throwable cause = e.getCause();
						if(cause instanceof Error) throw (Error) cause;
						if(cause instanceof RuntimeException) throw (RuntimeException) cause;
						if(cause instanceof IOException) throw (IOException) cause;
						throw new AssertionError(cause);
					}
					finally {
						clientThreadFuture.cancel(true);
						serverThreadFuture.cancel(true);
					}
				}
			} catch (final Throwable exception) {

				exception.printStackTrace();
			}
		}

	}
}