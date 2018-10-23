package nachos.threads;

import nachos.machine.*;
import nachos.threads.Condition;
import nachos.threads.Lock;
import nachos.threads.KThread;

import java.util.ArrayList;

import java.util.LinkedList;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {

    private Lock mutex;
    
    private Condition okToListen;
    private int waitingListeners = 0;

    private Condition okToSpeak;
    private int waitingSpeakers = 0;


    private Condition messageReady;
    private int currentMessage = 0;

    private boolean validMessage = false;

    /**
     * Allocate a new communicator.
     */
    public Communicator() {
        this.mutex = new Lock();
        this.okToListen = new Condition(mutex);
        this.okToSpeak = new Condition(mutex);
        this.messageReady = new Condition(mutex);
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param word the integer to transfer.
     */
    public void speak(int word) {
        mutex.acquire();
        waitingSpeakers++;

        if (waitingListeners == 0) {
            okToSpeak.sleep();
        }

        if (!mutex.isHeldByCurrentThread()) {
            mutex.acquire();
        }

        currentMessage = word;
        validMessage = true;
        waitingSpeakers--;
        
        messageReady.wake();

        mutex.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return the
     * <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return the integer transferred.
     */
    public int listen() {
        mutex.acquire();
        waitingListeners++;
        
        while (waitingSpeakers == 0) {
            okToListen.sleep();
        }
        
        while(!validMessage) {
            okToSpeak.wake();
            messageReady.sleep();
        }

        if (!mutex.isHeldByCurrentThread()) {
            mutex.acquire();
        }

        waitingListeners--;
        int return_message = this.currentMessage;
        validMessage = false;
        
        mutex.release();
        return return_message;
    }
}
