package de.htw.ds.sort.multithread;

import de.htw.ds.sort.StreamSorter;
import de.htw.ds.sort.StreamSorter.State;
import de.sb.toolbox.Copyright;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * Multi threaded merge sorter implementation that distributes elements evenly over two child
 * sorters, sorts them separately using two separate threads, and then merges the two sorted
 * children's elements during read requests. Note that this implementation is able to scale its
 * workload over two processor cores, and even more if such sorters are stacked!
 * @param <E> the element type to be sorted in naturally ascending order
 */
@Copyright(year = 2010, holders = "Sascha Baumeister")
public class MultiThreadSorter<E extends Comparable<E>> implements StreamSorter<E> {
	static private enum InternalState {
		WRITE_LEFT, WRITE_RIGHT, READ
	}

	private final StreamSorter<E> leftChild, rightChild;
	private volatile E leftCache, rightCache;
	private volatile InternalState internalState;

	/**
	 * Creates a new instance in {@link State#WRITE} state that is based on two child sorters.
	 * @param leftChild the left child
	 * @param rightChild the right child
	 * @throws NullPointerException if one of the given children is {@code null}
	 */
	public MultiThreadSorter(final StreamSorter<E> leftChild, final StreamSorter<E> rightChild) {
		if (leftChild == null || rightChild == null)
			throw new NullPointerException();

		this.leftChild = leftChild;
		this.rightChild = rightChild;
		this.internalState = InternalState.WRITE_LEFT;
		
	}

	/**
	 * {@inheritDoc}
	 */
	public State getState() {
		return this.internalState == InternalState.READ ? State.READ : State.WRITE;
	}

	/**
	 * Returns the next element of the left child, and replenishes it's cache.
	 * @return the left child's next element, or {@code null}
	 */
	private E nextLeftElement() {
		final E result = this.leftCache;
		try {
			this.leftCache = this.leftChild.read();
		} catch (final IllegalStateException exception) {
			this.leftCache = null;
		}
		return result;
	}

	/**
	 * Returns the next element of the right child, and replenishes it's cache.
	 * @return the right child's next element, or {@code null}
	 */
	private E nextRightElement() {
		final E result = this.rightCache;
		try {
			this.rightCache = this.rightChild.read();
		} catch (final IllegalStateException exception) {
			this.rightCache = null;
		}
		return result;
	}

	/**
	 * {@inheritDoc}
	 * @throws IllegalStateException {@inheritDoc}
	 */
	public synchronized E read() {
		if (this.getState() != State.READ)
			throw new IllegalStateException();

		final E result;
		if (this.leftCache == null) {
			result = this.nextRightElement();
		} else if (this.rightCache == null) {
			result = this.nextLeftElement();
		} else if (this.leftCache.compareTo(this.rightCache) >= 0) {
			result = this.nextRightElement();
		} else {
			result = this.nextLeftElement();
		}

		if (this.leftCache == null & this.rightCache == null) {
			this.internalState = InternalState.WRITE_LEFT;
		}

		return result;
	}

	/**
	 * {@inheritDoc}
	 */
	public synchronized void reset() {
		this.leftChild.reset();
		this.rightChild.reset();
		this.leftCache = null;
		this.rightCache = null;
		this.internalState = InternalState.WRITE_LEFT;
	}

	/**
	 * {@inheritDoc}
	 * @throws IllegalStateException {@inheritDoc}
	 */
	public void sort() {
		if (this.getState() != State.WRITE)
			throw new IllegalStateException();
		// of the read() operation (for the caches) and a possible exception (for a rethrow)
		// after resynchronisation. Implement the method using either an indebted semaphore
		// and shared references, or using futures.
		
		//TODO nicht futures, mit frische threads 
		
		FutureTask<E> sortLeftFuture = new FutureTask<E>(()->{
			this.leftChild.sort();
			try {
				return this.leftChild.read();
			} catch (final IllegalStateException exception) {
				return null;
			}
		});

		FutureTask<E> sortRightFuture = new FutureTask<E>(()->{
				
			this.rightChild.sort();
			try {
				return this.rightChild.read();
			} catch (final IllegalStateException exception) {
				return null;
			}
		});
		
		new Thread(sortLeftFuture).start();
		new Thread(sortRightFuture).start();
	
		try {
			this.leftCache = getUninterruptibly(sortLeftFuture);
			this.rightCache= getUninterruptibly(sortRightFuture);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if(cause instanceof Error) throw (Error) cause;		 
			if(cause instanceof RuntimeException) throw (RuntimeException) cause;
			throw new AssertionError(cause);
		} finally {
			sortLeftFuture.cancel(true);
			sortRightFuture.cancel(true);
		}
		if (this.leftCache != null | this.rightCache != null) {
			this.internalState = InternalState.READ;
		}
	}

	/**
	 * {@inheritDoc}
	 * @throws NullPointerException {@inheritDoc}
	 * @throws IllegalStateException {@inheritDoc}
	 */
	public synchronized void write(final E element) {
		switch (this.internalState) {
		case WRITE_LEFT: {
			this.leftChild.write(element);
			this.internalState = InternalState.WRITE_RIGHT;
			break;
		}
		case WRITE_RIGHT: {
			this.rightChild.write(element);
			this.internalState = InternalState.WRITE_LEFT;
			break;
		}
		default: {
			throw new IllegalStateException();
		}
		}
	}
	/**
	 * Repeat the (interruptible) blocking {@link Future#get()} operation until it ends without
	 * being interrupted. Note that this implementation preserves the interrupt-status of it's
	 * thread, i.e. interruptions are only delayed, not ignored completely.
	 * @param future the future
	 * @throws NullPointerException if any of the given arguments is {@code null}
	 * @throws ExecutionException if there is a problem during the given future's execution
	 */
	static public <T> T getUninterruptibly (final Future<T> future) throws NullPointerException, ExecutionException {
		T result;

		boolean interrupted = false;
		try {
			while (true) {
				try {
					result = future.get();
					break;
				} catch (final InterruptedException exception) {
					interrupted = true;
				}
			}
		} finally {
			if (interrupted) Thread.currentThread().interrupt();
		}

		return result;
	}
}