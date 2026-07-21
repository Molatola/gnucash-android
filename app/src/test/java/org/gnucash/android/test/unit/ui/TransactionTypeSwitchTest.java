package org.gnucash.android.test.unit.ui;

import android.widget.CompoundButton;
import android.widget.TextView;

import org.gnucash.android.test.unit.testutil.ShadowCrashlytics;
import org.gnucash.android.test.unit.testutil.ShadowUserVoice;
import org.gnucash.android.ui.util.widget.CalculatorEditText;
import org.gnucash.android.ui.util.widget.TransactionTypeSwitch;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link TransactionTypeSwitch} always dispatches to listeners
 * registered via {@link TransactionTypeSwitch#addOnCheckedChangeListener},
 * even when {@code setAmountFormattingListener} was never called.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21, packageName = "org.gnucash.android", shadows = {ShadowCrashlytics.class, ShadowUserVoice.class})
public class TransactionTypeSwitchTest {

    private TransactionTypeSwitch mSwitch;

    @Before
    public void setUp() {
        mSwitch = new TransactionTypeSwitch(RuntimeEnvironment.application);
    }

    @Test
    public void addOnCheckedChangeListener_firesWithoutAmountFormattingListener() {
        final AtomicInteger calls = new AtomicInteger();
        mSwitch.addOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                calls.incrementAndGet();
            }
        });

        boolean initial = mSwitch.isChecked();
        mSwitch.setChecked(!initial);

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    public void setOnCheckedChangeListener_doesNotWipeDispatcherAndReplacesExternal() {
        final AtomicInteger viaSetFirst = new AtomicInteger();
        final AtomicInteger viaSetSecond = new AtomicInteger();
        final AtomicInteger viaAdd = new AtomicInteger();

        mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                viaSetFirst.incrementAndGet();
            }
        });
        mSwitch.addOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                viaAdd.incrementAndGet();
            }
        });
        mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                viaSetSecond.incrementAndGet();
            }
        });

        mSwitch.setChecked(!mSwitch.isChecked());

        assertThat(viaSetFirst.get()).isEqualTo(0);
        assertThat(viaSetSecond.get()).isEqualTo(1);
        assertThat(viaAdd.get()).isEqualTo(1);
    }

    @Test
    public void setOnCheckedChangeListener_null_clearsExternalListener() {
        final AtomicInteger viaSet = new AtomicInteger();
        final AtomicInteger viaAdd = new AtomicInteger();

        mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                viaSet.incrementAndGet();
            }
        });
        mSwitch.addOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                viaAdd.incrementAndGet();
            }
        });

        mSwitch.setOnCheckedChangeListener(null);
        mSwitch.setChecked(!mSwitch.isChecked());

        assertThat(viaSet.get()).isEqualTo(0);
        assertThat(viaAdd.get()).isEqualTo(1);
    }

    @Test
    public void setAmountFormattingListener_replacesPriorFormattingListener() {
        CalculatorEditText amount1 = new CalculatorEditText(RuntimeEnvironment.application);
        CalculatorEditText amount2 = new CalculatorEditText(RuntimeEnvironment.application);
        TextView currency = new TextView(RuntimeEnvironment.application);
        BigDecimal ten = new BigDecimal("10");

        amount1.setValue(ten);
        amount2.setValue(ten);

        mSwitch.setAmountFormattingListener(amount1, currency);
        mSwitch.setAmountFormattingListener(amount2, currency);

        // Start unchecked with a positive amount; checking should negate only the active field.
        mSwitch.setChecked(false);
        mSwitch.setChecked(true);

        assertThat(amount1.getValue()).isEqualByComparingTo(ten);
        assertThat(amount2.getValue()).isEqualByComparingTo(ten.negate());
    }
}
