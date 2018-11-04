package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Comparator;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the thread
 * that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has the
 * potential to starve a thread if there's always a thread waiting with higher
 * priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 *
	 * @param transferPriority <tt>true</tt> if this queue should transfer priority
	 *                         from waiting threads to the owning thread.
	 * @return a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum && priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			return false;

		setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			return false;

		setPriority(thread, priority - 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;
	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;

	/**
	 * Return the scheduling state of the specified thread.
	 *
	 * @param thread the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue {
		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;

			// Compare using effective priorities if transferPriority is true
			if (transferPriority) {
				this.pQueue = new java.util.PriorityQueue<ThreadState>(11, new DonationComparator());
			} else {
				this.pQueue = new java.util.PriorityQueue<ThreadState>(11);
			}
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());

			pQueue.add(getThreadState(thread));

			for (ThreadState threadState : pQueue) {
				threadState.resetEffectivePriority();
			}

			getThreadState(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());

			if (pickNextThread() == null) {
				owner = null;
				return null;
			}

			owner = pQueue.poll();
			acquire(owner.getThread());
			return owner.getThread();
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return, without
		 * modifying the state of this queue.
		 *
		 * @return the next thread that <tt>nextThread()</tt> would return.
		 */
		public ThreadState pickNextThread() {
			return pQueue.peek();
		}

		/**
		 * Add a thread to this queue.
		 * 
		 * @param thread the thread to add to the queue.
		 */
		public void addThread(ThreadState thread) {
			pQueue.add(thread);
		}

		/**
		 * Prints the contents of the queue.
		 */
		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());

			System.out.println("------ Thread Queue ------");

			for (ThreadState threadState : pQueue) {
				System.out.println(threadState.toString());
			}

			System.out.println("--------------------------");
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting threads to
		 * the owning thread.
		 */
		public boolean transferPriority;

		/**
		 * The priority queue for the thread states
		 */
		public java.util.PriorityQueue<ThreadState> pQueue;

		/**
		 * The current owner of the thread queue
		 */
		protected ThreadState owner = null;
	}

	/**
	 * The scheduling state of a thread. This should include the thread's priority,
	 * its effective priority, any objects it owns, and the queue it's waiting for,
	 * if any.
	 *
	 * @see nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState implements Comparable<ThreadState> {
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 *
		 * @param thread the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;
			this.waitQueue = null;
			this.previousQueues = new LinkedList<PriorityQueue>();
			setPriority(priorityDefault);
		}

		/**
		 * Return the priority of the associated thread.
		 *
		 * @return the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 *
		 * @return the effective priority of the associated thread.
		 */
		public int getEffectivePriority() {
			// Only recalculate if value isnt cached
			if (effectivePriority == priorityMinimum - 1) {

				// Iterate over list of old queues this thread is still in
				for (PriorityQueue previousQueue : previousQueues) {

					// Iterate over the states in those queues
					for (ThreadState threadState : previousQueue.pQueue) {

						// Get the higher priority from previous queues and set the effective priority
						// to it
						if (threadState != this) {
							int otherEffectivePriority = threadState.getEffectivePriority();
							if (otherEffectivePriority > effectivePriority) {
								effectivePriority = otherEffectivePriority;
							}
						}
					}
				}
			}

			// If the calculated priority was less than the current priority, use the
			// current priority
			effectivePriority = Math.max(effectivePriority, priority);

			return effectivePriority;
		}

		/**
		 * Resets the effective priority to its sentinel value
		 */
		public void resetEffectivePriority() {
			this.effectivePriority = priorityMinimum - 1;
		}

		/**
		 * Return the associated thread.
		 * 
		 * @return the associated thread.
		 */
		public KThread getThread() {
			return thread;
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param priority the new priority.
		 */
		public void setPriority(int priority) {
			if (this.priority == priority) {
				return;
			}

			// Ensure priority falls between priorityMinimum and priorityMaximum
			this.priority = Math.min(Math.max(priority, priorityMinimum), priorityMaximum);

			// Old effective priority is now invalid
			resetEffectivePriority();
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is the
		 * associated thread) is invoked on the specified priority queue. The associated
		 * thread is therefore waiting for access to the resource guarded by
		 * <tt>waitQueue</tt>. This method is only called if the associated thread
		 * cannot immediately obtain access.
		 *
		 * @param waitQueue the queue that the associated thread is now waiting on.
		 *
		 * @see nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			// waitQueues.add(waitQueue);
			this.waitQueue = waitQueue;

		}

		/**
		 * Called when the associated thread has acquired access to whatever is guarded
		 * by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 *
		 * @see nachos.threads.ThreadQueue#acquire
		 * @see nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) {
			previousQueues.add(waitQueue);
		}

		/**
		 * Called when this thread is no longer in a waitingQueue anymore.
		 * 
		 * @param waitQueue the queue that no longer contains this thread.
		 */
		public void removeQueue(PriorityQueue waitQueue) {
			previousQueues.remove(waitQueue);
		}

		/**
		 * Compares this ThreadState's priority with another's.
		 * 
		 * @param other the ThreadState to compare against.
		 * @return -1 if 'this' is higher priority, 0 for equal priority, and 1 for
		 *         lower priority.
		 */
		@Override
		public int compareTo(ThreadState other) {
			return new Integer(other.getPriority()).compareTo(this.getPriority());
		}

		/**
		 * Adds on to KThread's <tt>toString</tt> by also showing the priority and
		 * effective priority of the thread.
		 * 
		 * @return the formatted string.
		 * 
		 * @see nachos.threads.KThread#toString
		 */
		@Override
		public String toString() {
			return String.format(thread.toString() + "\t Pri: %d\t Eff: %d", this.getPriority(),
					this.getEffectivePriority());
		}

		/**
		 * The thread with which this object is associated.
		 */
		protected KThread thread;

		/**
		 * The priority of the associated thread.
		 */
		protected int priority;

		/**
		 * The effective priority of the associated thread. Defaults to a sentinel of
		 * the minimum priority - 1.
		 */
		protected int effectivePriority = priorityMinimum - 1;

		/**
		 * The queue the thread is currently waiting on.
		 */
		protected PriorityQueue waitQueue;

		/**
		 * Queues that the thread has been waiting on.
		 */
		protected LinkedList<PriorityQueue> previousQueues;
	}

	/**
	 * Comparator for effective priorities, used for comparing ThreadStates with
	 * priority donation enabled.
	 */
	public static class DonationComparator implements Comparator<ThreadState> {

		/**
		 * Compares two ThreadStates based on their effective priorities.
		 * 
		 * @param first  The first thread state to be compared.
		 * @param second The second thread state to be compared.
		 * @return -1 if the first thread has a higher priority, 0 if an equal priority,
		 *         and 1 if a lower priority.
		 */
		@Override
		public int compare(ThreadState first, ThreadState second) {
			return new Integer(second.getEffectivePriority()).compareTo(first.getEffectivePriority());
		}
	}
}
