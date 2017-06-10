package de.htw.ds.sort;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import de.sb.toolbox.Copyright;


/**
 * String sorter implementation that forwards all requests to a stream sort server, using a custom
 * sort protocol. Write requests are forwarded by writing the given string, followed by a newline.
 * Note that null or empty elements are not allowed! Sort requests are forwarded by sending an empty
 * string followed by a newline instead. Read requests mean that a line is read, while reset
 * requests close the connection and null its related fields.<br />
 * Note that a single connection is reused for all interactions with the server, which implies all
 * non-private methods must be synchronized. The protocol syntax is defined in EBNF as follows:
 * 
 * <pre>
 * cspRequest	:= { element, CR }, CR
 * cspResponse	:= ("error", CR, message) | ("ok", { CR, element } )
 * CR			:= line separator
 * element		:= String - (null | "")
 * message      := String -  null
 * </pre>
 */
@Copyright(year=2010, holders="Sascha Baumeister")
public class MultiProgramSorter implements StreamSorter<String> {

	private final InetSocketAddress socketAddress;
	private volatile State state;
	private volatile Socket connection;
	private volatile BufferedReader charSource;
	private volatile BufferedWriter charSink;


	/**
	 * Creates a new instance in {@link State#WRITE} state that is able to communicate with a
	 * stream server over the given socket address.
	 * @param socketAddress the socket address
	 * @throws NullPointerException if the given socket address is {@code null}
	 */
	public MultiProgramSorter (final InetSocketAddress socketAddress) {
		if (socketAddress == null) throw new NullPointerException();

		this.socketAddress = socketAddress;
		this.state = State.WRITE;
	}


	/**
	 * Creates a new active server connection.
	 * @throws IOException if there is an I/O related problem
	 */
	private void connect () throws IOException {
		this.connection = new Socket(this.socketAddress.getAddress(), this.socketAddress.getPort());
		this.charSource = new BufferedReader(new InputStreamReader(this.connection.getInputStream()));
		this.charSink = new BufferedWriter(new OutputStreamWriter(this.connection.getOutputStream()));
	}


	/**
	 * {@inheritDoc}
	 */
	public State getState () {
		return this.state;
	}


	/**
	 * {@inheritDoc}
	 * @throws IllegalStateException {@inheritDoc}
	 */
	public synchronized String read () {
		if (this.state != State.READ) throw new IllegalStateException();
		try {
			final String element = this.charSource.readLine();
			if (this.connection.isClosed()) this.state = State.WRITE;
			return element;
		} catch (final IOException exception) {
			throw new IllegalStateException(exception);
		}
	}


	/**
	 * {@inheritDoc}
	 */
	public synchronized void reset () {
		if (this.connection != null) {
			try {
				this.connection.close();
			} catch (final Exception exception) {}
			this.connection = null;
			this.charSource = null;
			this.charSink = null;
			this.state = State.WRITE;
		}
	}


	/**
	 * {@inheritDoc}
	 * @throws IllegalStateException {@inheritDoc}
	 */
	public synchronized void sort () {
		if (this.state != State.WRITE) throw new IllegalStateException();
		if (this.connection == null) return;

		try {
			this.charSink.newLine();
			this.charSink.flush();

			final String result = this.charSource.readLine();
			if ("error".equals(result)) {
				final String message = this.charSource.readLine();
				throw new IllegalStateException(message);
			} else if ("ok".equals(result)) {
				if (!this.connection.isClosed()) this.state = State.READ;
			} else {
				throw new IllegalStateException();
			}
		} catch (final IOException exception) {
			throw new IllegalStateException(exception);
		}
	}


	/**
	 * {@inheritDoc}
	 * @throws NullPointerException {@inheritDoc}
	 * @throws IllegalStateException {@inheritDoc}
	 */
	public synchronized void write (final String element) {
		if (this.state != State.WRITE) throw new IllegalStateException();
		if (element.isEmpty()) throw new IllegalArgumentException();

		try {
			if (this.connection == null) this.connect();
			this.charSink.write(element);
			this.charSink.newLine();
		} catch (final IOException exception) {
			this.reset();
			throw new IllegalStateException(exception);
		}
	}
}