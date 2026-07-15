package org.gnucash.android.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;

/**
 * Parses amounts as String into BigDecimal.
 */
public class AmountParser {
    /**
     * Parses {@code amount} and returns it as a BigDecimal.
     *
     * @param amount String with the amount to parse.
     * @return The amount parsed as a BigDecimal.
     * @throws ParseException if the full string couldn't be parsed as an amount.
     */
    public static BigDecimal parse(String amount) throws ParseException {
        DecimalFormat formatter = (DecimalFormat) NumberFormat.getNumberInstance();
        formatter.setParseBigDecimal(true);
        ParsePosition parsePosition = new ParsePosition(0);
        BigDecimal parsedAmount = (BigDecimal) formatter.parse(amount, parsePosition);

        // Ensure any mistyping by the user is caught instead of partially parsed
        if ((parsedAmount == null) || (parsePosition.getIndex() < amount.length()))
            throw new ParseException("Parse error", parsePosition.getErrorIndex());

        return parsedAmount;
    }

    /**
     * Formats {@code amount} for display in an amount input field using the default locale.
     *
     * <p>Grouping separators are disabled so the result can be re-parsed by
     * {@link #parse(String)}. Stored amounts are locale-independent plain decimals, so this
     * must be used instead of the raw stored string when populating an editable field.</p>
     *
     * @param amount         Amount to format.
     * @param fractionDigits Maximum number of fraction digits to show.
     * @return The amount formatted with the default-locale decimal separator.
     */
    public static String format(BigDecimal amount, int fractionDigits) {
        DecimalFormat formatter = (DecimalFormat) NumberFormat.getNumberInstance();
        formatter.setGroupingUsed(false);
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(fractionDigits);
        return formatter.format(amount);
    }
}