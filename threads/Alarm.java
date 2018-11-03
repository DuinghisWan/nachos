package nachos.threads;

import java.util.PriorityQueue;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback
     * 
     * <p>
     * <b>Note</b>: Nachos will not function correctly with more than one alarm.
     */

    public PriorityQueue<AlarmThread> q = new PriorityQueue<AlarmThread>();

    public Alarm() {
        Machine.timer().setInterruptHandler(new Runnable() {
            public void run() {
                timerInterrupt();
            }
        });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current thread
     * to yield, forcing a context switch if there is another thread that should be
     * run.
     */
    public void timerInterrupt() {
        //Machine current time
        long time = Machine.timer().getTime();

        Machine.interrupt().disable();

        
        while(!q.isEmpty() && q.peek().waitTime <= time){
            System.out.println(q.peek().toString());
            AlarmThread thread = q.remove();
            thread.kthread.ready(); 
        }

        Machine.interrupt().enable();
        
        KThread.yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks, waking it up in
     * the timer interrupt handler. The thread must be woken up (placed in the
     * scheduler ready set) during the first timer interrupt where
     *
     * <p>
     * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
     *
     * @param x the minimum number of clock ticks to wait.
     *
     * @see nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
        // for now, cheat just to get something working (busy waiting is bad)
        Machine.interrupt().disable();

        long wakeTime = Machine.timer().getTime() + x;
        q.add(new AlarmThread(KThread.currentThread(), wakeTime));
        KThread.sleep();

        Machine.interrupt().enable();

        //while (wakeTime > Machine.timer().getTime())
        KThread.yield();
    }
    /**
     * A class that creates a constructor to initialize variables for a current thread
     */
    class AlarmThread implements Comparable<AlarmThread>{
        KThread kthread;
        long waitTime;

        public AlarmThread(KThread kt, long t){
            kthread = kt;
            waitTime = t;
        }

        @Override
        public int compareTo(AlarmThread test){
            return(new Long(this.waitTime).compareTo(test.waitTime));
        }

    }
}
