package org.gnucash.android.test.unit.util;

import org.gnucash.android.util.AmountParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class AmountParserTest {
    private Locale mPreviousLocale;

    @Before
    public void setUp() throws Exception {
        mPreviousLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @After
    public void tearDown() throws Exception {
        Locale.setDefault(mPreviousLocale);
    }

    @Test
    public void testParseIntegerAmount() throws ParseException {
        assertThat(AmountParser.parse("123")).isEqualTo(new BigDecimal(123));
    }

    @Test
    public void parseDecimalAmount() throws ParseException {
        assertThat(AmountParser.parse("123.45")).isEqualTo(new BigDecimal("123.45"));
    }

    @Test
    public void parseDecimalAmountWithDifferentSeparator() throws ParseException {
        Locale.setDefault(Locale.GERMANY);
        assertThat(AmountParser.parse("123,45")).isEqualTo(new BigDecimal("123.45"));
    }

    @Test(expected = ParseException.class)
    public void withGarbageAtTheBeginning_shouldFailWithException() throws ParseException {
        AmountParser.parse("asdf123.45");
    }

    @Test(expected = ParseException.class)
    public void withGarbageAtTheEnd_shouldFailWithException() throws ParseException {
        AmountParser.parse("123.45asdf");
    }

    @Test(expected = ParseException.class)
    public void emptyString_shouldFailWithException() throws ParseException {
        AmountParser.parse("");
    }

    @Test
    public void parseStrict_acceptsLocaleDecimalSeparator() throws ParseException {
        assertThat(AmountParser.parseStrict("3.50")).isEqualByComparingTo(new BigDecimal("3.50"));

        Locale.setDefault(Locale.GERMANY);
        assertThat(AmountParser.parseStrict("3,50")).isEqualByComparingTo(new BigDecimal("3.50"));
    }

    @Test
    public void parseStrict_parsesDotAsDecimalInGermanLocale() throws ParseException {
        Locale.setDefault(Locale.GERMANY);
        // Two digits after the separator → decimal, not grouping
        assertThat(AmountParser.parseStrict("3.50")).isEqualByComparingTo(new BigDecimal("3.50"));
    }

    @Test
    public void parseStrict_parsesCommaAsDecimalInUsLocale() throws ParseException {
        assertThat(AmountParser.parseStrict("3,50")).isEqualByComparingTo(new BigDecimal("3.50"));
    }

    @Test
    public void parseStrict_acceptsGroupingSeparators() throws ParseException {
        assertThat(AmountParser.parseStrict("1,000")).isEqualByComparingTo(new BigDecimal("1000"));

        Locale.setDefault(Locale.GERMANY);
        assertThat(AmountParser.parseStrict("1.000")).isEqualByComparingTo(new BigDecimal("1000"));
    }

    @Test
    public void parseStrict_acceptsMixedSeparators() throws ParseException {
        assertThat(AmountParser.parseStrict("1,234.56")).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(AmountParser.parseStrict("1.234,56")).isEqualByComparingTo(new BigDecimal("1234.56"));
    }

    @Test(expected = ParseException.class)
    public void parseStrict_emptyString_shouldFailWithException() throws ParseException {
        AmountParser.parseStrict("");
    }

    @Test(expected = ParseException.class)
    public void parseStrict_whitespace_shouldFailWithException() throws ParseException {
        AmountParser.parseStrict("   ");
    }

    @Test(expected = ParseException.class)
    public void parseStrict_null_shouldFailWithException() throws ParseException {
        AmountParser.parseStrict(null);
    }

    @Test
    public void parseStrict_reparsesFormatOutput() throws ParseException {
        Locale.setDefault(Locale.GERMANY);
        String formatted = AmountParser.format(new BigDecimal("1234.50"), 2);
        assertThat(AmountParser.parseStrict(formatted)).isEqualByComparingTo(new BigDecimal("1234.50"));
    }

    @Test
    public void format_usesLocaleSeparator_andReparses() throws ParseException {
        Locale.setDefault(Locale.GERMANY);
        String formatted = AmountParser.format(new BigDecimal("3.50"), 2);
        assertThat(formatted).contains(",");
        assertThat(AmountParser.parse(formatted)).isEqualByComparingTo(new BigDecimal("3.50"));
    }

    @Test
    public void format_dropsGroupingSeparators() {
        Locale.setDefault(Locale.US);
        assertThat(AmountParser.format(new BigDecimal("1234.50"), 2)).isEqualTo("1234.5");
    }
}