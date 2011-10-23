package sg.util;

import java.util.concurrent.*;
import java.util.*;
import java.io.*;

public class QueueBasedMapRedImpl<DATA, RESULT>
    implements
    MapReduce<DATA, RESULT>,
    Waitable
{
    static final boolean VERBOSE = Boolean.getBoolean("verbose");
    static final PrintStream LOG = System.err;

    private final int                   concurrentRequests;
    private final ResultHandler<RESULT> resultHandler;
    private final Wroker<DATA, RESULT>  worker;
    private final BlockingQueue<DATA>   queue;
    private final DATA                  poisonMsg;
    private final CountDownLatch        consumersFinishedSignal;
    private final long                  durationMS;

    public QueueBasedMapRedImpl(
        int                   concurrentRequests,
        ResultHandler<RESULT> resultHandler,
        Wroker<DATA, RESULT>  worker,
        DATA                  poisonMsg,
        long                  durationMS)
    {

        if(concurrentRequests < 1) throw new IllegalArgumentException("concurrentRequests is invalid, "+concurrentRequests);
        if(resultHandler == null ) throw new IllegalArgumentException("resultHandler can not be null");
        if(worker        == null ) throw new IllegalArgumentException("worker can not be null");
        if(poisonMsg     == null ) throw new IllegalArgumentException("poisonMsg can not be null");

        this.concurrentRequests= concurrentRequests;
        this.resultHandler     = resultHandler;
        this.worker            = worker;
        this.poisonMsg         = poisonMsg;
        this.durationMS        = durationMS;

        this.queue = new LinkedBlockingQueue<DATA>();

        for(int i=0; i < concurrentRequests; i++) {
            new Thread(new Consumer(this.queue)).start();
        }

        consumersFinishedSignal = new CountDownLatch(concurrentRequests);
    }

    boolean timeover(long tm) {
      long elapsed = System.currentTimeMillis() - tm;
      boolean flag = false;

      if(this.durationMS > 0)flag=elapsed >= this.durationMS;

      if(VERBOSE)LOG.printf("%s elapsed=%s ms, durationMS=%s ms, flag=%s%n",
        Thread.currentThread(), elapsed, this.durationMS, flag);

      return  flag;
    }

    public void shutdown() {
        // one POISON MSG for each consumer thread.
        for(int i=0; i < concurrentRequests; i++){
          OnNextData(poisonMsg);
        }
    }

    public void waitTermination(){
        // wait for all consumer threads to terminate
        try{consumersFinishedSignal.await();}
        catch(InterruptedException e){e.printStackTrace();}
    }

    public void setTerminateEvent(final TerminateEvent terminateEvent){
      new Thread(){
        public void run(){
          waitTermination();
          terminateEvent.OnTerminate();
        }
      }.start();
    }

    public void OnNextData(final DATA input) {
        try {queue.put(input);}
        catch(InterruptedException e) {e.printStackTrace(System.err);}
    }

    class Consumer implements Runnable {
        private final BlockingQueue<DATA> queue;
        private long startms = 0;

        Consumer(BlockingQueue<DATA> q) { queue = q; }

        public void run() {
            try {
                while (true) {
                    DATA msg = queue.take();
                    if(startms==0)startms=System.currentTimeMillis();
                    if(msg == poisonMsg  /*match by reference*/ || timeover(startms)) {
                      consumersFinishedSignal.countDown();
                      break;
                    }
                    resultHandler.onResult(worker.doWork(msg));
                }
            } catch (InterruptedException ex) {ex.printStackTrace(System.err);}
        }
    }
}