package de.htw.ds.sort.multiprogram;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.stream.IntStream;

import de.htw.ds.sort.SortClient;
import de.htw.ds.sort.StreamSorter;
import de.htw.ds.sort.multithread.MultiThreadSorter;
import de.sb.toolbox.Copyright;


/**
 * This class implements a single-threaded file sorter test case. It sorts all non-empty trimmed
 * lines of a an input file into an output file. Note that this class is declared final because it
 * provides an application entry point, and therefore not supposed to be extended.
 */
@Copyright(year=2010, holders="Sascha Baumeister")
public final class SortClient3 extends SortClient {

	/**
	 * Returns a new stream sorter.
	 * @return the stream sorter
	 */
	static private StreamSorter<String> createSorter(InetSocketAddress[] sockets) {
		int serversCount=sockets.length;
		if(serversCount== 1)
			return new MultiProgramSorter(sockets[0]);
		else{
			Queue<StreamSorter<String>> queue = new ArrayDeque<>();
			//fill the queue with multi programm sorters
			for (int i = 0; i < sockets.length; i++) 
				queue.add(new MultiProgramSorter(sockets[i]));
			
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
		System.out.println(Arrays.toString(args));
		InetSocketAddress[] sockets = new InetSocketAddress[args.length-2];
		IntStream.range(2, args.length).forEach(index -> sockets[index-2] = new InetSocketAddress(args[index].split(":")[0], Integer.parseInt(args[index].split(":")[1])));
		
		final StreamSorter<String> sorter = createSorter(sockets);
		
		final SortClient3 client = new SortClient3(sourcePath, sinkPath, sorter);
		client.process();

	}


	/**
	 * Creates a new sort client.
	 * @param sourcePath the source file path
	 * @param targetPath the target file path
	 * @param streamSorter the stream sorter
	 */
	public SortClient3 (final Path sourcePath, final Path targetPath, final StreamSorter<String> streamSorter) {
		super(sourcePath, targetPath, streamSorter);
	}
}