package sg.edu.nushigh.arp.backgroundprioritizer;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public final class Utilities {
    /**
     * Obtains the currently running app.
     * @param c The context this is being used in.
     * @return The package name of the current app.
     */
    static String getForegroundActivity(Context c){
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
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

    /**
     * Uses the {@code top} command to retrieve a list of running processes and their associated data
     * @return The list of currently running processes
     */
    static ProcessUsageData[] taskList(){
        ArrayList<String> out = Utilities.executeCommand("ps");
        // Data about individual processes only starts after line 1
        if(out.size() > 1){
            ProcessUsageData[] data = new ProcessUsageData[out.size()-1];
            for(int i = 1; i < out.size(); i++)
                data[i-1] = new Utilities.ProcessUsageData(out.get(i));
            return data;
        }else{
            Log.e("get task list", "out is " + out.size() + " lines long");
            return new ProcessUsageData[0];
        }
    }

    /**
     * Used as a data type to organize the output from the {@code top} command.
     */
    static class ProcessUsageData{
        private int pid;	// Process ID (PID)
        private int ppid;   // Parent PID\
        private int vss;	// Virtual set size, units in K
        private int rss;	// Resident set size, units in K
        private String uid;	// User who owns it
        private String name;// Package Name

        /**
         * Constructs an object from the command output.
         * @param line The line as it is produced from the command line.
         */
        private ProcessUsageData(String line){
            String[] tokens = line.trim().split("\\s+");
            uid = tokens[0];
            pid = Integer.parseInt(tokens[1]);
            ppid = Integer.parseInt(tokens[2]);
            vss = Integer.parseInt(tokens[3]);
            rss = Integer.parseInt(tokens[4]);
            name = tokens[8];
        }

        public int getPid() {
            return pid;
        }

        public int getVss() {
            return vss;
        }

        public int getRss() {
            return rss;
        }

        public String getUid() {
            return uid;
        }

        public String getName() {
            return name;
        }

        public int getPpid() {
            return ppid;
        }
    }

    /**
     * Creates a new Android terminal process with superuser permissions,
     * and then executes a command on it.
     * @param cmd The command to execute.
     * @return The output by the terminal after executing the command, line by line, in chronological order.
     */
    static ArrayList<String> executeSuperCommand(String cmd){
        try{
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream pstdin = new DataOutputStream(p.getOutputStream());
            BufferedReader pstdout = new BufferedReader(new InputStreamReader(p.getInputStream()));

            pstdin.writeBytes(cmd + "\nexit\n");
            pstdin.flush();
            p.waitFor();
            ArrayList<String> out = new ArrayList<>();
            String line;
            while ((line = pstdout.readLine()) != null){
                out.add(line);
            }
            pstdin.close();
            pstdout.close();
            return out;
        }catch(IOException e){
            Log.e("shell execution", "IO error: " + e.getMessage());
        }catch(InterruptedException e) {
            Log.e("shell execution", "Interrupt error: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    /**
     * Creates a new Android terminal process with normal permissions,
     * and then executes a command on it.
     * @param cmd The command to execute.
     * @return The output by the terminal after executing the command, line by line, in chronological order.
     */
    static ArrayList<String> executeCommand(String cmd){
        try{
            Process p = Runtime.getRuntime().exec("sh");
            DataOutputStream pstdin = new DataOutputStream(p.getOutputStream());
            BufferedReader pstdout = new BufferedReader(new InputStreamReader(p.getInputStream()));

            pstdin.writeBytes(cmd + "\nexit\n");
            pstdin.flush();
            p.waitFor();
            ArrayList<String> out = new ArrayList<>();
            String line;
            while ((line = pstdout.readLine()) != null){
                out.add(line);
            }
            pstdin.close();
            pstdout.close();
            return out;
        }catch(IOException e){
            Log.e("shell execution", "IO error: " + e.getMessage());
        }catch(InterruptedException e) {
            Log.e("shell execution", "Interrupt error: " + e.getMessage());
        }
        return new ArrayList<>();
    }
}
