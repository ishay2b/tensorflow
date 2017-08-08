package org.tensorflow.demo;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * Created by ishay on 30/07/2017.
 */

public class PyDump {
    public final static String LOG_TAG = "PyDump";

    //public final  static  String PATH = Environment.getExternalStorageDirectory().getAbsolutePath()+"/tfDemo";

    public static final String getRoot(){
        final String root =
                Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "tensorflow";
        Log.i(LOG_TAG, "Saving  to "+root);
        final File myDir = new File(root);

        if (!myDir.mkdirs()) {
            Log.i(LOG_TAG, "Make dir failed");
        }
        return root;
    }

    public static void dumpFloatsArray(String filename, float floats[]){
        final String fname = filename;
        final File file = new File(getRoot(), fname);
        if (file.exists()) {
            file.delete();
        }
        try {
            FileOutputStream fos = new FileOutputStream(file);
            fos.write("[".getBytes());
            for (int i=0; i<floats.length; i++){
                fos.write(String.format("%.3f",floats[i]).getBytes());
                if (i<floats.length-1) {
                    fos.write(",".getBytes());
                }
            }
            fos.write("]".getBytes());
            fos.flush();
            fos.close();
        } catch (final Exception e) {
            Log.e(LOG_TAG,  "Exception!"+e.toString());
        }
    }



    public static File cacheDir(Context context){
        Log.i(LOG_TAG,"createDirectory() called...");
        File cacheDir;
        if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)){
            File dataDir = new File(new File(Environment.getExternalStorageDirectory(), "Android"), "data");
            cacheDir = new File(new File(dataDir, context.getPackageName()), "cache");
            Log.i(LOG_TAG,"cacheDir exists in ext storage?: "+cacheDir.isDirectory());
        } else{
            cacheDir=context.getCacheDir();
            Log.i(LOG_TAG,"cacheDir exists in int storage?: "+cacheDir.isDirectory());
        }

        if(!cacheDir.isDirectory()){
            cacheDir.mkdirs();
            Log.i(LOG_TAG,"A New Directory is made[ "+cacheDir.getAbsolutePath());
        }
        else{
            Log.i(LOG_TAG,"Cache Dir already exists[ "+cacheDir.getAbsolutePath());
        }

        return  cacheDir;
    }

    public static File getFile(Context context, String filename){
        //String filename=String.valueOf(url.hashCode());
        File f = new File(cacheDir(context), String.valueOf(filename.hashCode()));
        return f;
    }

    public static void saveFile(String dataToWrite, File file){
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(file));
            outputStreamWriter.write(dataToWrite);
            outputStreamWriter.close();
        } catch (FileNotFoundException e) {
        } catch (IOException e) {
        }
    }
    public static String readFromFile(File file){
        try{
            InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(file));
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String receiveString = "";
            StringBuilder stringBuilder = new StringBuilder();

            while ( (receiveString = bufferedReader.readLine()) != null ) {
                stringBuilder.append(receiveString);
            }
            bufferedReader.close();
            inputStreamReader.close();
            return stringBuilder.toString();
        }
        catch (FileNotFoundException e) {
        } catch (IOException e) {
        }
        return null;

    }
}
