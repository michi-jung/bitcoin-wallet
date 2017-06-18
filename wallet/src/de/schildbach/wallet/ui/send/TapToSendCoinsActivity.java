package de.schildbach.wallet.ui.send;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.ExchangeRate;
import de.schildbach.wallet.data.ExchangeRatesLoader;
import de.schildbach.wallet.data.ExchangeRatesProvider;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.service.CardService;
import de.schildbach.wallet.ui.AbstractBindServiceActivity;
import de.schildbach.wallet.ui.CurrencyAmountView;
import de.schildbach.wallet.ui.CurrencyCalculatorLink;
import de.schildbach.wallet_test.R;

/**
 * Created by mijung on 18.06.17.
 */

public class TapToSendCoinsActivity extends Activity {
    public static final String INTENT_EXTRA_PAYMENT_INTENT = "payment_intent";
    public static final String INTENT_EXTRA_FEE_PER_KIB = "fee_per_kib";
    private View payeeGroup;
    private TextView payeeNameView;
    private View memoGroup;
    private Button cancelButton;
    private TextView memoView;
    private PaymentIntent paymentIntent = null;
    private CurrencyCalculatorLink amountCalculatorLink;
    private CurrencyCalculatorLink feesCalculatorLink;
    private Configuration config;
    private WalletApplication application;
    private LoaderManager loaderManager;
    private Activity activity = this;
    private static final Logger log = LoggerFactory.getLogger(TapToSendCoinsActivity.class);

    private static final int ID_RATE_LOADER = 1;

    public static void start(final Context context, PaymentIntent paymentIntent, Coin feePerKiB) {
        final Intent intent = new Intent(context, TapToSendCoinsActivity.class);
        intent.putExtra(INTENT_EXTRA_PAYMENT_INTENT, paymentIntent);
        intent.putExtra(INTENT_EXTRA_FEE_PER_KIB, feePerKiB.longValue());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        application = (WalletApplication)getApplication();
        config = application.getConfiguration();
        loaderManager = getLoaderManager();

        paymentIntent = getIntent().getParcelableExtra(INTENT_EXTRA_PAYMENT_INTENT);

        SendRequest sendRequest = paymentIntent.toSendRequest();
        sendRequest.feePerKb = Coin.valueOf(getIntent().getLongExtra(INTENT_EXTRA_FEE_PER_KIB, 0));
        sendRequest.memo = paymentIntent.memo;
        //sendRequest.exchangeRate = amountCalculatorLink.getExchangeRate();
        try {
            application.getWallet().completeTx(sendRequest);
        } catch(final InsufficientMoneyException x) {
            log.error("InsufficientMoneyException!");
        }

        //application.getWallet().commitTx(sendRequest.tx);

        setContentView(R.layout.tap_to_send_coins_content);

        payeeGroup = (View)findViewById(R.id.tap_to_send_coins_payee_group);
        payeeNameView = (TextView)findViewById(R.id.tap_to_send_coins_payee_name);

        if (paymentIntent.hasPayee()) {
            payeeGroup.setVisibility(View.VISIBLE);
            payeeNameView.setVisibility(View.VISIBLE);
            payeeNameView.setText(paymentIntent.payeeName);
        } else {
            payeeGroup.setVisibility(View.INVISIBLE);
        }

        memoGroup = (View)findViewById(R.id.tap_to_send_coins_memo_group);
        memoView = (TextView)findViewById(R.id.tap_to_send_coins_memo);

        if (paymentIntent.memo != null) {
            memoGroup.setVisibility(View.VISIBLE);
            memoView.setVisibility(View.VISIBLE);
            memoView.setText(paymentIntent.memo);
            /*memoView.setText(sendRequest.tx.getFee().toFriendlyString());*/
        } else {
            memoGroup.setVisibility(View.INVISIBLE);
        }

        final CurrencyAmountView btcAmountView = (CurrencyAmountView)findViewById(R.id.tap_to_send_coins_amount_btc);
        btcAmountView.setCurrencySymbol(config.getFormat().code());
        btcAmountView.setInputFormat(config.getMaxPrecisionFormat());
        btcAmountView.setHintFormat(config.getFormat());

        final CurrencyAmountView localAmountView = (CurrencyAmountView)findViewById(R.id.tap_to_send_coins_amount_local);
        localAmountView.setInputFormat(Constants.LOCAL_FORMAT);
        localAmountView.setHintFormat(Constants.LOCAL_FORMAT);
        amountCalculatorLink = new CurrencyCalculatorLink(btcAmountView, localAmountView);
        amountCalculatorLink.setBtcAmount(paymentIntent.getAmount());
        amountCalculatorLink.setEnabled(false);
        amountCalculatorLink.setExchangeDirection(true);

        final CurrencyAmountView btcFeesView = (CurrencyAmountView)findViewById(R.id.tap_to_send_coins_fees_btc);
        btcFeesView.setCurrencySymbol(config.getFormat().code());
        btcFeesView.setInputFormat(config.getMaxPrecisionFormat());
        btcFeesView.setHintFormat(config.getFormat());

        final CurrencyAmountView localFeesView = (CurrencyAmountView)findViewById(R.id.tap_to_send_coins_fees_local);
        localFeesView.setInputFormat(Constants.LOCAL_FORMAT);
        localFeesView.setHintFormat(Constants.LOCAL_FORMAT);
        feesCalculatorLink = new CurrencyCalculatorLink(btcFeesView, localFeesView);
        feesCalculatorLink.setBtcAmount(sendRequest.tx.getFee());
        feesCalculatorLink.setEnabled(false);
        feesCalculatorLink.setExchangeDirection(true);

        loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);

        cancelButton = (Button)findViewById(R.id.tap_to_send_bitcoins_cancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                handleCancel();
            }
        });

        final Intent broadcastTransaction = new Intent(CardService.ACTION_BROADCAST_TRANSACTION);
        broadcastTransaction.setPackage(getPackageName());
        broadcastTransaction.putExtra(CardService.ACTION_BROADCAST_TRANSACTION_TX, sendRequest.tx.bitcoinSerialize());
        startService(broadcastTransaction);
        //LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastTransaction);

        Vibrator v = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(500);
    }

    private void handleCancel() {
        final Intent broadcastTransaction = new Intent(CardService.ACTION_BROADCAST_TRANSACTION);
        broadcastTransaction.setPackage(getPackageName());
        log.info("handleCancel");
        stopService(broadcastTransaction);
        finish();
    }

    private final LoaderManager.LoaderCallbacks<Cursor> rateLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {
        @Override
        public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
            return new ExchangeRatesLoader(activity, config);
        }

        @Override
        public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
            if (data != null && data.getCount() > 0) {
                data.moveToFirst();
                final ExchangeRate exchangeRate = ExchangeRatesProvider.getExchangeRate(data);

                amountCalculatorLink.setExchangeRate(exchangeRate.rate);
                feesCalculatorLink.setExchangeRate(exchangeRate.rate);
            }
        }

        @Override
        public void onLoaderReset(final Loader<Cursor> loader) {
        }
    };
}
