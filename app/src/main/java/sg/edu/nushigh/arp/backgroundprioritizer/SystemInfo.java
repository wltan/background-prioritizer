package sg.edu.nushigh.arp.backgroundprioritizer;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.Enumeration;

@SuppressWarnings("deprecation")
public final class SystemInfo {
    private final Context c;

    private final ConnectivityManager connManager;
    private final WifiManager wifi;
    private final WifiInfo wifiInfo;
    private final BluetoothAdapter bluetooth;

    private final Runtime runtime;
    private final ActivityManager.MemoryInfo mi;
    private final StatFs intFs, extFs;

    private final Intent battery;

    //private final String[] cpuUse;

    public SystemInfo(Context c){
        this.c = c;
        connManager = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        wifi = ((WifiManager) c.getSystemService(Context.WIFI_SERVICE));
        wifiInfo = wifi.getConnectionInfo();
        bluetooth = BluetoothAdapter.getDefaultAdapter();
        runtime = Runtime.getRuntime();
        mi = new ActivityManager.MemoryInfo(); ((ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        intFs = new StatFs(Environment.getDataDirectory().getPath());
        extFs = new StatFs(Environment.getExternalStorageDirectory().getPath());
        battery = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        //cpuUse = Utilities.executeCommand("top -n 1 -m 1").get(3).split(", ");
    }

    // Android version
    public String androidVersionName(){
        Field[] fields = Build.VERSION_CODES.class.getFields();
        for (Field field : fields) {
            String fieldName = field.getName();
            int fieldValue = -1;

            try {
                fieldValue = field.getInt(new Object());
            } catch (IllegalArgumentException|IllegalAccessException|NullPointerException e) {}

            if (fieldValue == Build.VERSION.SDK_INT) {
                return fieldName;
            }
        }
        return "Error retrieving version";
    }

    public String androidVersionCode(){
        return Build.VERSION.RELEASE;
    }

    // System uptime
    public String uptime(){
        long ms = SystemClock.uptimeMillis();
        ms /= 1000;
        long sec = ms%60;
        ms /= 60;
        long min = ms%60;
        ms /= 60;
        long hour = ms%24;
        ms /= 24;
        long days = ms;
        return days + "d " + hour + "h " + min + "m " + sec + "s";
    }

    // IMEI/ESN, or whatever unique identifier for the device
    public String imei(){
        return ((TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId();
    }

    // Brand and model
    public String brand(){
        return Build.BRAND;
    }
    public String model(){
        return Build.MODEL;
    }

    // Wireless and networks
    public boolean wifiOn(){
        return wifi.isWifiEnabled();
    }
    public void toggleWifi(){
        wifi.setWifiEnabled(!wifi.isWifiEnabled());
    }
    public boolean wifiConnected(){
        try{
            return connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected();
        }catch(Exception e){
            return false;
        }
    }
    public String wifiMac(){
        boolean off = !wifiOn();
        String result;
        if(off){
            wifi.setWifiEnabled(true);
            result = wifi.getConnectionInfo().getMacAddress();
            wifi.setWifiEnabled(false);
        }else{
            result = wifiInfo.getMacAddress();
        }
        return result;
    }
    // Check that wifi is connected with the above method before proceeding to use these methods
    public String wifiSSID(){
        String ssid = wifiInfo.getSSID();
        if(ssid.startsWith("\"") && ssid.endsWith(("\"")))
            return ssid.substring(1, ssid.length()-1);
        return ssid;
    }
    public String wifiIP(){
        int ipAddress = wifiInfo.getIpAddress();

        // Convert little-endian to big-endian if needed
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN))
            ipAddress = Integer.reverseBytes(ipAddress);

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        try {
            return InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException ex) {
            Log.e("wifi info (IP)", "Unable to get host address");
            return null;
        }
    }
    public String wifiSpeed(){
        return wifiInfo.getLinkSpeed() + " " + WifiInfo.LINK_SPEED_UNITS;
    }
    public boolean bluetoothSupported(){
        return bluetooth != null;
    }
    // Check that bluetooth is supported first, will throw exception otherwise
    public boolean bluetoothOn(){
        if(!bluetoothSupported())
            throw new UnsupportedOperationException("Bluetooth not supported!");
        return bluetooth.isEnabled();
    }
    public String bluetoothAddress() {
        if(!bluetoothSupported())
            throw new UnsupportedOperationException("Bluetooth not supported!");
        return bluetooth.getAddress();
    }
    public boolean mobileOn(){
        return connManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).isConnected();
    }
    public String mobileType(){
        return connManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).getSubtypeName();
    }
    public String mobileIP(){
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }

                }
            }
        } catch (SocketException e) {}
        return null;
    }

    // Memory usage
    public static final long MEGABYTE = 1048576L;
    public static final String MEGABYTE_UNITS = " MB";
    public long ramTotal(){
        return mi.totalMem/MEGABYTE;
    }
    public long ramUsed(){
        return (mi.totalMem-mi.availMem)/MEGABYTE;
    }
    public long ramFree(){
        return mi.availMem/MEGABYTE;
    }
    public long intStorageTotal(){
        // deprecation suppressed because the non-deprecated alternative requires API level 18
        return ((long) intFs.getBlockCount())*intFs.getBlockSize()/MEGABYTE;
    }
    public long intStorageUsed(){
        return intStorageTotal()-intStorageFree();
    }
    public long intStorageFree(){
        return ((long) intFs.getAvailableBlocks())*intFs.getBlockSize()/MEGABYTE;
    }
    public long extStorageTotal(){
        return ((long) extFs.getBlockCount())*extFs.getBlockSize()/MEGABYTE;
    }
    public long extStorageUsed(){
        return extStorageTotal()-extStorageFree();
    }
    public long extStorageFree(){
        return ((long) extFs.getAvailableBlocks())*extFs.getBlockSize()/MEGABYTE;
    }

    // Battery
    public int batteryLevel(){
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return (int) (100.0*level/scale);
    }
    public String batteryHealth(){
        switch(battery.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)){
            case BatteryManager.BATTERY_HEALTH_COLD:
                return "Cold";
            case BatteryManager.BATTERY_HEALTH_DEAD:
                return "Dead";
            case BatteryManager.BATTERY_HEALTH_GOOD:
                return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                return "Overheating";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                return "Overvolting";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                return "Unspecified Failure";
            case BatteryManager.BATTERY_HEALTH_UNKNOWN:
            default:
                return "Unknown";
        }
    }
    public String batteryChargingState(){
        switch(battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return "Charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return "Discharging";
            case BatteryManager.BATTERY_STATUS_FULL:
                return "Full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return "Not charging";
            case BatteryManager.BATTERY_STATUS_UNKNOWN:
            default:
                return "Unknown";
        }
    }
    public String batteryChargingSource(){
        switch(battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)){
            case 0:
                return "On battery";
            case BatteryManager.BATTERY_PLUGGED_AC:
                return "AC";
            case BatteryManager.BATTERY_PLUGGED_USB:
                return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                return "Wireless";
            default:
                return "Unknown";
        }
    }
    public int batteryTemp(){
        return battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
    }
    public String batteryTechnology(){
        return battery.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
    }
    public int batteryVoltage(){
        return battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
    }

    /*
    // CPU
    public int cpuUser(){
        // works as long as cpu doesn't reach 100% (pretty hard to do without freezing the update anyway)
        return Integer.parseInt(cpuUse[0].substring(cpuUse[0].length() - 3, cpuUse[0].length() - 1).trim());
    }
    public int cpuSystem(){
        return Integer.parseInt(cpuUse[1].substring(cpuUse[0].length()-3, cpuUse[1].length()-1).trim());
    }
    */

    public int cpuCount(){
        return runtime.availableProcessors();
    }

}
