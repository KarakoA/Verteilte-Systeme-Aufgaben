package de.htw.ds.sort;

import de.sb.toolbox.Copyright;


/**
 * Multi threaded merge sorter implementation that distributes elements evenly over two child
 * sorters, sorts them separately using two separate threads, and then merges the two sorted
 * children's elements during read requests. Note that this implementation is able to scale its
 * workload over two processor cores, and even more if such sorters are stacked!
 * @param <E> the element type to be sorted in naturally ascending order
 */
@Copyright(year=2010, holders="Sascha Baumeister")
public class MultiThreadSorterSkeleton<E extends Comparable<E>> implements StreamSorter<E> {
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
	public MultiThreadSorterSkeleton (final StreamSorter<E> leftChild, final StreamSorter<E> rightChild) {
		if (leftChild == null || rightChild == null) throw new NullPointerException();

		this.leftChild = leftChild;
		this.rightChild = rightChild;
		this.internalState = InternalState.WRITE_LEFT;
	}


	/**
	 * {@inheritDoc}
	 */
	public State getState () {
		return this.internalState == InternalState.READ ? State.READ : State.WRITE;
	}


	/**
	 * Returns the next element of the left child, and replenishes it's cache.
	 * @return the left child's next element, or {@code null}
	 */
	private E nextLeftElement () {
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
	private E nextRightElement () {
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
	public synchronized E read () {
		if (this.getState() != State.READ) throw new IllegalStateException();

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
	public synchronized void reset () {
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
	public void sort () {
		if (this.getState() != State.WRITE) throw new IllegalStateException();

		// TODO: Scale this implementation so that the two sort requests and associated
		// reads are distributed into two separate threads, which should be advantageous on
		// Multi-Core nodes. Code this keeping in mind that the sort method may throw
		// runtime exceptions or errors. Therefore, make sure you can access both the result
		// of the read() operation (for the caches) and a possible exception (for a rethrow)
		// after resynchronisation. Implement the method using either an indebted semaphore
		// and shared references, or using futures.
		this.leftChild.sort();
		try {
			this.leftCache = this.leftChild.read();
		} catch (final IllegalStateException exception) {
			this.leftCache = null;
		}

		this.rightChild.sort();
		try {
			this.rightCache = this.rightChild.read();
		} catch (final IllegalStateException exception) {
			this.rightCache = null;
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
	public synchronized void write (final E element) {
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
}