package de.htw.ds.sort;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import de.sb.toolbox.Copyright;


/**
 * This class implements a single-threaded file sorter test case. It sorts all non-empty trimmed
 * lines of a an input file into an output file. Note that this class is declared final because it
 * provides an application entry point, and therefore not supposed to be extended.
 */
@Copyright(year=2010, holders="Sascha Baumeister")
public final class SortClient1 extends SortClient {

	/**
	 * Returns a new stream sorter.
	 * @return the stream sorter
	 */
	static private StreamSorter<String> createSorter () {
		return new SingleThreadSorter<>();
	}


	/**
	 * Processes a stream sort test case. Arguments are the path to the input file, and the path of
	 * the sorted output file.
	 * Example args-   resources/goethe-faust/goethe-faust.txt out.txt
	 * 				   resources/goethe-faust/goethe-faust-x50.txt out.txt
	 * @param args the given runtime arguments
	 * @throws IOException if there is an I/O related problem
	 */
	static public void main (final String[] args) throws IOException {
		final Path sourcePath = Paths.get(args[0]);
		final Path sinkPath = Paths.get(args[1]);

		final StreamSorter<String> sorter = SortClient1.createSorter();
		final SortClient1 client = new SortClient1(sourcePath, sinkPath, sorter);
		client.process();
	}


	/**
	 * Creates a new sort client.
	 * @param sourcePath the source file path
	 * @param targetPath the target file path
	 * @param streamSorter the stream sorter
	 */
	public SortClient1 (final Path sourcePath, final Path targetPath, final StreamSorter<String> streamSorter) {
		super(sourcePath, targetPath, streamSorter);
	}
}