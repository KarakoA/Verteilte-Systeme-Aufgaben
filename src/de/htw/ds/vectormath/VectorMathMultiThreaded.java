package de.htw.ds.vectormath;

import java.util.Arrays;

public class VectorMathMultiThreaded {
	
	private static final int PROCESSOR_COUNT=Runtime.getRuntime().availableProcessors();
	
	private static class Calculator implements Runnable{
				
		private double[] resultAdd;
		private double[][] resultMux;
		private final double[] a;
		private final double[] b;
		private boolean adding;
		private final int start;
		private final int end;

		private Calculator(double[] a, double[] b, int start, int end) {
			this.a = a;
			this.b = b;
			this.start = start;
			this.end = end;
		}
		/**
		 * Creates a new instance which adds two vectors.
		 */
		public Calculator(double[] a, double[] b, int start, int end, double[] resultAdd) {
			this(a,b,start,end);
			this.resultAdd = resultAdd;
			this.adding=true;
		}
		/**
		 * Creates a new instance which multiplies two vectors.
		 */
		public Calculator(double[] a, double[] b, int start, int end, double[][] resultMux) {
			this(a,b,start,end);
			this.resultMux = resultMux;
			this.adding=false;
		}
				
		/**
		 * Sums the vectors within a single thread.
		 * @throws NullPointerException if one of the vectors is {@code null}
		 */
		private void add () {
			for (int x = start; x < end; ++x) {
				resultAdd[x] = a[x] + b[x];
			}
		}
		
		/**
		 * Multiplies the vectors within a single thread.
		 * @throws NullPointerException if one of the vectors is {@code null}
		 */
		private void mux () {
			for (int x = start; x < end; ++x) {
				for (int y = 0; y < b.length; ++y) {
					resultMux[x][y] = a[x] * b[y];
				}
			}
		}
		
		/**
		 * Multiplies or adds the vectors depending on the parameters with which this object was constructed.
		 * {@see Calculator#add()}
		 * {@see Calculator#mux()}
		 * {@see java.lang.Runnable#run()}
		 * */
		@Override
		public void run() {
			if(adding){
				 add();
			}else{
				 mux();
			}		
		}
	}

	/**
	 * Runs both vector summation and vector multiplexing for demo purposes.
	 * @param args the argument array
	 */
	static public void main (final String[] args) {
		final int size = args.length == 0 ? 1000 : Integer.parseInt(args[0]);
	
		// initialize operand vectors
		final double[] a = new double[size], b = new double[size];
		for (int index = 0; index < size; ++index) {
			a[index] = index + 1.0;
			b[index] = index + 2.0;
		}
		int resultHash = 0;

		// Warm-up phase to force hot-spot translation of byte-code into machine code, code-optimization, etc!
		// Output of resultHash prevents VM from over-optimizing the warmup-phase (by complete removal), which
		// happens to code that does not compute something in loops that is not used outside of it.
		for (int loop = 0; loop < 30000; ++loop) {
			double[] c = addMulti(a, b);
			resultHash ^= c.hashCode();

			double[][] d = muxMulti(a, b);
			resultHash ^= d.hashCode();
		}
		System.out.format("warm-up phase ended with result hash %d\n", resultHash);
	
		System.out.format("Computation is performed on %s processors\n",PROCESSOR_COUNT);
		final long timestamp0 = System.currentTimeMillis();
		for (int loop = 0; loop < 10000; ++loop) {
			final double[] sum = addMulti(a, b);
			resultHash ^= sum.hashCode();
		}
		final long timestamp1 = System.currentTimeMillis();
		for (int loop = 0; loop < 10000; ++loop) {
			final double[][] mux = muxMulti(a, b);
			resultHash ^= mux.hashCode();
		}
		final long timestamp2 = System.currentTimeMillis();
		System.out.format("timing phase ended with result hash %d\n", resultHash);

		System.out.format("a + b took %.4fms to compute.\n", (timestamp1 - timestamp0) * 0.0001);
		System.out.format("a x b took %.4fms to compute.\n", (timestamp2 - timestamp1) * 0.0001);
		if (size <= 100) {
			final double[] sum = addMulti(a, b);
			final double[][] mux = muxMulti(a, b);
			System.out.print("a = ");
			System.out.println(Arrays.toString(a));
			System.out.print("b = ");
			System.out.println(Arrays.toString(b));
			System.out.print("a + b = ");
			System.out.println(Arrays.toString(sum));
			System.out.print("a x b = [");
			for (int index = 0; index < mux.length; ++index) {
				System.out.print(Arrays.toString(mux[index]));
			}
			System.out.println("]");
		}
	}

	private static double[][] muxMulti(double[] a, double[] b) {
		if (a.length != b.length) 
			throw new IllegalArgumentException();
		
		double[][] resultMux = new double[a.length][b.length];
		Thread[] threads = new Thread[PROCESSOR_COUNT];
		float nDouble = a.length/ (float) PROCESSOR_COUNT;
		int n = (int) Math.ceil(nDouble);
		for (int i = 0; i < PROCESSOR_COUNT; i++) {
			int start = i*n;
			int end = start + n;
			if(end> a.length) end = a.length;
			Calculator muxCalc = new Calculator(a, b, start,end, resultMux);
			Thread thread = new Thread(muxCalc);
			threads[i] = thread;
			thread.start();
		}
		joinOnThreads(threads);
		return resultMux;
	}

	private static double[] addMulti(double[] a, double[] b) {
		if (a.length != b.length) 
			throw new IllegalArgumentException();
		
		double[] resultAdd = new double[a.length];
		Thread[] threads = new Thread[PROCESSOR_COUNT];
		float nDouble = a.length/ (float) PROCESSOR_COUNT;
		int n = (int) Math.ceil(nDouble);
		for (int i = 0; i < PROCESSOR_COUNT; i++) {
			int start = i*n;
			int end = start + n;
			if(end> a.length) end = a.length;
			Calculator addCalc = new Calculator(a, b, start, end,resultAdd);
			Thread thread = new Thread(addCalc);
			threads[i] = thread;
			thread.start();
		}
		joinOnThreads(threads);
		return resultAdd;
	}
	
	private static void joinOnThreads(Thread[] threads){
		for (Thread thread : threads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
