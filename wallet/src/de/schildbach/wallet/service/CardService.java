package de.schildbach.wallet.service;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import java.util.Arrays;


/**
 * Created by mijung on 28.05.17.
 */

public class CardService extends HostApduService {
    private static final byte[] SELECT_2PAY_SYS_DDF01_CAPDU =
            HexStringToByteArray("00A404000E325041592E5359532E444446303100");
    private static final byte[] SELECT_2PAY_SYS_DDF01_RAPDU =
            HexStringToByteArray(
                    "6F20840E325041592E5359532E4444463031A50EBF0C0B61094F07426974636F696E9000");
    private static final byte[] SELECT_BITCOIN_CAPDU =
            HexStringToByteArray("00A4040007426974636F696E00");
    private static final byte[] SELECT_BITCOIN_RAPDU =
            HexStringToByteArray(
                    "6F3C8407426974636F696EA5319F380C9F66049F02065F2A029F3704BF0C1FD21D104761739001010010FFFF0112170158014254434F00000000000000009000");
    private static final byte[] FILE_NOT_FOUND_RAPDU = HexStringToByteArray("6A82");
    private static final byte[] COMMAND_NOT_ALLOWED = HexStringToByteArray("6986");
    private static final byte[] EGPO_HEADER = HexStringToByteArray("80E00000");

    @Override
    public void onDeactivated(int reason) {}

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        if (Arrays.equals(SELECT_2PAY_SYS_DDF01_CAPDU, commandApdu)) {
            return SELECT_2PAY_SYS_DDF01_RAPDU;
        } else if (Arrays.equals(SELECT_BITCOIN_CAPDU, commandApdu)) {
            return SELECT_BITCOIN_RAPDU;
        } else if (Arrays.equals(EGPO_HEADER, Arrays.copyOfRange(commandApdu, 0, 4))) {
            return COMMAND_NOT_ALLOWED;
        } else {
            return FILE_NOT_FOUND_RAPDU;
        }
    }

    public static byte[] HexStringToByteArray(String s) throws IllegalArgumentException {
        int len = s.length();
        if (len % 2 == 1) {
            throw new IllegalArgumentException("Hex string must have even number of characters");
        }
        byte[] data = new byte[len / 2]; // Allocate 1 byte per 2 hex characters
        for (int i = 0; i < len; i += 2) {
            // Convert each character into a integer (base-16), then bit-shift into place
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}
