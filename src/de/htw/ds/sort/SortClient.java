package de.htw.ds.sort;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import de.sb.toolbox.Copyright;


/**
 * This class defines a file sorter test case. It sorts all non-empty trimmed lines of a an input
 * file into an output file. Note that this class is declared final because it provides an
 * application entry point, and therefore not supposed to be extended.
 */
@Copyright(year=2010, holders="Sascha Baumeister")
public abstract class SortClient {

	private final Path sourcePath;
	private final Path targetPath;
	private final StreamSorter<String> streamSorter;


	/**
	 * Creates a new sort client.
	 * @param sourcePath the source file path
	 * @param targetPath the target file path
	 * @param streamSorter the stream sorter
	 */
	public SortClient (final Path sourcePath, final Path targetPath, final StreamSorter<String> streamSorter) {
		if (sourcePath == null | targetPath == null | streamSorter == null) throw new NullPointerException();

		this.sourcePath = sourcePath;
		this.targetPath = targetPath;
		this.streamSorter = streamSorter;
	}


	/**
	 * Processes the elements to be sorted from from the given source file and writes the sorted
	 * elements to the given target file.
	 * @throws IOException if an I/O related problem occurs
	 */
	public final void process () throws IOException {
		try (BufferedReader charSource = Files.newBufferedReader(this.sourcePath, StandardCharsets.UTF_8)) {
			try (BufferedWriter charSink = Files.newBufferedWriter(this.targetPath, StandardCharsets.UTF_8)) {
				final long timestamp1 = System.currentTimeMillis();

				long elementCount = 0;
				for (String line = charSource.readLine(); line != null; line = charSource.readLine()) {
					for (final String element : line.split("\\s")) {
						if (!element.isEmpty()) {
							this.streamSorter.write(element);
							elementCount += 1;
						}
					}
				}

				final long timestamp2 = System.currentTimeMillis();
				this.streamSorter.sort();
				final long timestamp3 = System.currentTimeMillis();

				for (long todo = elementCount; todo > 0; --todo) {
					final String element = this.streamSorter.read();
					charSink.write(element);
					charSink.newLine();
				}
				charSink.flush();

				final long timestamp4 = System.currentTimeMillis();
				System.out.format("Sort ok, %s elements sorted.\n", elementCount);
				System.out.format("Read time: %sms.\n", timestamp2 - timestamp1);
				System.out.format("Sort time: %sms.\n", timestamp3 - timestamp2);
				System.out.format("Write time: %sms.\n", timestamp4 - timestamp3);
			}
		} finally {
			try {
				this.streamSorter.reset();
			} catch (final Exception exception) {}
		}
	}
}