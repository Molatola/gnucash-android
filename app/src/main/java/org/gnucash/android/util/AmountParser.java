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
        return parse(amount, true);
    }

    public static BigDecimal parseStrict(String amount) throws ParseException {
        try {
            String originalAmount = amount;
            amount = amount.trim();
            if (amount.isEmpty()) {
                throw new ParseException("Empty amount", 0);
            }

            int dots = 0;
            int commas = 0;
            int lastDotIndex = -1;
            int lastCommaIndex = -1;
            for (int i = 0; i < amount.length(); i++) {
                char c = amount.charAt(i);
                if (c == '.') { dots++; lastDotIndex = i; }
                else if (c == ',') { commas++; lastCommaIndex = i; }
            }

            if (dots > 0 && commas > 0) {
                if (lastDotIndex > lastCommaIndex) {
                    return parseStrictFormat(amount, ",", "\\.");
                } else {
                    return parseStrictFormat(amount, "\\.", ",");
                }
            } else if (dots > 0 || commas > 0) {
                char punct = dots > 0 ? '.' : ',';
                int count = dots > 0 ? dots : commas;
                int lastIndex = dots > 0 ? lastDotIndex : lastCommaIndex;

                if (count > 1) {
                    int digitsAfter = amount.length() - 1 - lastIndex;
                    if (digitsAfter == 3) {
                        return parseStrictFormat(amount, punct == '.' ? "\\." : ",", "");
                    } else {
                        throw new ParseException("Invalid format for amount: " + originalAmount, lastIndex);
                    }
                } else {
                    int digitsAfter = amount.length() - 1 - lastIndex;
                    if (digitsAfter == 3) {
                        DecimalFormat formatter = (DecimalFormat) NumberFormat.getNumberInstance();
                        char localeDecimal = formatter.getDecimalFormatSymbols().getDecimalSeparator();
                        if (punct == localeDecimal) {
                            amount = amount.replace(punct, '.');
                            return new BigDecimal(amount);
                        } else {
                            amount = amount.replace(String.valueOf(punct), "");
                            return new BigDecimal(amount);
                        }
                    } else {
                        amount = amount.replace(punct, '.');
                        return new BigDecimal(amount);
                    }
                }
            } else {
                return new BigDecimal(amount);
            }
        } catch (NumberFormatException e) {
            throw new ParseException("Invalid amount", 0);
        }
    }

    private static BigDecimal parseStrictFormat(String amount, String g, String d) throws ParseException {
        String regex = "^-?(?:\\d{1,3}(?:" + g + "\\d{2,3})*" + g + "\\d{3}|\\d+)(?:" + d + "\\d+)?$";
        if (!amount.matches(regex)) {
            throw new ParseException("Invalid format for amount: " + amount, 0);
        }
        String clean = amount.replaceAll(g, "");
        if (!d.isEmpty()) {
            clean = clean.replace(d.charAt(d.length() - 1), '.');
        }
        return new BigDecimal(clean);
    }


    private static BigDecimal parse(String amount, boolean groupingUsed) throws ParseException {
        DecimalFormat formatter = (DecimalFormat) NumberFormat.getNumberInstance();
        formatter.setParseBigDecimal(true);
        formatter.setGroupingUsed(groupingUsed);
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