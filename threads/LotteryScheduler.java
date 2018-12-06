package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.LinkedList;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking the
 * maximum).
 */
public class LotteryScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public LotteryScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 *
	 * @param transferPriority <tt>true</tt> if this queue should transfer priority
	 *                         from waiting threads to the owning thread.
	 * @return a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new LotteryQueue(transferPriority);
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
	public static final int priorityMinimum = 1;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = Integer.MAX_VALUE;

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
	protected class LotteryQueue extends ThreadQueue {
		LotteryQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;

			// Compare using effective priorities if transferPriority is true
			if (transferPriority) {
				this.pQueue = new java.util.PriorityQueue<ThreadState>(11, new LotteryDonationComparator());
			} else {
				this.pQueue = new java.util.PriorityQueue<ThreadState>(11, new LotteryComparator());
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
	protected class ThreadState {
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 *
		 * @param thread the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;
			this.waitQueue = null;
			this.previousQueues = new LinkedList<LotteryQueue>();
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
				for (LotteryQueue previousQueue : previousQueues) {

					// Iterate over the states in those queues
					for (ThreadState threadState : previousQueue.pQueue) {

						// Get the higher priority from previous queues and set the effective priority
						// to it
						if (threadState != this) {
							int otherEffectivePriority = threadState.getEffectivePriority();
							effectivePriority += otherEffectivePriority;
						}
					}
				}
				
				effectivePriority += priority;
            }

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
		public void waitForAccess(LotteryQueue waitQueue) {
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
		public void acquire(LotteryQueue waitQueue) {
			previousQueues.add(waitQueue);
		}

		/**
		 * Called when this thread is no longer in a waitingQueue anymore.
		 * 
		 * @param waitQueue the queue that no longer contains this thread.
		 */
		public void removeQueue(LotteryQueue waitQueue) {
			previousQueues.remove(waitQueue);
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
		protected LotteryQueue waitQueue;

		/**
		 * Queues that the thread has been waiting on.
		 */
		protected LinkedList<LotteryQueue> previousQueues;
	}

    /**
     * ThreadState comparator for the lottery scheduler without donation.
     */
    public static class LotteryComparator implements Comparator<ThreadState> {

		/**
		 * Compares two ThreadStates based on their priorities with lottery.
		 * 
		 * @param first  The first thread state to be compared.
		 * @param second The second thread state to be compared.
		 * @return -1 if the first thread has a higher priority, 0 if an equal priority,
		 *         and 1 if a lower priority.
		 */
		@Override
		public int compare(ThreadState first, ThreadState second) {
            Random rand = new Random();

            int sum = first.getPriority() + second.getPriority();
            int choice = rand.nextInt(sum+1);
            
            if (choice > first.getPriority()) {
                return -1;
            } else if (choice == first.getPriority()) {
                return 0;
            } else {
                return 1;
            }
		}
    }

    /**
     * ThreadState comparator for the lottery shceduler with donation.
     */
    public static class LotteryDonationComparator implements Comparator<ThreadState> {

		/**
		 * Compares two ThreadStates based on their effective priorities with lottery.
		 * 
		 * @param first  The first thread state to be compared.
		 * @param second The second thread state to be compared.
		 * @return -1 if the first thread has a higher priority, 0 if an equal priority,
		 *         and 1 if a lower priority.
		 */
		@Override
		public int compare(ThreadState first, ThreadState second) {
            Random rand = new Random();
            
            int sum = first.getEffectivePriority() + second.getEffectivePriority();
            int choice = rand.nextInt(sum+1);

            if (choice > first.getEffectivePriority()) {
                return -1;
            } else if (choice == first.getEffectivePriority()) {
                return 0;
            } else {
                return 1;
            }
		}
    }
}
