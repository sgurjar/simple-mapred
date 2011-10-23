// MapReduce.java
package sg.util;

public interface MapReduce<DATA, RESULT>{
  void OnNextData(DATA input);
  void shutdown();
}
