package sg.util;

import java.util.concurrent.*;
import java.util.*;

public class MapReduceImpl<DATA, RESULT> implements MapReduce<DATA, RESULT> {
    private final int concurrentRequests;
    private final ExecutorService executor;
    private final ExecutorCompletionService<RESULT> ecs;
    private final ResultHandler<RESULT> resultHandler;
    private final Wroker<DATA, RESULT> worker;

    private List<Callable<RESULT>> callables;

    public MapReduceImpl(
      int                   concurrentRequests,
      ResultHandler<RESULT> resultHandler,
      Wroker<DATA, RESULT>  worker)
    {
        if(concurrentRequests < 1 ) throw new IllegalArgumentException("concurrentRequests is invalid, "+concurrentRequests);
        if(resultHandler == null) throw new IllegalArgumentException("resultHandler can not be null");
        if(worker == null) throw new IllegalArgumentException("worker can not be null");

        this.concurrentRequests= concurrentRequests;
        this.resultHandler = resultHandler;
        this.worker = worker;

        this.executor =  Executors.newFixedThreadPool(this.concurrentRequests);
        this.ecs = new ExecutorCompletionService<RESULT>(this.executor);
    }

    public void shutdown() {
        submit(); // if anything left

        if(executor!=null) {
            executor.shutdown();
        }
    }

    public void OnNextData(final DATA input) {
        if(callables==null)callables=new ArrayList<Callable<RESULT>>();

        callables.add(
          new Callable<RESULT>() {
              public RESULT call() {
                  return doWork(input);
              }
          }
        );

        if(callables.size() == concurrentRequests) {
            submit();
        }
    }

    void onResult(final RESULT result) {resultHandler.onResult(result);}

    RESULT doWork(final DATA input) {return worker.doWork(input);}

    void submit() {
        if(callables == null) return;

        int sz = callables.size();
        final CyclicBarrier barrier = new CyclicBarrier(sz);

        for(int i=0; i < sz; i++) {
            final Callable<RESULT> c = callables.get(i);
            ecs.submit(
              new Callable<RESULT>() {
                  public RESULT call() {
                      try {
                          barrier.await();  // wait for all threads to comeup to this point
                          return c.call();
                      } catch (Exception e) {throw new RuntimeException(e);}
                  }
              }
            );
        }

        for(int i=0; i < sz; i++) {
          try{onResult(ecs.take().get());}
          catch(Exception e){e.printStackTrace(System.err);}
        }

        callables = null; // reset callables
    }
}
