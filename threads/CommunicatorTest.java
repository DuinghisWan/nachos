package nachos.threads;

import nachos.machine.*;
import nachos.threads.Communicator;
import nachos.threads.KThread;

import java.util.ArrayList;

public class CommunicatorTest {
    /**
     * Test this class in a variety of situations Runs automatically when
     * ThreadedKernel is started
     */
    public static void test() {
        CommunicatorTest tester = new CommunicatorTest();

        tester.testSingle();
    }

    private class RunSpeaker implements Runnable {
        private int word;
        private Communicator comm;

        public RunSpeaker(Communicator comm, int word) {
            this.word = word;
        }

        public void run() {
            comm.speak(word);
        }
    }

    private class RunListener implements Runnable {
        private int word;
        private Communicator comm;

        public RunListener(Communicator comm) {
            this.comm = comm;
        }

        public void run() {
            this.word = comm.listen();
        }

        public int getWord() {
            return this.word;
        }
    }

    void testSingle() {
        Communicator comm = new Communicator();

        RunSpeaker runSpeaker = new RunSpeaker(comm, 100);
        RunListener runListener = new RunListener(comm);

        KThread speaker = new KThread(runSpeaker);
        KThread listener = new KThread(runListener);

        speaker.join();
        listener.join();

        Lib.assertTrue(runListener.getWord() == 0,
                String.format("Single Test: [ Expected: 1000, Actual: %d ]", runListener.getWord()));
    }
}