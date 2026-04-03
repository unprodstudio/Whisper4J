package dev.cadindie.whisper4j.threading;

import dev.cadindie.whisper4j.Whisper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class WhisperPool {
    private final BlockingQueue<TranscriptionTask> queue = new LinkedBlockingQueue<>();
    private final List<Thread> workers = new ArrayList<>();
    private final Whisper whisper;

    public WhisperPool(Whisper whisper, int maxthreads) {
        this.whisper = whisper;
        for (int i = 0; i < maxthreads; i++) {
            Thread worker = new Thread(new Worker(), "WhisperWorker-" + i);
            worker.start();
            workers.add(worker);
        }
    }

    public void submit(TranscriptionTask task) {
        queue.add(task);
    }

    public void shutdown() {
        for (Thread worker : workers) {
            worker.interrupt();
        }
    }

    private class Worker implements Runnable {
        Whisper _whisper = whisper;

        @Override
        public void run() {
            synchronized (WhisperPool.this) {
                _whisper.initialize();
            }

            while (true) {
                try {
                    TranscriptionTask task = queue.take();
                    String result = _whisper.transcribeRaw(task.audio());
                    task.callback().accept(result);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
