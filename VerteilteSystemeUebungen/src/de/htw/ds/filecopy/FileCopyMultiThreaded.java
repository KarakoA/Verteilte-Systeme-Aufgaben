package de.htw.ds.filecopy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Demonstrates copying a file using two threads. Note that this class is declared final because
 * it provides an application entry point, and therefore not supposed to be extended.
 */
public final class FileCopyMultiThreaded {

	/**
	 * Copies a file. The first argument is expected to be a qualified source file name, the second
	 * a qualified target file name.
	 * @param args the VM arguments
	 * @throws IOException if there's an I/O related problem
	 */
	static public void main (final String[] args) throws IOException {
		final Path sourcePath = Paths.get(args[0]);
		if (!Files.isReadable(sourcePath)) throw new IllegalArgumentException(sourcePath.toString());

		final Path sinkDirectory = Paths.get(args[1]);
		final Path sinkPath =  sinkDirectory.resolve(sourcePath.getFileName());
		if (sinkPath.getParent() != null && !Files.isDirectory(sinkPath.getParent())) throw new IllegalArgumentException(sinkPath.toString());

		deleteOldCreateNewFile(sinkPath);
		
		PipedOutputStream pipedOutput = new PipedOutputStream();
		PipedInputStream pipedInputStream = new PipedInputStream(pipedOutput);
		
		Transporter sourceToPipeTransporter = new Transporter(Files.newInputStream(sourcePath), pipedOutput);
		Transporter pipeToSinkTransporter = new Transporter(pipedInputStream, Files.newOutputStream(sinkPath));
		
		Thread thread1 = new Thread(sourceToPipeTransporter);
		Thread thread2 = new Thread(pipeToSinkTransporter);
		
		
		thread1.start();
		thread2.start();		
	}
	
	private static void deleteOldCreateNewFile(Path copiedFilePath) throws IOException {
		Files.deleteIfExists(copiedFilePath);
		Files.createFile(copiedFilePath);
	}

	/**
	 * Private static inner class Transporter. 
	 * Used to transport the content of a given input stream to a given output stream.
	 */
	private static class Transporter implements Runnable{
		
		private InputStream inputStream;
		private OutputStream outputStream;
		/**
		 * Creates a new instance.
		 * @param inputStream the input stream to read from 
		 * @param outputStream the output stream to write to 
		 */
		public Transporter(InputStream inputStream, OutputStream outputStream){
			this.inputStream = inputStream;
			this.outputStream = outputStream;
		}
		/**
		 * Transports the bytes of the input stream to the output stream.
		 */
		@Override
		public void run() {
			try (InputStream fis = inputStream;
					OutputStream fos = outputStream) {
					final byte[] buffer = new byte[0x10000];
					for (int bytesRead = fis.read(buffer); bytesRead != -1; bytesRead = fis.read(buffer)) {
						fos.write(buffer, 0, bytesRead);
					}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}finally {
				System.out.println(Thread.currentThread().getName() + " done.");
			}
		}
	}
} 
