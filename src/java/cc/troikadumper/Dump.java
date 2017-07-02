package cc.troikadumper;

import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Scanner;
import java.util.TimeZone;

import cc.troikadumper.utils.HexUtils;

public class Dump {
    public static final String FILENAME_FORMAT = "%04d-%02d-%02d_%02d%02d%02d_%d_%dRUB.txt";
    public static final String FILENAME_REGEXP = "([0-9]{4})-([0-9]{2})-([0-9]{2})_([0-9]{6})_([0-9]+)_([0-9]+)RUB.txt";

    public static final int BLOCK_COUNT = 4;
    public static final int BLOCK_SIZE = MifareClassic.BLOCK_SIZE;
    public static final int SECTOR_INDEX = 8;

    public static final byte[] KEY_B =
            {(byte)0xE3,(byte)0x51,(byte)0x73,(byte)0x49, (byte)0x4A,(byte)0x81};

    public static final byte[] KEY_A =
            {(byte)0xA7,(byte)0x3F,(byte)0x5D,(byte)0xC1, (byte)0xD3,(byte)0x33};

    public static final byte[] KEY_0 =
            {(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00, (byte)0x00,(byte)0x00};

    // raw
    protected byte[]    uid;
    protected byte[][]  data;

    // parsed
    protected int    cardNumber;
    protected int    balance;
    protected Date   lastUsageDate;
    protected int    lastValidatorId;

    public Dump(byte[] uid, byte[][] sector8) {
        this.uid = uid;
        this.data = sector8;
        parse();
    }

    public static Dump fromTag(Tag tag) throws IOException {
        MifareClassic mfc = getMifareClassic(tag);

        int blockCount = mfc.getBlockCountInSector(SECTOR_INDEX);
        if (blockCount < BLOCK_COUNT) {
            throw new IOException("Wtf? Not enough blocks on this card");
        }

        byte[][] data = new byte[BLOCK_COUNT][BLOCK_SIZE];

        for (int i = 0; i < BLOCK_COUNT; i++) {
            data[i] = mfc.readBlock(mfc.sectorToBlock(SECTOR_INDEX) + i);
        }

        return new Dump(tag.getId(), data);
    }

    public static Dump fromFile(File file) throws IOException {
        FileInputStream fs = new FileInputStream(file);
        Scanner scanner = new Scanner(fs, "US-ASCII");
        byte[] uid = HexUtils.fromString(scanner.nextLine());

        byte[][] data = new byte[BLOCK_COUNT][BLOCK_SIZE];
        for (int i = 0; i < BLOCK_COUNT; i++) {
            data[i] = HexUtils.fromString(scanner.nextLine());
        }

        return new Dump(uid, data);
    }

    protected static MifareClassic getMifareClassic(Tag tag) throws IOException {
        MifareClassic mfc = MifareClassic.get(tag);
        mfc.connect();

        // fucked up card
        if (mfc.authenticateSectorWithKeyA(SECTOR_INDEX, KEY_0) && mfc.authenticateSectorWithKeyB(SECTOR_INDEX, KEY_0)) {
            return mfc;
        }

        // good card
        if (mfc.authenticateSectorWithKeyA(SECTOR_INDEX, KEY_A) && mfc.authenticateSectorWithKeyB(SECTOR_INDEX, KEY_B)
                ) {
            return mfc;
        }

        throw new IOException("No permissions");
    }

    protected void parse() {
        // block#0 bytes#3-6
        cardNumber = intval(data[0][3], data[0][4], data[0][5], data[0][6]) >> 4;

        // block#1 bytes#0-1
        lastValidatorId = intval(data[1][0], data[1][1]);

        // block#1 bytes#2-4½
        int lastUsageDay = intval(data[1][2], data[1][3]);
        if (lastUsageDay > 0) {
            double lastUsageTime = (double) intval(
                    (byte) (data[1][4] >> 4 & 0x0F),
                    (byte) (data[1][5] >> 4 & 0x0F | data[1][4] << 4 & 0xF0)
            );
            lastUsageTime = lastUsageTime / 120.0;
            int lastUsageHour = (int)Math.floor(lastUsageTime);
            int lastUsageMinute = (int)Math.round((lastUsageTime % 1) * 60);

            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT+3"));
            c.set(1992, 0, 1, lastUsageHour, lastUsageMinute);
            c.add(Calendar.DATE, lastUsageDay - 1);
            lastUsageDate = c.getTime();
        } else {
            lastUsageDate = null;
        }

        // block#1 bytes#8.5-10.5 (??)
        balance = intval(
                (byte)(data[1][8]  & 0b00001111),
                (byte) data[1][9], //  87654321
                (byte)(data[1][10] & 0b11111000)
        ) / 200;
    }

    public void write(Tag tag) throws IOException {
        MifareClassic mfc = getMifareClassic(tag);

        if (!Arrays.equals(tag.getId(), this.getUid())) {
            throw new IOException("Card UID mismatch: \n"
                    + HexUtils.toString(tag.getId()) + " (card) != "
                    + HexUtils.toString(getUid()) + " (dump)");
        }

        int numBlocksToWrite = BLOCK_COUNT - 1; // do not overwrite last block (keys)
        int startBlockIndex = mfc.sectorToBlock(SECTOR_INDEX);
        for (int i = 0; i < numBlocksToWrite; i++) {
            mfc.writeBlock(startBlockIndex + i, data[i]);
        }
    }

    public File save(File dir) throws IOException {
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            throw new IOException("Can not write to external storage");
        }

        if (!dir.isDirectory()) {
            throw new IOException("Not a dir");
        }

        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Can not make save dir");
        }

        File file = new File(dir, makeFilename());
        FileOutputStream stream = new FileOutputStream(file);
        OutputStreamWriter out = new OutputStreamWriter(stream);
        out.write(getUidAsString() + "\r\n");
        for (String block : getDataAsStrings()) {
            out.write(block + "\r\n");
        }
        out.close();

        return file;
    }

    protected String makeFilename() {
        Date now = new Date();
        return String.format(
                FILENAME_FORMAT,
                now.getYear() + 1900, now.getMonth() + 1, now.getDate(),
                now.getHours(), now.getMinutes(), now.getSeconds(),
                getCardNumber(), getBalance()
        );
    }

    public byte[] getUid() {
        return uid;
    }

    public String getUidAsString() {
        return HexUtils.toString(getUid());
    }

    public byte[][] getData() {
        return data;
    }

    public String[] getDataAsStrings() {
        String blocks[] = new String[data.length];
        for (int i = 0; i < data.length; i++) {
            blocks[i] = HexUtils.toString(data[i]);
        }
        return blocks;
    }

    public Date getLastUsageDate() {
        return lastUsageDate;
    }

    public String getLastUsageDateAsString() {
        if (lastUsageDate == null) {
            return "<NEVER USED>";
        }
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(lastUsageDate);
    }

    public int getLastValidatorId() {
        return lastValidatorId;
    }

    public String getLastValidatorIdAsString() {
        return "ID# " + getLastValidatorId();
    }

    public int getBalance() {
        return balance;
    }

    public String getBalanceAsString() {
        return "" + getBalance() + " RUB";
    }

    public int getCardNumber() {
        return cardNumber;
    }

    public String getCardNumberAsString() {
        return formatCardNumber(cardNumber);
    }

    public static String formatCardNumber(int cardNumber) {
        int cardNum3 = cardNumber % 1000;
        int cardNum2 = (int)Math.floor(cardNumber / 1000) % 1000;
        int cardNum1 = (int)Math.floor(cardNumber / 1000000) % 1000;
        return String.format("%04d %03d %03d", cardNum1, cardNum2, cardNum3);
    }

    public String toString() {
        return "[Card UID=" + getUidAsString() + " " + getBalanceAsString() + "RUR]";
    }

    protected static int intval(byte... bytes) {
        int value = 0;
        for (int i = 0; i < bytes.length; i++) {
            int x = (int)bytes[bytes.length - i - 1];
            while (x < 0) x = 256 + x;
            value += x * Math.pow(0x100, i);
        }
        return value;
    }

}
