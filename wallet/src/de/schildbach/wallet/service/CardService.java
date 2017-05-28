package de.schildbach.wallet.service;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

/**
 * Created by mijung on 28.05.17.
 */

public class CardService extends HostApduService {
    @Override
    public void onDeactivated(int reason) {}

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        byte statusword[] = new byte[2];
        statusword[0] = 0x00;
        statusword[1] = 0x00;
        return statusword;
    }
}
