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
    
    private Condition readyToListen;
    private int waitingListeners = 0;
    
    private Condition readyToSpeak;

    private int currentMessage = 0;
    private boolean validMessage = false;

    /**
     * Allocate a new communicator.
     */
    public Communicator() {
        this.mutex = new Lock();
        this.readyToListen = new Condition(mutex);
        this.readyToSpeak = new Condition(mutex);
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

        try {
            // Wait for there to be a listener to speak to
            while (waitingListeners == 0) {
                readyToSpeak.sleep();
            }

            // Send a message to the listener
            currentMessage = word;
            validMessage = true;
            readyToListen.wake();
        } finally {
            mutex.release();
        }
    }

    /**
     * Wait for a thread to speak through this communicator, and then return the
     * <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return the integer transferred.
     */
    public int listen() {
        mutex.acquire();

        try {
            waitingListeners++;

            // Wait for a speaker to speak
            while (!validMessage) {
                readyToSpeak.wake();
                readyToListen.sleep();
            }

            // Read message and return it
            int return_message = this.currentMessage;
            validMessage = false;
            waitingListeners--;

            return return_message;
        } finally {
            mutex.release();
        }
    }
}
