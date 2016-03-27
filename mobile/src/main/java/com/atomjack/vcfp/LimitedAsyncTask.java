package com.atomjack.vcfp;

import android.os.AsyncTask;

import com.atomjack.shared.Logger;

import java.util.ArrayList;

public class LimitedAsyncTask {
  private int numConcurrentTasks = 5;
  private ArrayList<LAsyncTask> tasks = new ArrayList<>();

  public void run() {
    Logger.d("Will run first %d of %d tasks", numConcurrentTasks, tasks.size());
    for(int i=0;i<numConcurrentTasks;i++) {
      LAsyncTask task = tasks.remove(0);
      task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  public void addTask(LAsyncTask task) {
    task.handler = taskHandler;
    tasks.add(task);
  }

  public void setNumConcurrentTasks(int numConcurrentTasks) {
    this.numConcurrentTasks = numConcurrentTasks;
  }

  private TaskHandler taskHandler = new TaskHandler() {
    @Override
    public void onFinished() {
      Logger.d("Done with task, have %d tasks left", tasks.size());
      if(tasks.size() > 0) {
        LAsyncTask task = tasks.remove(0);
        Logger.d("Now running task %s", task.label);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
      }
    }
  };

  public interface TaskHandler {
    void onFinished();
  }
}
