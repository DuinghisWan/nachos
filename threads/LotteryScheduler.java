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
public class LotteryScheduler extends PriorityScheduler {
    /**
     * Allocate a new lottery scheduler.
     */
    public LotteryScheduler() {
    }

    /**
     * The minimum priority that a thread can have.
     */
    public static final int priorityMinimum = 0;

    /**
     * The maximum priority that a thread can have.
     */
    public static final int priorityMaximum = Integer.MAX_VALUE;

    /**
     * Allocate a new lottery thread queue.
     *
     * @param transferPriority <tt>true</tt> if this queue should transfer tickets
     *                         from waiting threads to the owning thread.
     * @return a new lottery thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
        return new LotteryQueue(transferPriority);
    }

    protected class LotteryQueue extends PriorityQueue {
        LotteryQueue(boolean transferPriority) {
            super(transferPriority);

            this.transferPriority = transferPriority;
            if (transferPriority) {
                this.pQueue = new java.util.PriorityQueue<LotteryThreadState>(11, new LotteryDonationComparator());
            } else {
                this.pQueue = new java.util.PriorityQueue<LotteryThreadState>(11, new LotteryComparator());
            }
        }

        /**
		 * Return the next thread that <tt>nextThread()</tt> would return, without
		 * modifying the state of this queue.
		 *
		 * @return the next thread that <tt>nextThread()</tt> would return.
		 */
        @Override
        public LotteryThreadState pickNextThread() {
			return pQueue.peek();
        }
        
        /**
		 * Add a thread to this queue.
		 * 
		 * @param thread the thread to add to the queue.
		 */
		public void addThread(LotteryThreadState thread) {
			pQueue.add(thread);
		}

        /**
         * The priority queue for the thread states
         */
        public java.util.PriorityQueue<LotteryThreadState> pQueue;
    }

    protected class LotteryThreadState extends ThreadState {
        public LotteryThreadState(KThread thread) {
            super(thread);
            this.previousQueues = new LinkedList<LotteryQueue>();
            setPriority(priorityDefault);
        }

        /**
         * Calculates the effective priority of this thread.
         * For lottery, a sum is used instead of a amaximum calculation.
         * 
         * @return the effective priority of the associated thread.
         */
        @Override
        public int getEffectivePriority() {
            System.out.println("New effectivePriority");
            // Only recalculate if value isnt cached
			if (effectivePriority == priorityMinimum - 1) {

				// Iterate over list of old queues this thread is still in
				for (LotteryQueue previousQueue : previousQueues) {

					// Iterate over the states in those queues
					for (LotteryThreadState threadState : previousQueue.pQueue) {

						// Get the higher priority from previous queues and set the effective priority
						// to it
						if (threadState != this) {
							int otherEffectivePriority = threadState.getEffectivePriority();
							effectivePriority += otherEffectivePriority;
						}
					}
                }
            }

            // Integer overflow, reset to max
            if (effectivePriority < priorityMinimum - 1) {
                effectivePriority = priorityMaximum;
            }

			// If the calculated priority was less than the current priority, use the
			// current priority
			effectivePriority = Math.max(effectivePriority, priority);

			return effectivePriority;          
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
		 * Compares this ThreadState's priority with another's.
		 * 
		 * @param other the ThreadState to compare against.
		 * @return -1 if 'this' is higher priority, 0 for equal priority, and 1 for
		 *         lower priority.
		 */
		public int compareTo(LotteryThreadState other) {
			return new Integer(other.getPriority()).compareTo(this.getPriority());
		}

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
