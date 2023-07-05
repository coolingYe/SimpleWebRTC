package com.example.gesturelib;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class CommonUtils {

    public static boolean copyFilesFromAssetsTo(Context context, String[] fileNames, String dirPath) {
        try {
            File file = new File(dirPath);
            if(!file.exists()){
                boolean result = file.mkdirs();
                if(!result) return false;
            }
            for (String model : fileNames) {
                copyAssetFileToFiles(context, model, dirPath);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static void copyAssetFileToFiles(Context context, String filename, String dirPath) throws IOException {
        File of = new File(dirPath + filename);
        if (!of.exists()) {
            InputStream is = context.getAssets().open(filename);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);

            of.createNewFile();
            FileOutputStream os = new FileOutputStream(of);
            os.write(buffer);
            os.close();
            is.close();
        }
    }

}
