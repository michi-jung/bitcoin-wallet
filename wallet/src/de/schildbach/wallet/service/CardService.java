package de.schildbach.wallet.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Currency;
import java.util.Locale;

import org.bitcoinj.script.ScriptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Address;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.ui.send.FeeCategory;
import de.schildbach.wallet.ui.send.SendCoinsActivity;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.ui.send.TapToSendCoinsActivity;

import static de.schildbach.wallet.ui.send.SendCoinsActivity.INTENT_EXTRA_PAYMENT_INTENT;


/**
 * Created by mijung on 28.05.17.
 */

public class CardService extends HostApduService {
    public static final String ACTION_CANCEL_TRANSACTION = CardService.class.getPackage().getName() + ".cancel_transaction";
    public static final String ACTION_BROADCAST_TRANSACTION = CardService.class.getPackage().getName() + ".broadcast_transaction";
    public static final String ACTION_BROADCAST_TRANSACTION_TX = "tx";

    private static final byte[] SELECT_2PAY_SYS_DDF01_CAPDU =
            HexStringToByteArray("00A404000E325041592E5359532E444446303100");
    private static final byte[] SELECT_2PAY_SYS_DDF01_RAPDU =
            HexStringToByteArray(
                    "6F20840E325041592E5359532E4444463031A50EBF0C0B61094F07426974636F696E9000");
    private static final byte[] SELECT_BITCOIN_CAPDU =
            HexStringToByteArray("00A4040007426974636F696E00");
    private static final byte[] SELECT_BITCOIN_RAPDU =
            HexStringToByteArray(
                    "6F468407426974636F696EA53B9F38169F02069F03069F4E509F66045F2A029A039C019F3704BF0C1FD21D104761739001010010FFFF0112170158014254434F00000000000000009000");
    private static final byte[] FILE_NOT_FOUND_RAPDU = HexStringToByteArray("6A82");
    private static final byte[] COMMAND_NOT_ALLOWED = HexStringToByteArray("6986");
    private static final byte[] EGPO_HEADER = HexStringToByteArray("80E00000");

    private static final Logger log = LoggerFactory.getLogger(CardService.class);

    private byte[] transaction = null;

    private static long BCDtoLong(byte[] bcd) {
        StringBuilder sb = new StringBuilder(bcd.length * 2);
        for (byte b : bcd) {
            sb.append(String.format("%02x", b));
        }
        return Long.valueOf(sb.toString());
    }

    private static String ANStoString(byte[] ans) {
        int idx;

        for (idx = 0; idx < ans.length; idx++)
            if (ans[idx] == 0x00)
                break;

        return new String(Arrays.copyOfRange(ans, 0, idx), StandardCharsets.ISO_8859_1);
    }

    private static Calendar YMDtoCalendar(byte[] dmy) {
        int year = (int)BCDtoLong(Arrays.copyOfRange(dmy, 0, 1));
        int month = (int)BCDtoLong(Arrays.copyOfRange(dmy, 1, 2)) - 1;
        int dayOfMonth = 30 + (int)BCDtoLong(Arrays.copyOfRange(dmy, 2, 3));

        return new GregorianCalendar(year, month, dayOfMonth);
    }

    private static Currency ISO4217BCDtoCurrency(byte[] iso4217) {
        switch ((int)BCDtoLong(iso4217)) {
            case 826:
                return Currency.getInstance("GBP");
            case 840:
                return Currency.getInstance("USD");
            case 978:
                return Currency.getInstance("EUR");
            default:
                return null;
        }
    }

    private static Coin LittleEndianToCoin(byte[] le) {
        long satoshis = 0;
        int i;

        log.info("LittleEndianToCoin: " + Arrays.toString(le));

        for (i = le.length - 1; i >= 0; i--) {
            satoshis *= 256;
            satoshis += ((int)le[i] & 0xFF);
        }

        log.info("LittleEndianToCoin: " + satoshis);

        return Coin.valueOf(satoshis);
    }

    private static PaymentIntent.Output[] buildSimplePayTo(final Coin amount, final Address address) {
        return new PaymentIntent.Output[] { new PaymentIntent.Output(amount, ScriptBuilder.createOutputScript(address)) };
    }

    private static String FormatAmount(long amount, Currency currency) {
        return String.format(Locale.getDefault(), "%s%.2f", currency.getSymbol(),
                (float)amount / Math.pow(10, currency.getDefaultFractionDigits()));
    }

    private static String BuildPaymentIntentMemo(String merchant, byte transactionType, long amountAuthorized, long amountOther, Currency currency) {
        switch (transactionType) {
            case 0x00:
                return FormatAmount(amountAuthorized, currency) +
                        " for purchase of goods or services.";
            default:
                return "Unknown Transaction Type: " + transactionType;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent.getAction();

        log.info("transactionReceiver.onStartCommand(" + intent.getAction() + ")");

        if (ACTION_BROADCAST_TRANSACTION.equals(action)) {
            transaction = intent.getByteArrayExtra(ACTION_BROADCAST_TRANSACTION_TX);
        } else if (ACTION_CANCEL_TRANSACTION.equals(action)) {
            transaction = null;
        }

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        log.info("onCreate: " + ACTION_BROADCAST_TRANSACTION);

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_BROADCAST_TRANSACTION);
        intentFilter.addAction(ACTION_CANCEL_TRANSACTION);
        getApplication().registerReceiver(transactionReceiver, intentFilter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        log.info("onDestroy");

        getApplication().unregisterReceiver(transactionReceiver);
    }

    @Override
    public void onDeactivated(int reason) {
        log.info("onDeactivated");
    }

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        if (transaction != null) {
            log.info("processCommandApdu. transaction: " + transaction.toString());
        } else {
            log.info("processCommandApdu. transaction: none");
        }

        if (Arrays.equals(SELECT_2PAY_SYS_DDF01_CAPDU, commandApdu)) {
            return SELECT_2PAY_SYS_DDF01_RAPDU;
        } else if (Arrays.equals(SELECT_BITCOIN_CAPDU, commandApdu)) {
            return SELECT_BITCOIN_RAPDU;
        } else if (Arrays.equals(EGPO_HEADER, Arrays.copyOfRange(commandApdu, 0, 4))) {
            long amountAuthorized = BCDtoLong(Arrays.copyOfRange(commandApdu, 7, 13));
            long amountOther = BCDtoLong(Arrays.copyOfRange(commandApdu, 13, 19));
            String merchantNameAndLocation = ANStoString(Arrays.copyOfRange(commandApdu, 19, 99));
            byte[] terminalTransactionQualifiers = Arrays.copyOfRange(commandApdu, 99, 103);
            Currency currency = ISO4217BCDtoCurrency(Arrays.copyOfRange(commandApdu, 103, 105));
            Calendar transactionDate = YMDtoCalendar(Arrays.copyOfRange(commandApdu, 105, 108));
            byte transactionType = commandApdu[108];
            byte[] readerUnpredictableNumber = Arrays.copyOfRange(commandApdu, 108, 112);
            Coin amount = LittleEndianToCoin(Arrays.copyOfRange(commandApdu, 148, 156));
            Coin feePerKiB = LittleEndianToCoin(Arrays.copyOfRange(commandApdu, 156, 164));
            Address address = new Address(Constants.NETWORK_PARAMETERS, (int)commandApdu[164], Arrays.copyOfRange(commandApdu, 165, 185));
            PaymentIntent paymentIntent = new PaymentIntent(PaymentIntent.Standard.BIP70, merchantNameAndLocation, null, buildSimplePayTo(amount, address),
                    BuildPaymentIntentMemo(merchantNameAndLocation, transactionType, amountAuthorized, amountOther, currency),
                    null, null, null, null);

            log.info("             Amount, Authorized: " + amountAuthorized);
            log.info("                  Amount, Other: " + amountOther);
            log.info("     Merchant Name and Location: " + merchantNameAndLocation);
            log.info("Terminal Transaction Qualifiers: " + Arrays.toString(terminalTransactionQualifiers));
            log.info("                       Currency: " + currency.getDisplayName());
            log.info("               Transaction Date: " +
                        transactionDate.get(Calendar.YEAR) + "/" +
                        transactionDate.get(Calendar.MONTH) + "/" +
                        transactionDate.get(Calendar.DAY_OF_MONTH)
            );
            log.info("               Transaction Type: " + transactionType);
            log.info("    Reader Unpredictable Number: " + Arrays.toString(readerUnpredictableNumber));
            log.info("             Amount, Authorized: " + amount.toFriendlyString());
            log.info("               Fee per Kilobyte: " + feePerKiB.toFriendlyString());
            log.info("                        Address: " + address.toBase58());
            log.info("                 Payment Intent: " + paymentIntent.toString());


            TapToSendCoinsActivity.start(CardService.this, paymentIntent, feePerKiB);

            /* SendCoinsActivity.start(CardService.this, paymentIntent, FeeCategory.NORMAL,
                                                                     Intent.FLAG_ACTIVITY_NEW_TASK); */

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

    private final BroadcastReceiver transactionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();

            log.info("transactionReceiver.onReceive(" + intent.getAction() + ")");

            if (ACTION_BROADCAST_TRANSACTION.equals(action)) {
                transaction = intent.getByteArrayExtra(ACTION_BROADCAST_TRANSACTION_TX);
            } else if (ACTION_CANCEL_TRANSACTION.equals(action)) {
                transaction = null;
            }
        }
    };
}
