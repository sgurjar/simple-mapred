import sg.util.*;
import java.io.*;
import java.util.jar.*;
import java.util.*;

public class JWhich
    implements
        FileFilter,
        ResultHandler<Map<String, Object>>,
        Wroker<File, Map<String, Object>>
{
    final static boolean VERBOSE = Boolean.getBoolean("verbose");
    final static PrintStream LOG = System.err;

    public static void main(String[] args) throws Exception {
        long starttm = System.nanoTime();

        if(args.length < 2) {
            usage();
            return;
        }

        try{
          String text    = args[args.length-1];
          String basedir = args[args.length-2];
          boolean keepsearching=false, recursive=false, casesensitive=false;

          for(int i=0; i < (args.length-2); i++) {
              String a = args[i];
              if(a.equals("-k"))keepsearching=true; else
              if(a.equals("-r"))recursive    =true; else
              if(a.equals("-c"))casesensitive=true;
          }

          JWhich j = null;
          int threads = atoi(System.getProperty("threads"),Runtime.getRuntime().availableProcessors());
          try{j = new JWhich(text, basedir, threads).recursive(recursive).keepSearching(keepsearching).caseSensitive(casesensitive).which();}
          finally {if(j!=null)j.close();}
        }finally{
          System.out.printf("elapsed %s ms%n",((System.nanoTime() - starttm)/1000000F));
        }
    }

    static void usage() {
        String progname = System.getProperty("progname","JWhich");
        System.out.printf("Usage: %s [-c][-k][-r] dir str%n",progname);
        System.out.println("  -c case-sensitive search, otherwise case-insensitive ");
        System.out.println("  -k keep searching, otherwise stops after first found");
        System.out.println("  -r recursive search in all subdirectories");
        System.out.println("dir is root of the directory tree from where search began.");
        System.out.println("str is the text that is searched with entries in in jar/zip files.");
        System.out.println("Example:");
        System.out.printf("   %s -r -k  C:\\ibm\\SDP\\runtimes\\base_v7 WsnInitialContextFactory%n",progname);
    }

    final static String REC_SEP = String.valueOf((char)30);

    private final String text;
    private final File basedir;
    private boolean recursive;
    private boolean keepsearching;
    private boolean casesensitive;
    private final MapReduce<File, Map<String, Object>> mapper;

    JWhich(String text, String basedir, int concurrentRequests) {
        if(text    == null || (text=text.trim()).length()==0)throw new IllegalArgumentException("text can not be null or empty");
        if(basedir == null || (basedir=basedir.trim()).length()==0)throw new IllegalArgumentException("basedir can not be null or empty");

        File f = new File(basedir);

        if(!f.exists     ())throw new IllegalArgumentException(basedir + " not exists");
        if(!f.isDirectory())throw new IllegalArgumentException(basedir + " is not directory");
        if(!f.canRead    ())throw new IllegalArgumentException(basedir + " read permission denied");
        this.basedir = f;

        this.text = text;

        ResultHandler<Map<String, Object>> resultHandler = this;
        Wroker<File, Map<String, Object>>  worker = this;
        this.mapper = new MapReduceImpl<File, Map<String, Object>>(concurrentRequests, resultHandler, worker);
    }

    JWhich recursive    (boolean recursive    ) {this.recursive    =recursive    ; return this;}
    JWhich keepSearching(boolean keepsearching) {this.keepsearching=keepsearching; return this;}
    JWhich caseSensitive(boolean casesensitive) {this.casesensitive=casesensitive; return this;}

    public JWhich which() {
        basedir.listFiles(this);
        return this;
    }

    public void close(){mapper.shutdown();}

    void onNextFile(File input) {
      mapper.OnNextData(input);
    }

    public Map<String, Object> doWork(File file){
      Map<String, Object> out = new HashMap<String, Object>();
      List<String[]> alist = new ArrayList<String[]>();
      try{
        JarFile jarfile = new JarFile(file);
        Enumeration<JarEntry> entries = jarfile.entries();
        while(entries.hasMoreElements()){
          JarEntry entry = entries.nextElement();
          if(entry.getName().indexOf(text)!=-1){
            alist.add(new String[]{file.getAbsolutePath(),entry.getName()});
          }
        }
      }catch(IOException ex){
        ex.printStackTrace(LOG);
        alist.add(new String[]{file.getAbsolutePath(),ex.toString()});
      }

      out.put(file.getAbsolutePath()+REC_SEP+Thread.currentThread().toString(),alist);
      return out;
    }

    public void onResult(Map<String,Object> result){
      for(Map.Entry<String,Object> entry : result.entrySet()){
        String key = entry.getKey();
        Object val = entry.getValue();
        if(val instanceof List){
          List<String[]> alist = (List<String[]>)val;
          for(String[] arr : alist){
            System.out.println(arr[0]+"\t"+arr[1]);
          }
        }
        if(VERBOSE){
          String[] a = key.split(REC_SEP);
          LOG.printf("%s:%s:%s%n",
            a.length > 0 ? a[0]:"",
            a.length > 1 ? a[1]:"",
            val
            );
        }
      }
    }

    public boolean accept(File pathname) {
        if(pathname.isDirectory()) {
            if(recursive) {
                pathname.listFiles(this);
            }
        } else if(pathname.isFile()) {
            String fname = pathname.getName();

            if(fname.endsWith(".jar")||fname.endsWith(".zip")) {
                onNextFile(pathname);
            }
        } else {
            LOG.printf("ERROR: unknown file type '%s'%n",pathname);
        }

        /*it doesnt matter what we return as we are not collecting results,
        we return false so that File[] returned by listFiles will always
        be empty and no memory is wasted.*/
        return false;
    }

    static int atoi(String s, int _default){
      if(s!=null){
        try{return Integer.parseInt(s.trim());}
        catch(Exception ex){ex.printStackTrace(LOG);}
      }
      return _default;
    }

}