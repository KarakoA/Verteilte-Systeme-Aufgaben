package de.htw.ds.sort.multithread;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.htw.ds.sort.SingleThreadSorter;
import de.htw.ds.sort.SortClient;
import de.htw.ds.sort.StreamSorter;
import de.sb.toolbox.Copyright;


/**
 * This class implements a single-threaded file sorter test case. It sorts all non-empty trimmed
 * lines of a an input file into an output file. Note that this class is declared final because it
 * provides an application entry point, and therefore not supposed to be extended.
 */
@Copyright(year=2010, holders="Sascha Baumeister")
public final class SortClient2 extends SortClient {

	/**
	 * Returns a new stream sorter.
	 * @return the stream sorter
	 */
	static private StreamSorter<String> createSorter() {
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
		
		final StreamSorter<String> sorter = createSorter();
		
		final SortClient2 client = new SortClient2(sourcePath, sinkPath, sorter);
		client.process();
	}


	/**
	 * Creates a new sort client.
	 * @param sourcePath the source file path
	 * @param targetPath the target file path
	 * @param streamSorter the stream sorter
	 */
	public SortClient2 (final Path sourcePath, final Path targetPath, final StreamSorter<String> streamSorter) {
		super(sourcePath, targetPath, streamSorter);
	}
}