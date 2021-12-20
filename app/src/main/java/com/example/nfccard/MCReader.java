package com.example.nfccard;

import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.MifareClassic;
import android.preference.Preference;
import android.util.Log;
import android.util.SparseArray;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class MCReader {

    private final MifareClassic mMFC;
    public static final String NO_KEY = "------------";
    public static final String NO_DATA = "--------------------------------";

    private MCReader(Tag tag) {
        MifareClassic tmpMFC;
        try {
            tmpMFC = MifareClassic.get(tag);
        } catch (Exception e) {
            Log.e("MCReader", "Could not create MIFARE Classic reader for the"
                    + "provided tag (even after patching it).");
            throw e;
        }
        mMFC = tmpMFC;
    }

    /**
     * Get new instance of {@link MCReader}.
     * If the tag is "null" or if it is not a MIFARE Classic tag, "null"
     * will be returned.
     *
     * @param tag The tag to operate on.
     * @return {@link MCReader} object or "null" if tag is "null" or tag is
     * not MIFARE Classic.
     */
    public static MCReader get(Tag tag) {
        MCReader mcr = null;
        if (tag != null) {
            try {
                mcr = new MCReader(tag);
                if (!mcr.isMifareClassic()) {
                    return null;
                }
            } catch (RuntimeException ex) {
                // Should not happen. However, it did happen for OnePlus5T
                // user according to Google Play crash reports.
                return null;
            }
        }
        return mcr;
    }

    public boolean isMifareClassic() {
        return mMFC != null;
    }

    public boolean isConnected() {
        return mMFC.isConnected();
    }

    /**
     * Connect the reader to the tag. If the reader is already connected the
     * "connect" will be skipped. If "connect" will block for more than 500ms
     * then connecting will be aborted.
     *
     * @throws Exception Something went wrong while connecting to the tag.
     */
    public void connect() throws Exception {
        final AtomicBoolean error = new AtomicBoolean(false);

        // Do not connect if already connected.
        if (isConnected()) {
            return;
        }

        // Connect in a worker thread. (connect() might be blocking).
        Thread t = new Thread(() -> {
            try {
                mMFC.connect();
            } catch (IOException | IllegalStateException ex) {
                error.set(true);
            }
        });
        t.start();

        // Wait for the connection (max 500millis).
        try {
            t.join(500);
        } catch (InterruptedException ex) {
            error.set(true);
        }

        // If there was an error log it and throw an exception.
        if (error.get()) {
            Log.d("MCReader", "Error while connecting to tag.");
            throw new Exception("Error while connecting to tag.");
        }
    }

    /**
     * Close the connection between reader and tag.
     */
    public void close() {
        try {
            mMFC.close();
        } catch (IOException e) {
            Log.d("MCReader", "Error on closing tag.");
        }
    }


    /**
     * Read as much as possible from the tag with the given key information.
     *
     * @param keyMap Keys (A and B) mapped to a sector.
     *               See {@link // #buildNextKeyMapPart()}.
     * @return A Key-Value Pair. Keys are the sector numbers, values
     * are the tag data. This tag data (values) are arrays containing
     * one block per field (index 0-3 or 0-15).
     * If a block is "null" it means that the block couldn't be
     * read with the given key information.<br />
     * On Error, "null" will be returned (tag was removed during reading or
     * keyMap is null). If none of the keys in the key map are valid for reading
     * (and therefore no sector is read), an empty set (SparseArray.size() == 0)
     * will be returned.
     * // @see #buildNextKeyMapPart()
     */
    public SparseArray<String[]> readAsMuchAsPossible(
            SparseArray<byte[][]> keyMap) {
        SparseArray<String[]> resultSparseArray;
        Log.d("MIFARE READASMUCH", "READ");
        if (keyMap != null && keyMap.size() > 0) {
            Log.d("MIFARE READASMUCH", "inside READASMUCH");
            resultSparseArray = new SparseArray<>(keyMap.size());
            // For all entries in map do:
            for (int i = 0; i < keyMap.size(); i++) {
                String[][] results = new String[2][];
                try {
                    if (keyMap.valueAt(i)[0] != null) {
                        // Read with key A.
                        results[0] = readSector(
                                keyMap.keyAt(i), keyMap.valueAt(i)[0], false);
                    }
                    if (keyMap.valueAt(i)[1] != null) {
                        // Read with key B.
                        results[1] = readSector(
                                keyMap.keyAt(i), keyMap.valueAt(i)[1], true);
                    }
                } catch (TagLostException e) {
                    return null;
                }
                // Merge results.
                if (results[0] != null || results[1] != null) {
                    resultSparseArray.put(keyMap.keyAt(i), mergeSectorData(
                            results[0], results[1]));
                }
            }
            return resultSparseArray;
        }
        return null;
    }

    /**
     * Merge the result of two {@link #readSector(int, byte[], boolean)}
     * calls on the same sector (with different keys or authentication methods).
     * In this case merging means empty blocks will be overwritten with non
     * empty ones and the keys will be added correctly to the sector trailer.
     * The access conditions will be taken from the first (firstResult)
     * parameter if it is not null.
     *
     * @param firstResult  First
     *                     {@link #readSector(int, byte[], boolean)} result.
     * @param secondResult Second
     *                     {@link #readSector(int, byte[], boolean)} result.
     * @return Array (sector) as result of merging the given
     * sectors. If a block is {@link #NO_DATA} it
     * means that none of the given sectors contained data from this block.
     * @see #readSector(int, byte[], boolean)
     * @see #authenticate(int, byte[], boolean)
     */
    public String[] mergeSectorData(String[] firstResult,
                                    String[] secondResult) {
        String[] ret = null;
        if (firstResult != null || secondResult != null) {
            if ((firstResult != null && secondResult != null)
                    && firstResult.length != secondResult.length) {
                return null;
            }
            int length = (firstResult != null)
                    ? firstResult.length : secondResult.length;
            ArrayList<String> blocks = new ArrayList<>();
            // Merge data blocks.
            for (int i = 0; i < length - 1; i++) {
                if (firstResult != null && firstResult[i] != null
                        && !firstResult[i].equals(NO_DATA)) {
                    blocks.add(firstResult[i]);
                } else if (secondResult != null && secondResult[i] != null
                        && !secondResult[i].equals(NO_DATA)) {
                    blocks.add(secondResult[i]);
                } else {
                    // None of the results got the data form the block.
                    blocks.add(NO_DATA);
                }
            }
            ret = blocks.toArray(new String[blocks.size() + 1]);
            int last = length - 1;
            // Merge sector trailer.
            if (firstResult != null && firstResult[last] != null
                    && !firstResult[last].equals(NO_DATA)) {
                // Take first for sector trailer.
                ret[last] = firstResult[last];
                if (secondResult != null && secondResult[last] != null
                        && !secondResult[last].equals(NO_DATA)) {
                    // Merge key form second result to sector trailer.
                    ret[last] = ret[last].substring(0, 20)
                            + secondResult[last].substring(20);
                }
            } else if (secondResult != null && secondResult[last] != null
                    && !secondResult[last].equals(NO_DATA)) {
                // No first result. Take second result as sector trailer.
                ret[last] = secondResult[last];
            } else {
                // No sector trailer at all.
                ret[last] = NO_DATA;
            }
        }
        return ret;
    }

    public String[] readSector(int sectorIndex, byte[] key,
                               boolean useAsKeyB) throws TagLostException {
        boolean auth = authenticate(sectorIndex, key, useAsKeyB);
        String[] ret = null;
        // Read sector.
        if (auth) {
            // Read all blocks.
            ArrayList<String> blocks = new ArrayList<>();
            int firstBlock = mMFC.sectorToBlock(sectorIndex);
            int lastBlock = firstBlock + 4;
            if (mMFC.getSize() == MifareClassic.SIZE_4K
                    && sectorIndex > 31) {
                lastBlock = firstBlock + 16;
            }
            for (int i = firstBlock; i < lastBlock; i++) {
                try {
                    byte[] blockBytes = mMFC.readBlock(i);
                    // mMFC.readBlock(i) must return 16 bytes or throw an error.
                    // At least this is what the documentation says.
                    // On Samsung's Galaxy S5 and Sony's Xperia Z2 however, it
                    // sometimes returns < 16 bytes for unknown reasons.
                    // Update: Aaand sometimes it returns more than 16 bytes...
                    // The appended byte(s) are 0x00.
                    if (blockBytes.length < 16) {
                        throw new IOException();
                    }
                    if (blockBytes.length > 16) {
                        blockBytes = Arrays.copyOf(blockBytes, 16);
                    }

                    blocks.add(Common.bytes2Hex(blockBytes));
                } catch (TagLostException e) {
                    throw e;
                } catch (IOException e) {
                    // Could not read block.
                    // (Maybe due to key/authentication method.)
                    Log.d("MCReader", "(Recoverable) Error while reading block "
                            + i + " from tag.");
                    blocks.add(NO_DATA);
                    if (!mMFC.isConnected()) {
                        throw new TagLostException(
                                "Tag removed during readSector(...)");
                    }
                    // After an error, a re-authentication is needed.
                    authenticate(sectorIndex, key, useAsKeyB);
                }
            }
            ret = blocks.toArray(new String[0]);
            int last = ret.length - 1;

            // Validate if it was possible to read any data.
            boolean noData = true;
            for (String s : ret) {
                if (!s.equals(NO_DATA)) {
                    noData = false;
                    break;
                }
            }
            if (noData) {
                // Was is possible to read any data (especially with key B)?
                // If Key B may be read in the corresponding Sector Trailer,
                // it cannot serve for authentication (according to NXP).
                // What they mean is that you can authenticate successfully,
                // but can not read data. In this case the
                // readBlock() result is 0 for each block.
                // Also, a tag might be bricked in a way that the authentication
                // works, but reading data does not.
                ret = null;
            } else {
                // Merge key in last block (sector trailer).
                if (!useAsKeyB) {
                    if (isKeyBReadable(Common.hex2Bytes(
                            ret[last].substring(12, 20)))) {
                        ret[last] = Common.bytes2Hex(key)
                                + ret[last].substring(12, 32);
                    } else {
                        ret[last] = Common.bytes2Hex(key)
                                + ret[last].substring(12, 20) + NO_KEY;
                    }
                } else {
                    ret[last] = NO_KEY + ret[last].substring(12, 20)
                            + Common.bytes2Hex(key);
                }
            }
        }
        return ret;
    }

    /**
     * Check if key B is readable.
     * Key B is readable for the following configurations:
     * <ul>
     * <li>C1 = 0, C2 = 0, C3 = 0</li>
     * <li>C1 = 0, C2 = 0, C3 = 1</li>
     * <li>C1 = 0, C2 = 1, C3 = 0</li>
     * </ul>
     *
     * @param ac The access conditions (4 bytes).
     * @return True if key B is readable. False otherwise.
     */
    private boolean isKeyBReadable(byte[] ac) {
        if (ac == null) {
            return false;
        }
        byte c1 = (byte) ((ac[1] & 0x80) >>> 7);
        byte c2 = (byte) ((ac[2] & 0x08) >>> 3);
        byte c3 = (byte) ((ac[2] & 0x80) >>> 7);
        return c1 == 0
                && (c2 == 0 && c3 == 0)
                || (c2 == 1 && c3 == 0)
                || (c2 == 0 && c3 == 1);
    }

    private boolean authenticate(int sectorIndex, byte[] key,
                                 boolean useAsKeyB) {
        // Fetch the retry authentication option. Some tags and
        // devices have strange issues and need a retry in order to work...
        // Info: https://github.com/ikarus23/MifareClassicTool/issues/134
        // and https://github.com/ikarus23/MifareClassicTool/issues/106
        boolean retryAuth = Common.getPreferences().getBoolean("use_retry_authentication", false);
        int retryCount = Common.getPreferences().getInt(
                "retry_authentication_count", 1);
        if (key == null) {
            return false;
        }
        boolean ret = false;
        for (int i = 0; i < retryCount + 1; i++) {
            try {
                if (!useAsKeyB) {
                    // Key A.
                    ret = mMFC.authenticateSectorWithKeyA(sectorIndex, key);
                } else {
                    // Key B.
                    ret = mMFC.authenticateSectorWithKeyB(sectorIndex, key);
                }
            } catch (IOException | ArrayIndexOutOfBoundsException e) {
                Log.d("MCReader", "Error authenticating with tag.");
                return false;
            }
            // Retry?
            if (ret || !retryAuth) {
                break;
            }
        }
        return ret;
    }

}
