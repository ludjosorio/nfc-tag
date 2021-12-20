package com.example.nfccard;

import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.os.Bundle;
import com.google.android.material.snackbar.Snackbar;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.example.nfccard.databinding.ActivityMainBinding;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private static NfcAdapter mNfcAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        //Initialise NfcAdapter
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);    //If no NfcAdapter, display that the device has no NFC
        if (mNfcAdapter == null) {
            Log.d("NFC", "if not exist NFC");
            Toast.makeText(this, "NO NFC Capabilities",
                    Toast.LENGTH_SHORT).show();
            finish();
        }    //Create a PendingIntent object so the Android system can
        //populate it with the details of the tag when it is scanned.
        // PendingIntent.getActivity(Context,requestcode(identifier for
        //                           intent),intent,int)
        Log.d("NFC", "SI EXISTE NFC");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //nfcAdapter.enableForegroundDispatch(context,pendingIntent,
        //                                    intentFilterArray,
        //                                    techListsArray)
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, this.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        mNfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
    }

    protected void onPause() {
        super.onPause();
        //Onpause stop listening
        if (mNfcAdapter != null) {
            mNfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d("NFC", "onNewIntent");
        super.onNewIntent(intent);
        setIntent(intent);
        resolveIntent(intent);
    }

    void resolveIntent(Intent intent) {
        // 1) Parse the intent and get the action that triggered this intent
        String action = intent.getAction();
        // 2) Check if it was triggered by a tag discovered interruption.
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            //  3) Get an instance of the TAG from the NfcAdapter
            Tag tagFromIntent = (Tag) intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            // 4) Get an instance of the Mifare classic card from this TAG intent
            MifareClassic mfc = MifareClassic.get(tagFromIntent);
            byte[] data;
            try {       //  5.1) Connect to card
                mfc.connect();
                boolean auth = false;
                String cardData = null;
                // 5.2) and get the number of sectors this card has..and loop thru these sectors
                int secCount = mfc.getSectorCount();
                int bCount = 0;
                int bIndex = 0;
                for (int j = 0; j < secCount; j++) {
                    // 6.1) authenticate the sector
                    auth = mfc.authenticateSectorWithKeyA(j, MifareClassic.KEY_DEFAULT);
                    if (auth) {
                        // 6.2) In each sector - get the block count
                        bCount = mfc.getBlockCountInSector(j);
                        bIndex = 0;
                        bIndex = mfc.sectorToBlock(j);
                        for (int i = 0; i < bCount; i++) {
                            // 6.3) Read the block
                            data = mfc.readBlock(bIndex);
                            // 7) Convert the data into a string from Hex format.
                            // Log.i("MIFARE", getHexString(data, data.length));
                            // Log.i("MIFARE", "Paso 7");
                            // Log.i("MIFARE", data.toString());
                            toHexString(data);
                            bIndex++;
                        }
                    } else { // Authentication failed - Handle it

                    }
                }
                mfc.close();
            } catch (IOException e) {
                Log.e("MIFARE", "ERROR" + e.getLocalizedMessage());
            }
        } // End of method
    }

    public static String toHexString(byte[] bytes) {
        final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        Log.d("MIFARE", "response: " + new String(hexChars));
        return new String(hexChars);
    }

}