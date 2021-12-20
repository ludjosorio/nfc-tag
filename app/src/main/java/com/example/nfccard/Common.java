package com.example.nfccard;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.nfc.Tag;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;

public class Common extends Application {

    private static Tag mTag = null;
    private static byte[] mUID = null;
    private static Context mAppContext;
    private ArrayList<String> mKeysWithOrder;

    // TODO: add description include KeyMapCreator Class
    private static int mKeyMapFrom = -1;
    private static int mKeyMapTo = -1;

    private int mLastSector = -1;
    private int mFirstSector = 0;
    private int mKeyMapStatus = 0;
    private static SparseArray<byte[][]> mKeyMap = null;

    /**
     * Initialize the {@link #mAppContext} with the application context.
     * Some functions depend on this context.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        mAppContext = getApplicationContext();
//        mScale = getResources().getDisplayMetrics().density;
//
//        try {
//            mVersionCode = getPackageManager().getPackageInfo(
//                    getPackageName(), 0).versionName;
//        } catch (PackageManager.NameNotFoundException e) {
//            Log.d(LOG_TAG, "Version not found.");
//        }
    }

    /**
     * Create a connected {@link MCReader} if there is a present MIFARE Classic
     * tag. If there is no MIFARE Classic tag an error
     * message will be displayed to the user.
     *
     * @param context The Context in which the error Toast will be shown.
     * @return A connected {@link MCReader} or "null" if no tag was present.
     */
    public static MCReader checkForTagAndCreateReader(Context context) {
        MCReader reader;
        boolean tagLost = false;
        // Check for tag.
        if (mTag != null && (reader = MCReader.get(mTag)) != null) {
            try {
                reader.connect();
                Log.d("MCReader", "reader connect");
            } catch (Exception e) {
                Log.d("MCReader", "catch connect");
                tagLost = true;
            }
            if (!tagLost && !reader.isConnected()) {
                Log.d("MCReader", "reader close");
                reader.close();
                tagLost = true;
            }
            if (!tagLost) {
                Log.d("MCReader", "not exist tagLost");
                return reader;
            }
        }

        // Error. The tag is gone.
        Toast.makeText(context, "Error: Etiqueta retirada durante la lectura",
                Toast.LENGTH_LONG).show();
        return null;
    }

    public static void setTag(Tag tag) {
        mTag = tag;
        mUID = tag.getId();
    }

    /**
     * Get the shared preferences with application context for saving
     * and loading ("global") values.
     *
     * @return The shared preferences object with application context.
     */
    public static SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(mAppContext);
    }


    /**
     * Convert an array of bytes into a string of hex values.
     *
     * @param bytes Bytes to convert.
     * @return The bytes in hex string format.
     */
    public static String bytes2Hex(byte[] bytes) {
        StringBuilder ret = new StringBuilder();
        if (bytes != null) {
            for (Byte b : bytes) {
                ret.append(String.format("%02X", b.intValue() & 0xFF));
            }
        }
        return ret.toString();
    }

    /**
     * Convert a string of hex data into a byte array.
     * Original author is: Dave L. (http://stackoverflow.com/a/140861).
     *
     * @param hex The hex string to convert
     * @return An array of bytes with the values of the string.
     */
    public static byte[] hex2Bytes(String hex) {
        if (!(hex != null && hex.length() % 2 == 0
                && hex.matches("[0-9A-Fa-f]+"))) {
            return null;
        }
        int len = hex.length();
        byte[] data = new byte[len / 2];
        try {
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                        + Character.digit(hex.charAt(i + 1), 16));
            }
        } catch (Exception e) {
            Log.d("COMMON", "Argument(s) for hexStringToByteArray(String s)"
                    + "was not a hex string");
        }
        return data;
    }

    /**
     * Get the key map start point.
     *
     * @return {@link #mKeyMapFrom}
     */
    public static int getKeyMapRangeFrom() {
        return mKeyMapFrom;
    }

    /**
     * Get the key map end point
     *
     * @return {@link #mKeyMapTo}
     */
    public static int getKeyMapRangeTo() {
        return mKeyMapTo;
    }


    public static SparseArray<byte[][]> getKeyMap() {
        return mKeyMap;
    }

    public static void setKeyMap(SparseArray<byte[][]> value) {
        mKeyMap = value;
    }

}
