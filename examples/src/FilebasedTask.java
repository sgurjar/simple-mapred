import sg.util.*;
import java.util.*;
import java.io.*;

public class FilebasedTask
  implements
    ResultHandler<Map<String, Object>>,
    Wroker<String, Map<String, Object>>
{
  static final boolean VERBOSE = Boolean.getBoolean("verbose");
  static final PrintStream LOG = System.err;

  static final String POISON_MSG = new String(UUID.randomUUID().toString());

  private final MapReduce<String, Map<String, Object>> mapper;
  private File basedir;
  private boolean recursive;
  private FileFilter fileFilter = new FileFilter(){
    public boolean accept(File pathname){
      if(pathname.isDirectory()){
        if(recursive)pathname.listFiles(fileFilter);
      }else if(pathname.isFile()){
        if(filter(pathname)){
          mapper.OnNextData(pathname.getAbsolutePath());
        }
      } else {
        // dont know what it is
        LOG.println("unknown file type, " + pathname);
      }
      return false; // what we return doesnt matter
    }
  };
  long durationMS, startMS;

  public FilebasedTask(int concurrentReuqests, String basedir, long durationSecs){
    File f = new File(basedir);
    if(!f.exists     ())throw new IllegalArgumentException(basedir + " dir not exists");
    if(!f.isDirectory())throw new IllegalArgumentException(basedir + " is not directory");
    if(!f.canRead    ())throw new IllegalArgumentException(basedir + " read permission denied");
    this.basedir = f;
    this.durationMS = durationSecs*1000;
    this.startMS = System.currentTimeMillis();

    QueueBasedMapRedImpl<String, Map<String, Object>> qbmr =
      new QueueBasedMapRedImpl<String, Map<String, Object>>(
        concurrentReuqests, this, this, POISON_MSG, this.durationMS);

    qbmr.setTerminateEvent(
      new TerminateEvent(){
        public void OnTerminate(){
          cleanup();
        }
     });

    this.mapper = qbmr;
  }

  void setRecursive (boolean    recursive ){this.recursive =recursive ;}
  void setFileFilter(FileFilter fileFilter){this.fileFilter=fileFilter;}

  public void run(){
    long startms = System.currentTimeMillis();
    for(;;){
      basedir.listFiles(fileFilter);
      if(this.durationMS <= 0) break;
      else if(System.currentTimeMillis() - startms >= this.durationMS) break;
      Thread.yield();
    }
  }

  void cleanup(){
    long elapsed = ((System.currentTimeMillis() - this.startMS)/1000L);
    if(VERBOSE) LOG.printf("%s elapsed secs %s %n", Thread.currentThread(), elapsed);
  }

  public void close(){
    if(mapper!=null){
      mapper.shutdown();
      //if(mapper instanceof Waitable)
      //  ((Waitable)mapper).waitTermination();
    }
  }

  public Map<String, Object> doWork(String input){
    if(VERBOSE)LOG.println(Thread.currentThread() + " processing " + input );
    Map<String, Object> out = new HashMap<String, Object>();
    BufferedReader br=null;
    try{
      br = new BufferedReader(new FileReader(input));
      doWork(input, br, out);
    }catch(IOException ex){
      ex.printStackTrace(LOG);
    }finally{
      if(br!=null){
        try{br.close();}catch(IOException ex){ex.printStackTrace(LOG);}
      }
    }

    return out;
  }

  protected void doWork(String input, BufferedReader br, Map<String, Object> out) throws IOException {
      String line=null;
      while((line=br.readLine())!=null){
        onLine(input, line, out);
      }
  }

  protected void onLine(String filename, String line, Map<String, Object> out){
    System.out.printf("%s %s %s%n", Thread.currentThread(), filename, line);
  }

  public void onResult(Map<String, Object> result){
    System.out.println(result);
  }

  protected boolean filter(File pathname){
    return true;
    // derived classed may overwrite it to implement their filter
    // or call setFileFilter to set different FileFilter
  }

  static int atoi(String s, int _default){
    if(s!=null){
      try{return Integer.parseInt(s.trim());}
      catch(Exception ex){ex.printStackTrace(LOG);}
    }
    return _default;
  }

  public static void main(String[] args){
    if(args.length < 1){
      System.out.printf("usage: %s [-r] <dirpath>%n", System.getProperty("progname",FilebasedTask.class.getName()));
      return;
    }
    boolean recursive=false;
    String basedir = args[args.length-1];
    for(int i=0; i < args.length-1; i++){
      String a = args[i];
      if("-r".equals(a))recursive=true;
    }

    // default is number of processors
    int threads = atoi(System.getProperty("threads"),Runtime.getRuntime().availableProcessors());
    long durationSecs = atoi(System.getProperty("durationsecs"),0);
    FilebasedTask ft = new FilebasedTask(threads, basedir, durationSecs);
    ft.setRecursive(recursive);

    try{
      ft.run();
    }
    finally{
      ft.close(); // dont forget to close otherwise it wont terminate
    }
  }
}