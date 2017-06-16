package de.htw.ds.sort.multiprogram;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.htw.ds.sort.SingleThreadSorter;
import de.htw.ds.sort.StreamSorter;
import de.htw.ds.sort.StreamSorter.State;
import de.htw.ds.sort.multithread.MultiThreadSorter;
import de.sb.toolbox.Copyright;


/**
 * Server that provides stateful sorting using a custom TCP based sort protocol, based on the
 * acceptor/service design pattern. Note that this class is declared final because it provides
 * an application entry point, and is therefore not supposed to be extended. Also note that the
 * protocol only allows for text sorters, and it's syntax is defined in EBNF as follows:
 * <pre>
 * cspRequest	:= { element, CR }, CR
 * cspResponse	:= ("error", CR, message) | ("ok", { CR, element } )
 * CR			:= line separator
 * element		:= String - (null | "")
 * message      := String -  null
 * </pre>
 */
@Copyright(year=2010, holders="Sascha Baumeister")
public final class SortServer implements Runnable, AutoCloseable {

	/**
	 * Static inner class modeling CSP connection handlers that are spawned whenever
	 * a new TCP connection is established.
	 */
	static private class ConnectionHandler implements Runnable {
		private final Socket connection;


		/**
		 * Creates a new instance.
		 * @param connection the TCP socket connection
		 * @throws NullPointerException if the given connection is {@code null}
		 */
		public ConnectionHandler (final Socket connection) {
	
			if (connection == null) throw new NullPointerException();

			this.connection = connection;
		}


		/**
		 * Handles a CSP connection, allowing for multiple requests per connection.
		 * Implicitly closes the connection before returning, which is required for
		 * regular program termination.
		 */
		public void run () {
			final StreamSorter<String> streamSorter = SortServer.createSorter();
			try(
				BufferedReader charSource = new BufferedReader(new InputStreamReader(this.connection.getInputStream()));
				BufferedWriter charSink = new BufferedWriter(new OutputStreamWriter(this.connection.getOutputStream()))
				){
				charSink.write("ok");
				charSink.newLine();
				charSink.flush();

				String result = charSource.readLine();
				while (!("").equals(result)) {
					streamSorter.write(result);
					result = charSource.readLine();
				}
				streamSorter.sort();
				try {
					while(streamSorter.getState() == State.READ){
						String sortedResult  = streamSorter.read();
						charSink.write(sortedResult);
						charSink.newLine();
						charSink.flush();
					}
				} catch (final IOException exception) {
					charSink.write("errors");
					charSink.newLine();
					charSink.flush();
					throw new IllegalStateException(exception);
				}
			} catch (final IOException exception) {
				throw new IllegalStateException(exception);
			}
		}
	}


	/**
	 * Returns a new stream sorter.
	 * @return the stream sorter
	 */
	static private StreamSorter<String> createSorter () {
		int processorCount=Runtime.getRuntime().availableProcessors();
		if(processorCount == 1)
			return new SingleThreadSorter<>();
		else{
			Queue<StreamSorter<String>> queue = new ArrayDeque<>();
			//fill the queue with single thread sorters
			for (int i = 0; i < processorCount; i++) 
				queue.add(new SingleThreadSorter<String>());
			
			//combine them to multi thread sorters
			while(queue.size() !=1){
				StreamSorter<String> left = queue.poll();
				StreamSorter<String> right = queue.poll();
				queue.add(new MultiThreadSorter<>(left,right));
			}
			return queue.poll();
		}
	}


	/**
	 * Application entry point. The given runtime parameters must be a service port.
	 * @param args the given runtime arguments
	 * @throws IllegalArgumentException if the given service port is outside range [0, 0xFFFF]
	 * @throws IOException if the given port is already in use, or if there is a problem waiting for
	 *         the quit signal
	 */
	static public void main (final String[] args) throws IOException {
		final long timestamp = System.currentTimeMillis();
		final int servicePort = Integer.parseInt(args[0]);

		try (SortServer server = new SortServer(servicePort)) {
			System.out.println("Sort server running on one acceptor thread, enter \"quit\" to stop.");
			System.out.format("Service port is %s.\n", server.getServicePort());
			System.out.format("Startup time is %sms.\n", System.currentTimeMillis() - timestamp);

			final BufferedReader charSource = new BufferedReader(new InputStreamReader(System.in));
			while (!"quit".equals(charSource.readLine()));
		}
		
	}


	private final ServerSocket serviceSocket;


	/**
	 * Public constructor.
	 * @param servicePort the service port
	 * @throws IllegalArgumentException if the given service port is outside range [0, 0xFFFF]
	 * @throws IOException if the given port is already in use, or cannot be bound
	 */
	public SortServer (final int servicePort) throws IOException {
		this.serviceSocket = new ServerSocket(servicePort);
		new Thread(this, "csp-acceptor").start();
	}


	/**
	 * Closes the server.
	 * @throws IOException if there is an I/O related problem
	 */
	public void close () throws IOException {
		this.serviceSocket.close();
	}


	/**
	 * Returns the service port.
	 * @return the service port
	 */
	public int getServicePort () {
		return this.serviceSocket.getLocalPort();
	}



	/**
	 * Periodically blocks until a TCP connection is requested, handles the latter subsequently.
	 */
	public void run () {
		Socket connection = null;
		try {
			while (true) {
				connection = this.serviceSocket.accept();
				new Thread(new ConnectionHandler(connection), "csp-service").start();
			}
		} catch (final SocketException exception) {
			// service socket has been closed
		} catch (final IOException exception) {
			try { connection.close(); } catch (final Exception nestedException) {}
			exception.printStackTrace();
		}
	}
}