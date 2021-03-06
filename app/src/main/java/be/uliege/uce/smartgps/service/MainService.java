package be.uliege.uce.smartgps.service;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import be.uliege.uce.smartgps.R;
import be.uliege.uce.smartgps.activities.MainActivity;
import be.uliege.uce.smartgps.dataBase.SQLiteController;
import be.uliege.uce.smartgps.entities.Sensor;
import be.uliege.uce.smartgps.utilities.Constants;
import be.uliege.uce.smartgps.utilities.NotificationUtils;
import be.uliege.uce.smartgps.utilities.Utilidades;

import static be.uliege.uce.smartgps.utilities.Utilidades.timerInterval;

import androidx.core.app.NotificationCompat;

public class MainService extends Service {

    private static final String TAG = MainService.class.getSimpleName();

    private BroadcastReceiver broadcastReceiverActivity;
    private BroadcastReceiver broadcastReceiverSensor;
    private BroadcastReceiver broadcastReceiverGps;
    private BroadcastReceiver broadcastReceiverLocation;
    private BroadcastReceiver broadcastReceiverGoogleLocation;

    private Sensor sensorObject;
    private Sensor sensorActual;
    private float varProximity;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat(Constants.FORMAT_DATE);

    private SQLiteController dbSensor;

    private NotificationUtils mNotificationUtils;
    private int DEFAULT_NOTIFY = 0;
    private int notify = 0;
    private Map<String, String> dataSync;

    static final int DEFAULT_THREAD = 0;
    static int thread;

    IBinder mBinder = new MainService.LocalBinder();

    public class LocalBinder extends Binder {
        public MainService getServerInstance() {
            return MainService.this;
        }
    }

    public MainService() {

    }

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        sensorActual = new Sensor();
        dbSensor = new SQLiteController(getApplicationContext());
        mNotificationUtils = new NotificationUtils(this);
        startTracking();
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.i(TAG, "onStartCommand");

        sensorActual = new Sensor();
        sensorObject = new Sensor();
        dbSensor = new SQLiteController(getApplicationContext());
        mNotificationUtils = new NotificationUtils(this);

        startTracking();

        broadcastReceiverSensor = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                if (intent.getAction().equals(Constants.SENSOR_ACTIVITY)) {

                    Sensor sensor = (Sensor) intent.getSerializableExtra(Constants.SENSOR_ACTIVITY);
                    if(sensor.getAclX() != null && !sensor.getAclX().equals("null")) {
                        sensorObject.setAclX(sensor.getAclX());
                        sensorObject.setAclY(sensor.getAclY());
                        sensorObject.setAclZ(sensor.getAclZ());
                    }
                    if(sensor.getGrsX() != null && !sensor.getGrsX().equals("null")) {
                        sensorObject.setGrsX(sensor.getGrsX());
                        sensorObject.setGrsY(sensor.getGrsY());
                        sensorObject.setGrsZ(sensor.getGrsZ());
                    }
                    //
                    if (sensor.getProximity() != null && !sensor.getProximity().equals("null")) {
                        sensorObject.setProximity(sensor.getProximity());
                        varProximity = sensor.getProximity();
                    }else {
                        sensorObject.setProximity(varProximity);
                    }

                    if (sensor.getLuminosity() != null && !sensor.getLuminosity().equals("null")){
                        sensorObject.setLuminosity(sensor.getLuminosity());
                    }

                    if (sensor.getStepCounter() != null && !sensor.getStepCounter().equals("null")){
                        sensorObject.setStepCounter(sensor.getStepCounter());
                    }

                    if (sensor.getBattery() != null && !sensor.getBattery().equals("null"))
                        sensorObject.setBattery(sensor.getBattery());
                }
            }
        };

        broadcastReceiverGps = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                if (intent.getAction().equals(Constants.GPS_ACTIVITY)) {

                    Sensor sensor = (Sensor) intent.getSerializableExtra(Constants.GPS_ACTIVITY);

                    sensorObject.setAccuracy(sensor.getAccuracy());
                    sensorObject.setnSatellites(sensor.getnSatellites());

                }
            }
        };

        broadcastReceiverLocation = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                if (intent.getAction().equals(Constants.LOCATION_ACTIVITY)) {

                    Sensor sensor = (Sensor) intent.getSerializableExtra(Constants.LOCATION_ACTIVITY);

                    sensorObject.setVelocity(sensor.getVelocity());
                    sensorObject.setLongitude(sensor.getLongitude());
                    sensorObject.setLatitude(sensor.getLatitude());
                    sensorObject.setAltitude(sensor.getAltitude());
                    if (sensor.getTemperature() != null && !sensor.getTemperature().equals("null")) {
                        sensorObject.setTemperature(sensor.getTemperature());
                    }
                    sensorObject.setWeather(sensor.getWeather());
                    sensorObject.setCity(sensor.getCity());
                }
            }
        };

        broadcastReceiverActivity = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                if (intent.getAction().equals(Constants.DETECTED_ACTIVITY)) {

                    Sensor sensor = (Sensor) intent.getSerializableExtra(Constants.DETECTED_ACTIVITY);
                    sensorActual.setActivity(sensor.getActivity());
                    sensorActual.setActivityConfidence(sensor.getActivityConfidence());
                }
            }
        };

        broadcastReceiverGoogleLocation = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                if (intent.getAction().equals(Constants.GOOGLE_LOCATION_ACTIVITY)) {

                    Sensor sensor = (Sensor) intent.getSerializableExtra("googleLocationDate");

                    if(sensor != null && sensor.getLatitude()!= null) {
                        if(sensorObject.getLatitude() == null || sensorObject.getLatitude().equals("null")) {
                            sensorObject.setLatitude(sensor.getLatitude());
                        }

                        if(sensorObject.getVelocity() == null || sensorObject.getVelocity().equals("null")) {
                            sensorObject.setVelocity(sensor.getVelocity());
                        }

                        if(sensorObject.getLongitude() == null || sensorObject.getLongitude().equals("null")) {
                            sensorObject.setLongitude(sensor.getLongitude());
                        }
                        if(sensorObject.getAltitude() == null || sensorObject.getAltitude().equals("null")){
                            sensorActual.setAltitude(sensor.getAltitude());
                        }
                    }
                }
            }
        };

        if(checkThread(1) < 2){
            new Thread(new Runnable() {
                public void run() {

                    int count = 0;

                    while (true) {

                        startTracking();

                        if(count >= Constants.FREQUENCY_SECOND) {
                            count = processData(count);
                        }
                        count = count+1;

                        if(timerInterval(new Date(), Constants.TIME_RECOVER)) {
//                        sincronizate();
                            SystemClock.sleep(1000);
                        }
                        SystemClock.sleep(1000);
                    }
                }
            }).start();
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiverActivity, new IntentFilter(Constants.DETECTED_ACTIVITY));
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiverSensor, new IntentFilter(Constants.SENSOR_ACTIVITY));
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiverGps, new IntentFilter(Constants.GPS_ACTIVITY));
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiverLocation, new IntentFilter(Constants.LOCATION_ACTIVITY));
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiverGoogleLocation, new IntentFilter(Constants.GOOGLE_LOCATION_ACTIVITY));

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this,
                    0, notificationIntent, 0);

            createNotificationChannel();

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("SmartGPS")
                    .setContentText("Ejecución en segundo plano")
                    .setSmallIcon(R.drawable.icon)
                    .setContentIntent(pendingIntent)
                    .build();

            startForeground(1, notification);
        }

        return START_STICKY;
    }

    public static final String CHANNEL_ID = "ServiceChannel";
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    private void executeTask(){
        sensorObject = new be.uliege.uce.smartgps.entities.Sensor();
        sensorObject.setDateInsert(new Timestamp(System.currentTimeMillis()));
        sensorObject.setDateUpdate(new Timestamp(System.currentTimeMillis()));
        sensorObject.setActivity(sensorActual.getActivity());
        sensorObject.setActivityConfidence(sensorActual.getActivityConfidence());
        sensorObject.setAltitude(sensorActual.getAltitude());

        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
            sensorObject.setProviderStatus(Constants.PROVIDER_ON);
        }else{
            sensorObject.setProviderStatus(Constants.PROVIDER_OFF);

//            Notification.Builder nb = mNotificationUtils. getAndroidChannelNotification("¡GPS deshabilitado.!", "Active el GPS para continuar con la recolección de información.", MainActivity.class);
//            mNotificationUtils.getManager().notify(101, nb.build());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder nb = mNotificationUtils. getAndroidChannelNotification("¡GPS deshabilitado.!", "Active el GPS para continuar con la recolección de información.", MainActivity.class);
                mNotificationUtils.getManager().notify(101, nb.build());
            }else{
                mNotificationUtils.notificationAndroid("¡GPS deshabilitado.!", "Active el GPS para continuar con la recolección de información.", MainActivity.class);
            }

        }

    }

    private void startTracking() {
        isMyServiceRunning(DetectedActivitiesService.class);
        isMyServiceRunning(SensorService.class);
        isMyServiceRunning(LocationService.class);
        isMyServiceRunning(GoogleLocationService.class);
    }

    private class GetUrlContentTask extends AsyncTask<String, Integer, String> {
        protected String doInBackground(String... urls) {

            URL url = null;
            try {
                url = new URL(urls[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setDoOutput(true);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();
                BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String content = "", line;
                while ((line = rd.readLine()) != null) {
                    content += line + "\n";
                }
                return content;

            } catch (MalformedURLException e) {
                Log.e(TAG, String.valueOf(e.getMessage()));
            } catch (ProtocolException e) {
                Log.e(TAG, String.valueOf(e.getMessage()));
            } catch (SocketTimeoutException e){
                Log.e(TAG, String.valueOf(e.getMessage()));
            } catch (IOException e) {
                Log.e(TAG, String.valueOf(e.getMessage()));
            }
            return null;
        }

        protected void onProgressUpdate(Integer... progress) {
        }

        protected void onPostExecute(String result) {
            if(result != null) {
                //Log.e(TAG, result);
            }else{
                Log.e(TAG+" GetUrlContentTask", "Result is null");
            }
        }
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {

        try {
            ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }

            Intent intent = new Intent(MainService.this, serviceClass.getClass());
            startService(intent);

            Log.e(TAG, "My Service " + serviceClass.getName() + " was reset.");

            return false;

        }catch (IllegalStateException ex){
            Log.e(TAG, "Error to run service "+serviceClass.getName());
        }
        return false;
    }

    public int processData(int count){
//        executeTask();
        int lastIdOld = dbSensor.getLastData().getInt(0);
        Sensor sensorOld = null;
        if(dbSensor.getData(lastIdOld).getCount() > 0) {
            Gson gson = new GsonBuilder().setDateFormat(Constants.FORMAT_DATE).create();
            try {
                sensorOld = gson.fromJson(dbSensor.getData(lastIdOld).getString(1), Sensor.class);
            }catch (com.google.gson.JsonSyntaxException e){
                try{
                    Gson gson2 = new GsonBuilder().setDateFormat(Constants.FORMAT_DATE_2).create();
                    sensorOld = gson2.fromJson(dbSensor.getData(lastIdOld).getString(1), Sensor.class);
                }catch (com.google.gson.JsonSyntaxException el){
                    Gson gson3 = new GsonBuilder().setDateFormat(Constants.FORMAT_DATE_3).create();
                    sensorOld = gson3.fromJson(dbSensor.getData(lastIdOld).getString(1), Sensor.class);
                }
            }
        }

        if(sensorObject.getLuminosity() == null){
            sensorObject.setLuminosity((float)-1);
        }

        if(Utilidades.setSensorObject(sensorObject, sensorOld)) {
            if(sensorObject.getDateInsert() != null){
//                Gson gson = new GsonBuilder().setDateFormat(Constants.FORMAT_DATE).create();
                dbSensor.insertData(new Gson().toJson(sensorObject));
                System.out.println("*************************************************************");
                System.out.println(sensorObject);
                System.out.println("*************************************************************");
                Log.i(TAG+".processData", String.valueOf("Count: "+dbSensor.countRowsData()+" - "+sensorObject.toString()));
            }
            executeTask();
            count = 0;

        }else{

            if(dbSensor.getLastData().getCount() > 0) {

                int lastId = dbSensor.getLastData().getInt(0);

                if(dbSensor.getData(lastId).getCount() > 0) {
                    Sensor sensor;
                    Gson gson = new GsonBuilder().setDateFormat(Constants.FORMAT_DATE).create();
                    try {
                        sensor = gson.fromJson(dbSensor.getData(lastId).getString(1), Sensor.class);
                    }catch (com.google.gson.JsonSyntaxException e){
                        try{
                            Gson gson2 = new GsonBuilder().setDateFormat(Constants.FORMAT_DATE_2).create();
                            sensor = gson2.fromJson(dbSensor.getData(lastId).getString(1), Sensor.class);
                        }catch (com.google.gson.JsonSyntaxException el){
                            Gson gson3 = new GsonBuilder().setDateFormat(Constants.FORMAT_DATE_3).create();
                            sensor = gson3.fromJson(dbSensor.getData(lastId).getString(1), Sensor.class);
                        }
                    }
                    //Sensor sensor = new Gson().fromJson(dbSensor.getData(lastId).getString(1), Sensor.class);
                    sensor.setDateUpdate(new Timestamp(System.currentTimeMillis()));

                    LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
                    if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                        sensor.setProviderStatusUpdate(Constants.PROVIDER_ON);
                        notify = DEFAULT_NOTIFY;
                    } else {
                        sensor.setProviderStatusUpdate(Constants.PROVIDER_OFF);
//                        Notification.Builder nb = mNotificationUtils.getAndroidChannelNotification("¡GPS deshabilitado.!", "Active el GPS para continuar con la recolección de información.", MainActivity.class);
//                        mNotificationUtils.getManager().notify(101, nb.build());
                        notify = notify + 1;
                        if(notify == 1){
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                Notification.Builder nb = mNotificationUtils. getAndroidChannelNotification("¡GPS deshabilitado.!", "Active el GPS para continuar con la recolección de información.", MainActivity.class);
                                mNotificationUtils.getManager().notify(101, nb.build());
                            }else{
                                mNotificationUtils.notificationAndroid("¡GPS deshabilitado.!", "Active el GPS para continuar con la recolección de información.", MainActivity.class);
                            }
                        }
                    }
                    System.out.println("/////////////////////////////////////////////");
                    System.out.println(sensor);
                    System.out.println("/////////////////////////////////////////////");
                    dbSensor.updateData(lastId, new Gson().toJson(sensor));
                }
                executeTask();
                count = 0;
            }
        }
        return count;
    }

//    public void sendResponse(final Map<String, String> params){
//
//        RequestQueue queue = Volley.newRequestQueue(MainService.this);
//
//        StringRequest postRequest = new StringRequest(Request.Method.POST, Constants.URL_CONSUMMER, new com.android.volley.Response.Listener<String>() {
//
//            @Override
//            public void onResponse(String response) {
//
//                Log.d(TAG+".onResponse", response);
//
//                String palabra = "OK";
//                boolean resultado = response.contains(palabra);
//
//                if(response != null && resultado){
//                    if(dataSync != null) {
//
//                        for (Map.Entry<String, String> entry : dataSync.entrySet()) {
//                            try {
//                                dbSensor.deleteData(Integer.parseInt(entry.getKey()));
//                            } catch (Exception e) {
//                                Log.e(TAG + ".onResponse", "Error el eliminar por id " + entry.getKey());
//                            }
//                        }
//
//                      //  final String url = Constants.URL_NOTIFICADOR_TELEGRAM +"?msj="+ (Build.MODEL+" --> Ha sincronizado "+dataSync.size()+" elementos automaticamente "+String.valueOf(new Timestamp(System.currentTimeMillis()))+"." ).replace(" ", "%20");
//                      //  new Thread(new Runnable() {
//                      //      public void run(){
//                      //         new GetUrlContentTask().execute(url);
//                      //      }
//                      //  }).start();
//
//                        dataSync = null;
//                        Log.i(TAG+".onResponse", "Sincronizado...");
//                    }
//                }else{
//                    dataSync = null;
//                    Log.e(TAG+".onResponse", "Error al sincronizar...!");
//                    Toast.makeText(MainService.this,"Error al sincronizar...!",Toast.LENGTH_SHORT).show();
//                }
//
//            }
//        },
//                new com.android.volley.Response.ErrorListener() {
//                    @Override
//                    public void onErrorResponse(VolleyError error) {
//                        Toast.makeText(MainService.this,"¡Ops... Algo salió mal.\nIntenta más tarde!",Toast.LENGTH_SHORT).show();
//                    }
//                }
//        ) {
//            @Override
//            protected Map<String, String> getParams() {
//                return params;
//            }
//        };
//        queue.add(postRequest);
//    }

//    public void sincronizate(){
//
//        User user = new Gson().fromJson(DataSession.returnDataSession(getApplicationContext(), Constants.INFO_SESSION_KEY), User.class);
//
//        String timeStart1 = user.getHoraSinc();
//        String timeEnd1 = "23:59:59";
//        String timeStart2 = "00:00:00";
//        String timeEnd2 = "03:00:01";
//        Date nowDate = formaterStringTime(formaterTimeString(new Date(new Timestamp(System.currentTimeMillis()).getTime())));
//
//        if(checkSincronizate(nowDate,
//                formaterStringTime(timeStart1),
//                formaterStringTime(timeEnd1),
//                formaterStringTime(timeStart2),
//                formaterStringTime(timeEnd2),
//                dbSensor.countRowsData())){
//
//
//            dataSync =  dbSensor.getAllData();
//            List<Sensor> positionsSinc = new ArrayList<>();
//
//            for(Map.Entry<String, String> entry : dataSync.entrySet()) {
//                positionsSinc.add(new Gson().fromJson(entry.getValue(), Sensor.class));
//            }
//
//            Log.i(TAG+".sincronizate" , "Sincronization starting... "+String.valueOf(positionsSinc.size())+" - "+new Gson().toJson(positionsSinc));
//
//            Map<String, String> params = new HashMap<>();
//            params.put("type","setInfoSensor");
//            params.put("dspId",String.valueOf(user.getDspId()));
//            params.put("sensorInfo",new Gson().toJson(positionsSinc));
//
//            sendResponse(params);
//        }
//    }

    public int checkThread (int valor){
        if(valor == 1){
            thread = thread + valor;
        }else if(valor == 0) {
            thread = DEFAULT_THREAD;
        }
        return thread;
    }
}