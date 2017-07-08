package de.htw.ds.sort;

import de.sb.toolbox.Copyright;


/**
 * Interface describing stream sorters that can sort tremendous amounts of objects without
 * necessarily loading them all into storage. The elements are sorted into ascending order,
 * depending on their natural ordering.<br />
 * Note that stream sorters are state bound objects! In {@link State#WRITE} state only
 * {@link #getState()}, {@link #write(Comparable)}, {@link #sort()}, or
 * {@link #reset()} messages are permitted. The {@link #sort()} message switches a sorter
 * into {@link State#READ} state. After this, only {@link #getState()},
 * {@link #read()} or {@link #reset()} messages are permitted until the final sorted
 * element has been read, which switches the sorter back into {@link State#WRITE} state.
 * @param <E> the element type to be sorted in naturally ascending order
 */
@Copyright(year=2010, holders="Sascha Baumeister")
public interface StreamSorter<E extends Comparable<E>> {

	/**
	 * Describes the two states defined for stream sorters.
	 */
	static public enum State {
		/**
		 * In this state a stream sorter may only be written, sorted, or reset.
		 */
		WRITE,

		/**
		 * In this state a stream sorter may only be read, sorted, or reset.
		 */
		READ
	}


	/**
	 * Returns the current state.
	 * @return the state
	 * @throws IllegalStateException if there is a problem with the underlying data
	 */
	public State getState ();


	/**
	 * Returns the next element from sorted storage, and switches the receiver into
	 * {@link State#WRITE} state just before the last element is returned.
	 * @return the next element in sort order
	 * @throws IllegalStateException if the sorter is not in {@link State#READ} state, or if
	 *         there is a problem with the underlying data
	 */
	E read ();


	/**
	 * Clears the receiver's sorting storage and resets it into {@link State#WRITE} state,
	 * regardless of the state it's currently in.
	 */
	void reset ();


	/**
	 * Sorts the elements in sorting storage, and switches the receiver into {@link State#READ}
	 * state if there is any element to be read after sorting.
	 * @throws IllegalStateException if the sorter is not in {@link State#WRITE} state, or if
	 *         there is a problem with the underlying data
	 */
	void sort ();


	/**
	 * Writes the given element to it's sorting storage.
	 * @param element the element to be stored
	 * @throws NullPointerException if the given element is {@code null}
	 * @throws IllegalStateException if the sorter is not in {@link State#WRITE} state, or if
	 *         there is a problem with the underlying data
	 */
	void write (E element);
}