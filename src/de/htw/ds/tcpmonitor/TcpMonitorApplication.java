package de.htw.ds.tcpmonitor;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import de.sb.toolbox.Copyright;
import de.sb.toolbox.exception.Exceptions;
import de.sb.toolbox.net.InetAddresses;
import de.sb.toolbox.validation.LongValidator;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;


/**
 * This class models a TCP monitor application that can work in both command and GUI mode. TCP
 * monitors are servers that redirect all incoming client connections towards another host, while
 * logging all traffic.
 */
@Copyright(year=2012, holders="Sascha Baumeister")
public class TcpMonitorApplication {

	/**
	 * Prevent external instantiation.
	 */
	private TcpMonitorApplication () {}


	/**
	 * Application entry point. Note that passing no arguments starts the application in GUI mode,
	 * while passing three starts it in command mode.
	 * @param args the runtime arguments: either none, or a service port, redirect address, and
	 *        context path
	 * @throws NullPointerException if any of the given arguments is {@code null}
	 * @throws IllegalArgumentException if there are three arguments while representing an invalid
	 *         service port, redirect address, or context path
	 * @throws IOException if there is an I/O related problem
	 */
	static public void main (final String[] args) throws IOException {
		if (args.length == 3) {
			final int servicePort = Integer.parseInt(args[0]);
			final InetSocketAddress redirectAddress = InetAddresses.toSocketAddress(args[1]);
			final Path contextPath = Paths.get(args[2]).normalize();
			if (!Files.isDirectory(contextPath)) throw new IllegalArgumentException();
			CommandApplication.launch(servicePort, redirectAddress, contextPath);
		} else {
			Application.launch(GuiApplication.class);
		}
	}



	/**
	 * Inner application class for command mode.
	 */
	static public class CommandApplication {

		/**
		 * Prevent external instantiation.
		 */
		private CommandApplication () {}


		/**
		 * Starts the application in command mode.
		 * @param servicePort the service port
		 * @param redirectAddress the redirect address
		 * @param contextPath the context path
		 * @throws NullPointerException if any of the given arguments is {@code null}
		 * @throws IOException if there is an I/O related problem
		 */
		static public void launch (final int servicePort, final InetSocketAddress redirectAddress, final Path contextPath) throws IOException {
			final Consumer<Throwable> exceptionConsumer = exception -> Logger.getGlobal().log(Level.WARNING, exception.getMessage(), exception);
			final Consumer<TcpMonitorRecord> recordConsumer = record -> {
				final String fileName = String.format("%1$tF-%1$tH.%1$tM.%1$tS.%tL-%d.log", record.getOpenTimestamp(), record.getIdentity());
				final Path filePath = contextPath.resolve(fileName);
				try (OutputStream fileSink = Files.newOutputStream(filePath)) {
					fileSink.write(record.getRequestData());
					fileSink.write("\n\n*** RESPONSE DATA ***\n\n".getBytes("ASCII"));
					fileSink.write(record.getResponseData());
				} catch (final Exception exception) {
					exceptionConsumer.accept(exception);
				}
			};
	
			final long timestamp = System.currentTimeMillis();
			try (TcpMonitorServer monitorServer = new TcpMonitorServer(servicePort, redirectAddress, recordConsumer, exceptionConsumer)) {
				// start acceptor thread(s)
				new Thread(monitorServer, "tcp-acceptor").start();
	
				// print welcome message
				System.out.println("TCP monitor server running on one acceptor thread, enter \"quit\" to stop.");
				System.out.format("Service port is %s.\n", monitorServer.getServicePort());
				System.out.format("Forward socket address is %s:%s.\n", monitorServer.getRedirectHostAddress().getHostName(), monitorServer.getRedirectHostAddress().getPort());
				System.out.format("Context directory is %s.\n", contextPath);
				System.out.format("Startup time is %sms.\n", System.currentTimeMillis() - timestamp);
				
				// wait for stop signal on System.in
				final BufferedReader charSource = new BufferedReader(new InputStreamReader(System.in));
				while (!"quit".equals(charSource.readLine()));
			}
		}
	}



	/**
	 * Inner application class for GUI mode. This separation works around the problem of FX starting
	 * threads while loading the Application superclass, which is firstly totally unneccessary
	 * because it could happen during launch, and secondly harmful for non-GUI mode applications.
	 * This way, the FX threads are only started when this inner class is loaded.
	 */
	static public class GuiApplication extends Application {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void start (final Stage window) throws IOException {
			final BorderPane rootPane = newRootPane();
			final Map<String, Image> icons = new HashMap<>();
			for (final String name : new String[] { "start", "suspend", "resume", "stop", "trash" }) {
				icons.put(name, newIcon(name));
			}

			final GuiController controller = new GuiController(rootPane, icons);
			final Image icon = new Image("de/htw/ds/tcpmonitor/resource/tcp-monitor.png");
			final Scene sceneGraph = new Scene(rootPane, 640, 480);
			sceneGraph.getStylesheets().add("de/htw/ds/tcpmonitor/resource/tcp-monitor.css");
	
			window.setOnCloseRequest(event -> controller.close());
			window.setScene(sceneGraph);
			window.setTitle("TCP Monitor");
			window.getIcons().add(icon);
			window.show();
		}


		/**
		 * Returns a new root pane.
		 * @return the root pane created
		 * @throws IOException if there is an I/O related problem
		 */
		static private BorderPane newRootPane () throws IOException {
			try (InputStream byteSource = Thread.currentThread().getContextClassLoader().getResourceAsStream("de/htw/ds/tcpmonitor/resource/tcp-monitor.fxml")) {
				return new FXMLLoader().load(byteSource);
			}
		}


		/**
		 * Returns a new icon image.
		 * @param name the icon name
		 * @return the icon created
		 * @throws NullPointerException if the given argument is {@code null}
		 * @throws IOException if there is an I/O related problem
		 */
		static private Image newIcon (final String name) throws NullPointerException, IOException {
			try (InputStream byteSource = Thread.currentThread().getContextClassLoader().getResourceAsStream("de/htw/ds/tcpmonitor/resource/" + name + "-icon.gif")) {
				return new Image(byteSource);
			}
		}
	}



	/**
	 * Inner controller class for GUI mode.
	 */
	static private class GuiController implements AutoCloseable {
		static private final Charset ASCII = Charset.forName("ASCII");
		static private final Predicate<String> PORT_VALIDATOR = new LongValidator(1, 0xffff);

		private volatile TcpMonitorServer monitorServer;
		private final ImageView startIcon, suspendIcon, resumeIcon, stopIcon, trashIcon;
		private final BorderPane rootPane;
		private final TextField servicePortField, redirectHostField, redirectPortField, errorField;
		private final TextArea requestArea, responseArea;
		private final Button startButton, stopButton, clearButton;
		private final TableView<TcpMonitorRecord> recordTable;
		private final TableColumn<TcpMonitorRecord,Long> recordIdentityColumn, recordRequestLengthColumn, recordResponseLengthColumn;
		private final TableColumn<TcpMonitorRecord,String> recordOpenTimestampColumn, recordCloseTimestampColumn;

	
		/**
		 * Creates a new controller instance using the given view, and initializes view callbacks.
		 * @param rootPane the root pane
		 * @param icons the icons
		 * @throws NullPointerException if any of the given arguments is {@code null}
		 */
		@SuppressWarnings("unchecked")
		public GuiController (final BorderPane rootPane, final Map<String,Image> icons) throws NullPointerException {
			this.rootPane = rootPane;
			this.startIcon = new ImageView(icons.get("start"));
			this.suspendIcon = new ImageView(icons.get("suspend"));
			this.resumeIcon = new ImageView(icons.get("resume"));
			this.stopIcon = new ImageView(icons.get("stop"));
			this.trashIcon = new ImageView(icons.get("trash"));

			final HBox controlPane = (HBox) this.rootPane.getTop();
			this.servicePortField = (TextField) controlPane.getChildren().get(1);
			this.redirectHostField = (TextField) controlPane.getChildren().get(3);
			this.redirectPortField = (TextField) controlPane.getChildren().get(5);
			this.startButton = (Button) controlPane.getChildren().get(6);
			this.stopButton = (Button) controlPane.getChildren().get(7);
			this.clearButton = (Button) controlPane.getChildren().get(8);
			final SplitPane recordPane = (SplitPane) this.rootPane.getCenter();
			this.recordTable = (TableView<TcpMonitorRecord>) recordPane.getItems().get(0);
			this.recordIdentityColumn = (TableColumn<TcpMonitorRecord,Long>) this.recordTable.getColumns().get(0);
			this.recordOpenTimestampColumn = (TableColumn<TcpMonitorRecord,String>) this.recordTable.getColumns().get(1);
			this.recordCloseTimestampColumn = (TableColumn<TcpMonitorRecord,String>) this.recordTable.getColumns().get(2);
			this.recordRequestLengthColumn = (TableColumn<TcpMonitorRecord,Long>) this.recordTable.getColumns().get(3);
			this.recordResponseLengthColumn = (TableColumn<TcpMonitorRecord,Long>) this.recordTable.getColumns().get(4);
			final SplitPane trafficPane = (SplitPane) recordPane.getItems().get(1);
			this.requestArea = (TextArea) trafficPane.getItems().get(0);
			this.responseArea = (TextArea) trafficPane.getItems().get(1);
			final HBox errorPane = (HBox) this.rootPane.getBottom();
			this.errorField = (TextField) errorPane.getChildren().get(1);

			this.startButton.setGraphic(this.startIcon);
			this.stopButton.setGraphic(this.stopIcon);
			this.clearButton.setGraphic(this.trashIcon);
			this.stopButton.setDisable(true);
			this.clearButton.setDisable(true);

			// initialize view callbacks
			this.servicePortField.textProperty().addListener((observable, oldValue, newValue) -> this.handlePortChanged(this.servicePortField));
			this.servicePortField.focusedProperty().addListener((observable, oldValue, newValue) -> this.handleFocusChanged(this.servicePortField));
			this.redirectPortField.textProperty().addListener((observable, oldValue, newValue) -> this.handlePortChanged(this.redirectPortField));
			this.redirectPortField.focusedProperty().addListener((observable, oldValue, newValue) -> this.handleFocusChanged(this.redirectPortField));
			this.startButton.setOnAction(event -> this.handleStartButtonPressed());
			this.stopButton.setOnAction(event -> this.handleStopButtonPressed());
			this.clearButton.setOnAction(event -> this.handleClearButtonPressed());
			this.recordTable.getSelectionModel().selectedItemProperty().addListener(
				(observed, oldSelection, newSelection) -> this.handleTableSelectionChanged(this.recordTable.getSelectionModel().getSelectedIndex())
			);
			this.recordIdentityColumn.setCellValueFactory(new PropertyValueFactory<TcpMonitorRecord,Long>("identity"));
			this.recordOpenTimestampColumn.setCellValueFactory(record -> new SimpleStringProperty(String.format("%tT", record.getValue().getOpenTimestamp())));
			this.recordCloseTimestampColumn.setCellValueFactory(record -> new SimpleStringProperty(String.format("%tT", record.getValue().getCloseTimestamp())));
			this.recordRequestLengthColumn.setCellValueFactory(new PropertyValueFactory<TcpMonitorRecord,Long>("requestLength"));
			this.recordResponseLengthColumn.setCellValueFactory(new PropertyValueFactory<TcpMonitorRecord,Long>("responseLength"));
		}


		/**
		 * Closes this controller's resources.
		 */
		public void close () {
			final TcpMonitorServer monitorServer = this.monitorServer;
			this.monitorServer = null;
			try { monitorServer.close(); } catch (final Exception exception) {}
		}


		/**
		 * Event handler for refocusing due to invalid content.
		 * @param source a node whose focus just changed
		 */
		protected void handleFocusChanged (final Node node) {
			if (!node.isFocused() && node.getStyleClass().contains("invalid")) {
				Platform.runLater(() -> node.requestFocus());
			}
		}


		/**
		 * Event handler for port field validation.
		 * @param node a node whose text just changed
		 */
		protected void handlePortChanged (final TextInputControl node) {
			final boolean valid = PORT_VALIDATOR.test(node.getText());
			if (valid) {
				node.getStyleClass().remove("invalid");
			} else if (!node.getStyleClass().contains("invalid")) {
				node.getStyleClass().add("invalid");
			}
			this.startButton.setDisable(!valid);
		}


		/**
		 * Event handler for the start button.
		 */
		protected void handleStartButtonPressed () {
			this.errorField.setText("");
			try {
				if (this.monitorServer == null) {
					final int servicePort = Integer.parseInt(this.servicePortField.getText());
					final String redirectHostName = this.redirectHostField.getText();
					final int redirectHostPort = Integer.parseInt(this.redirectPortField.getText());
					final InetSocketAddress redirectHostAddress = new InetSocketAddress(redirectHostName, redirectHostPort);
					final Consumer<TcpMonitorRecord> recordConsumer = record -> this.handleRecordCreated(record);
					final Consumer<Throwable> exceptionConsumer = exception -> this.handleExceptionCatched(exception);

					this.monitorServer = new TcpMonitorServer(servicePort, redirectHostAddress, recordConsumer, exceptionConsumer);
					this.stopButton.setDisable(false);
					new Thread(this.monitorServer, "tcp-acceptor").start();
				}

				final boolean active = this.startButton.getGraphic() == this.suspendIcon;
				this.startButton.getTooltip().setText(active ? "resume" : "suspend");
				this.startButton.setGraphic(active ? this.resumeIcon : this.suspendIcon);
			} catch (final Exception exception) {
				this.errorField.setText(errorMessage(exception));
			}
		}


		/**
		 * Closes and discards this pane's TCP monitor, and sets the activity state to inactive.
		 */
		protected void handleStopButtonPressed () {
			try { this.close(); } catch (final Exception exception) {}

			this.startButton.getTooltip().setText("start");
			this.startButton.setGraphic(this.startIcon);
			this.stopButton.setDisable(true);
			this.errorField.setText("");
		}


		/**
		 * Event handler for the clear button.
		 */
		protected void handleClearButtonPressed () {
			this.errorField.setText("");
			this.recordTable.getItems().clear();
			this.clearButton.setDisable(true);
		}


		/**
		 * Event handler for the list selector.
		 * @param rowIndex the selected row index
		 */
		protected void handleTableSelectionChanged (final int rowIndex) {
			if (rowIndex == -1) {
				this.requestArea.setText("");
				this.responseArea.setText("");
			} else {
				final TcpMonitorRecord record = this.recordTable.getItems().get(rowIndex);
				this.requestArea.setText(new String(record.getRequestData(), ASCII));
				this.responseArea.setText(new String(record.getResponseData(), ASCII));
			}
		}


		/**
		 * {@inheritDoc}
		 */
		protected void handleExceptionCatched (final Throwable exception) {
			if (this.startButton.getGraphic() == this.suspendIcon) {
				this.errorField.setText(errorMessage(exception));
			}
		}


		/**
		 * {@inheritDoc}
		 */
		protected void handleRecordCreated (final TcpMonitorRecord record) {
			if (this.startButton.getGraphic() == this.suspendIcon) {
				this.recordTable.getItems().add(record);
				this.clearButton.setDisable(false);
				this.errorField.setText("");
			}
		}


		/**
		 * Returns a formatted error message for the given exception, or an empty string for none.
		 * @param exception the (optional) exception
		 */
		static private String errorMessage (final Throwable exception) {
			if (exception == null) return "";
			final Throwable rootCause = Exceptions.rootCause(exception);
			return String.format("%s: %s", rootCause.getClass().getSimpleName(), rootCause.getMessage());
		}
	}
}