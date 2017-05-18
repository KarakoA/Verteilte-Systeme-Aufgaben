package de.htw.ds.tcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import de.sb.toolbox.Copyright;
import de.sb.toolbox.net.InetAddresses;


/**
 * This class models a TCP switch application. TCP switches model "spray" servers for any kind of
 * TCP oriented protocol connection. They redirect incoming client connections to one of their
 * redirect servers, either randomly selected, or determined by known session association. Note that
 * this class is declared final because it provides an application entry point, and therefore not
 * supposed to be extended.
 */
@Copyright(year=2008, holders="Sascha Baumeister")
public final class TcpSwitchApplication {

	/**
	 * Prevent external instantiation.
	 */
	private TcpSwitchApplication () {}


	/**
	 * Application entry point. The given runtime parameters must be a service port, the session
	 * awareness, and the list of address:port combinations for the cluster nodes.
	 * @param args the given runtime arguments
	 * @throws IllegalArgumentException if the given service port is outside range [0, 0xFFFF], or
	 *         there are no cluster nodes
	 * @throws IOException if the given port is already in use or cannot be bound, or if there is a
	 *         problem waiting for the quit signal
	 */
	static public void main (final String[] args) throws IOException {
		final int servicePort = Integer.parseInt(args[0]);
		final boolean sessionAware = Boolean.parseBoolean(args[1]);
		final Set<InetSocketAddress> redirectAddresses = new HashSet<>();
		for (int index = 2; index < args.length; ++index) {
			redirectAddresses.add(InetAddresses.toSocketAddress(args[index]));
		}

		launch(servicePort, redirectAddresses.toArray(new InetSocketAddress[0]), sessionAware);
	}


	/**
	 * Starts the application in command mode.
	 * @param servicePort the service port
	 * @param redirectAddress the redirect address
	 * @param contextPath the context path
	 * @throws NullPointerException if any of the given arguments is {@code null}
	 * @throws IOException if there is an I/O related problem
	 */
	static public void launch (final int servicePort, final InetSocketAddress[] redirectAddresses, final boolean sessionAware) throws IOException {
		final long timestamp = System.currentTimeMillis();

		try (TcpSwitchServer server = new TcpSwitchServer(servicePort, redirectAddresses, sessionAware)) {
			// start acceptor thread(s)
			new Thread(server, "tcp-acceptor").start();

			// print welcome message
			System.out.println("TCP switch running on one acceptor thread, enter \"quit\" to stop.");
			System.out.format("Service port is %s.\n", server.getServicePort());
			System.out.format("Session awareness is %s.\n", server.getSessionAware());
			System.out.println("The following redirect host addresses have been registered:");
			for (final InetSocketAddress redirectHostAddress : server.getRedirectHostAddresses()) {
				System.out.println(redirectHostAddress);
			}
			System.out.format("Startup time is %sms.\n", System.currentTimeMillis() - timestamp);

			// wait for stop signal on System.in
			final BufferedReader charSource = new BufferedReader(new InputStreamReader(System.in));
			while (!"quit".equals(charSource.readLine()));
		}
	}
}