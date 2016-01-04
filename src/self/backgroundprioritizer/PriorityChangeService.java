package self.backgroundprioritizer;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import android.app.ActivityManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class PriorityChangeService extends Service {

	private static final int POLL_TIME = 3000;	// time between executions (in ms)
	private static final int NICE_SHIFT = 15;	// amount to renice processes with
	
	private volatile boolean kill = false; // flag to stop service
	private volatile int prev = -1; // PID of previously buffed process
	
	static volatile PriorityChangeService instance = null;
	
	/** temp method, shows a short toast*/
	private void showToast(String msg){
		Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
	}
	
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId){
		final Handler handler = new Handler();
	    final Runnable task = new Runnable(){
	    	@Override
		    public void run() {
		    	if(!kill){
					ArrayList<String> out = executeCommand("top -n 1");
					if(out.size() > 7){
						String foregroundActivityPackageName = getForegroundActivity();
						ProcessUsageData[] data = new ProcessUsageData[out.size()-7];
						int toDelete = prev;
						for(int i = 0; i < out.size()-7; i++){
							data[i] = new ProcessUsageData(out.get(i+7));
							if(data[i].pid == toDelete && data[i].name.equals(foregroundActivityPackageName)){
								// no change since last execution
								showToast("priority still with " + data[i].name);
								break;
							}
							if(data[i].pid == toDelete){
								// change niceness back to normal
								executeCommand("renice +" + NICE_SHIFT + " " + data[i].pid);
								showToast("priority taken from " + data[i].name);
							}
							if(data[i].name.equals(foregroundActivityPackageName)){
								// reduce niceness to give more priority
								executeCommand("renice -" + NICE_SHIFT + " " + data[i].pid);
								showToast("priority given to " + data[i].name);
								prev = data[i].pid;
							}
						}
					}else{
						Log.e("qwerty", "out is " + out.size() + " lines long");
					}
					
		    		handler.postDelayed(this, POLL_TIME);
		    	}
		    }
	    };
	    handler.postDelayed(task, POLL_TIME);
		return START_STICKY;
	}

    private String getForegroundActivity(){
    	ActivityManager am = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
        if(Build.VERSION.SDK_INT >= 21){
        	List<ActivityManager.RunningAppProcessInfo> taskInfo = am.getRunningAppProcesses();
            return taskInfo.get(0).processName;
        }else{
        	// old device, need to use older (now deprecated) method
        	@SuppressWarnings("deprecation")
			List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1);
            ComponentName componentInfo = taskInfo.get(0).topActivity;
            return componentInfo.getPackageName();
        }
    }
    
    private static class ProcessUsageData{
    	private int pid;	// Process ID (PID)
    	private int prio;	// Priority
    	private int cpu;	// CPU usage (in %)
    	private char state;	// Running/Sleeping state
    	private int threads;// Number of threads
    	private int vss;	// Virtual set size, units in K
    	private int rss;	// Resident set size, units in K
    	private String pcy;	// Policy - foreground (fg) or background (bg)
    	private String uid;	// User who owns it
    	private String name;// Package Name
    	
    	private ProcessUsageData(String line){
    		String[] tokens = line.trim().split("\\s+");
    		pid = Integer.parseInt(tokens[0]);
    		prio = Integer.parseInt(tokens[1]);
    		cpu = Integer.parseInt(tokens[2].substring(0, tokens[2].length()-1));
    		state = tokens[3].charAt(0); 
    		threads = Integer.parseInt(tokens[4]);
    		vss = Integer.parseInt(tokens[5].substring(0, tokens[5].length()-1));
    		rss = Integer.parseInt(tokens[6].substring(0, tokens[6].length()-1));
    		if(tokens.length == 10){
    			pcy = tokens[7];
        		uid = tokens[8];
        		name = tokens[9];    			
    		}else{
    			// pcy is blank, 9 tokens only
    			pcy = "";
        		uid = tokens[7];
        		name = tokens[8];
    		}
    	}
    	
    }
    
    private static ArrayList<String> executeCommand(String cmd){
    	try{
    		Process p = Runtime.getRuntime().exec("su");
    		DataOutputStream pstdin = new DataOutputStream(p.getOutputStream());
    		BufferedReader pstdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
    		
    	    pstdin.writeBytes(cmd + "\nexit\n");
    	    pstdin.flush();;
    	    p.waitFor();						
    	    ArrayList<String> out = new ArrayList<String>();
    	    String line;			
    		while ((line = pstdout.readLine()) != null){
    			out.add(line);
    		}
    		pstdin.close();
    		pstdout.close();
    		return out;
    	}catch(IOException e){
    		Log.e("qwerty", "IO error " + e.getMessage());
		}catch(InterruptedException e) {
			Log.e("qwerty", "Interrupt error " + e.getMessage());
			
		}
    	return new ArrayList<String>();
    }

	@Override
	public void onCreate(){
		instance = this;
	}

	@Override
	public void onDestroy(){
		kill = true;
		instance = null;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

}
