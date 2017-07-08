package de.htw.ds.sort;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
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
public final class SortServerSkeleton implements Runnable, AutoCloseable {

	/**
	 * Static inner class modeling CSP connection handlers that are spawned whenever
	 * a new TCP connection is established.
	 */
	@SuppressWarnings("unused") // TODO: remove when implementing
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
			final StreamSorter<String> streamSorter = SortServerSkeleton.createSorter();

			// TODO: write the server logic conforming to above CSP protocol as the
			// counterpart to the given MultiProgramSorter class, using this handler's connection
			// in order to read/write data. This implies performing a multitude of write()
			// operations with the stream sorter defined above, then a single sort() operation
			// once an empty line has been received, and finally a multitude of read() operations,
			// depending on the data transmitted. Best wrap the connection streams in buffered
			// readers and writers to ease reading&writing CR delimited lines, similarly to
			// how the MultiProgramSorter class performs it's I/O operations.
		}
	}


	/**
	 * Returns a new stream sorter.
	 * @return the stream sorter
	 */
	static private StreamSorter<String> createSorter () {
		// TODO: create stream sorter similarly to SortClient2, i.e. using as many
		// single thread sorter instances as tree leaves as there are available processors.
		// However, only do this once the connection handler's run() method below is
		// fully tested, in order to avoid complications during debugging.
		return new SingleThreadSorter<String>();
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

		try (SortServerSkeleton server = new SortServerSkeleton(servicePort)) {
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
	public SortServerSkeleton (final int servicePort) throws IOException {
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