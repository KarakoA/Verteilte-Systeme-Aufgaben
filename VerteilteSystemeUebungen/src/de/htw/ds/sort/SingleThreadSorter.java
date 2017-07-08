package de.htw.ds.sort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import de.sb.toolbox.Copyright;


/**
 * Single threaded sorter implementation that collects elements in a list and sorts them using the
 * underlying merge sort implementation of lists. Note that this implementation implies that such a
 * sorter cannot scale its workload over more than one processor core, and additionally all elements
 * are stored within the RAM of a single process.
 * @param <E> the element type to be sorted in naturally ascending order
 */
@Copyright(year=2010, holders="Sascha Baumeister")
public class SingleThreadSorter<E extends Comparable<E>> implements StreamSorter<E> {
	private final List<E> elements;
	private volatile int readIndex;


	/**
	 * Creates a new instance in {@link StreamSorter.State#WRITE} state.
	 */
	public SingleThreadSorter () {
		this.elements = new ArrayList<E>();
		this.readIndex = -1;
	}


	/**
	 * {@inheritDoc}
	 */
	public State getState () {
		return this.readIndex == -1 ? State.WRITE : State.READ;
	}


	/**
	 * {@inheritDoc}
	 * @throws IllegalStateException {@inheritDoc}
	 */
	public synchronized E read () {
		if (this.getState() != State.READ) throw new IllegalStateException();

		final E result = this.elements.get(this.readIndex++);
		if (this.readIndex == this.elements.size()) this.readIndex = -1;
		return result;
	}


	/**
	 * {@inheritDoc}
	 */
	public synchronized void reset () {
		this.elements.clear();
		this.readIndex = -1;
	}


	/**
	 * {@inheritDoc}
	 * @throws IllegalStateException {@inheritDoc}
	 */
	public synchronized void sort () {
		if (this.getState() != State.WRITE) throw new IllegalStateException();

		if (!this.elements.isEmpty()) {
			Collections.sort(this.elements);
			this.readIndex = 0;
		}
	}


	/**
	 * {@inheritDoc}
	 * @throws NullPointerException {@inheritDoc}
	 * @throws IllegalStateException {@inheritDoc}
	 */
	public synchronized void write (final E element) {
		if (element == null) throw new NullPointerException();
		if (this.getState() != State.WRITE) throw new IllegalStateException();
		this.elements.add(element);
	}
}