package de.htw.ds.tcpmonitor;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.sb.toolbox.Copyright;
import de.sb.toolbox.io.IOStreams;
import de.sb.toolbox.io.MultiOutputStream;


/**
 * This class models a TCP monitor, i.e. a TCP server that redirects all incoming client connections
 * towards another host, while logging all traffic.
 */
@Copyright(year=2008, holders="Sascha Baumeister")
public class TcpMonitorServer implements Runnable, AutoCloseable {

	private final ExecutorService threadPool;
	private final ServerSocket host;
	private final InetSocketAddress redirectHostAddress;
	private final Consumer<TcpMonitorRecord> recordConsumer;
	private final Consumer<Throwable> exceptionConsumer;


	/**
	 * Creates a new instance.
	 * @param servicePort the service port
	 * @param redirectHostAddress the redirect host address
	 * @param recordConsumer the record consumer
	 * @param exceptionConsumer the exception consumer
	 * @throws NullPointerException if any of the given arguments is {@code null}
	 * @throws IllegalArgumentException if the given service port is outside range [0, 0xFFFF]
	 * @throws IOException if the given service port is already in use, or cannot be bound
	 */
	public TcpMonitorServer (final int servicePort, final InetSocketAddress redirectHostAddress, final Consumer<TcpMonitorRecord> recordConsumer, final Consumer<Throwable> exceptionConsumer) throws IOException {
		if (redirectHostAddress == null | recordConsumer == null | exceptionConsumer == null) throw new NullPointerException();

		this.threadPool = Executors.newCachedThreadPool();
		this.host = new ServerSocket(servicePort);
		this.redirectHostAddress = redirectHostAddress;
		this.recordConsumer = recordConsumer;
		this.exceptionConsumer = exceptionConsumer;
	}


	/**
	 * Closes this server.
	 * @throws IOException {@inheritDoc}
	 */
	public void close () throws IOException {
		try {
			this.host.close();
		} finally {
			this.threadPool.shutdown();
		}
	}


	/**
	 * Returns the redirect host address.
	 * @return the redirect host address
	 */
	public InetSocketAddress getRedirectHostAddress () {
		return redirectHostAddress;
	}



	/**
	 * Returns the service port.
	 * @return the service port
	 */
	public int getServicePort () {
		return this.host.getLocalPort();
	}


	/**
	 * Periodically blocks until a request arrives, handles the latter subsequently.
	 */
	public void run () {
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
	 * Instances of this inner class handle TCP client connections accepted by a TCP monitor.
	 */
	static private class ConnectionHandler implements Runnable {
		private final TcpMonitorServer parent;
		private final Socket clientConnection;


		/**
		 * Creates a new instance from a given client connection.
		 * @param parent the parent monitor
		 * @param clientConnection the connection
		 * @throws NullPointerException if any of the given arguments is {@code null}
		 */
		public ConnectionHandler (final TcpMonitorServer parent, final Socket clientConnection) {
			if (parent == null | clientConnection == null) throw new NullPointerException();

			this.parent = parent;
			this.clientConnection = clientConnection;
		}


		/**
		 * Handles the client connection by transporting all data to a new server connection, and
		 * vice versa. Closes all connections upon completion.
		 */
		public void run() {
			Future<?> clientThreadFuture = null;
			Future<?> serverThreadFuture = null;
			try (Socket clientConnection = this.clientConnection) {
				long openTimestamp = System.currentTimeMillis();
				try (Socket serverConnection = new Socket(this.parent.redirectHostAddress.getHostName(),this.parent.redirectHostAddress.getPort())) {
					try (ByteArrayOutputStream outputStreamBytesClient = new ByteArrayOutputStream();
							ByteArrayOutputStream outputStreamBytesServer = new ByteArrayOutputStream();) {
						// when the client or server sockets are closed those
						// stream are closed aswell. See Socket.close
						InputStream inputStreamClient = clientConnection.getInputStream();
						InputStream inputStreamServer = serverConnection.getInputStream();

						MultiOutputStream multiOutputStreamClient = new MultiOutputStream(clientConnection.getOutputStream(), outputStreamBytesServer);
						MultiOutputStream multiOutputStreamServer = new MultiOutputStream(serverConnection.getOutputStream(), outputStreamBytesClient);

						int bufferSize = 0x10000;
						// Transport all content from the client connection's input stream into
						// both the server connection's output stream and a byte output stream.
						clientThreadFuture = parent.threadPool.submit(() -> {
							try {
								IOStreams.copy(inputStreamClient, multiOutputStreamServer, bufferSize);
							} catch (Throwable exception) {
								this.parent.exceptionConsumer.accept(exception);
							}
						});
						// In parallel, transport all content from the server connection's input stream
						// into both the client connection's output stream and another byte output stream.
						serverThreadFuture = parent.threadPool.submit(() -> {
							try {
								IOStreams.copy(inputStreamServer, multiOutputStreamClient, bufferSize);
							} catch (final Throwable exception) {
								this.parent.exceptionConsumer.accept(exception);
							}
						});
						// Resynchronize them before closing all resources
						clientThreadFuture.get();
						serverThreadFuture.get();
						long closeTimestamp = System.currentTimeMillis();
						// If all goes well, use "ByteArrayOutputStream#toByteArray()" to get the respective request and response data; use it to
						// create a TcpMonitorRecord, and flush it using "this.parent.recordConsumer.accept()"
						this.parent.recordConsumer.accept(new TcpMonitorRecord(openTimestamp, closeTimestamp,
								outputStreamBytesClient.toByteArray(), outputStreamBytesServer.toByteArray()));
					}
				}
				// If anything goes wrong, use "this.parent.exceptionConsumer.accept()" instead.
			} catch (final Throwable exception) {
				exception.printStackTrace();
				clientThreadFuture.cancel(true);
				serverThreadFuture.cancel(true);
				this.parent.exceptionConsumer.accept(exception);
			}
		}
	}
}