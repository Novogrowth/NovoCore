package gr.novotrade.novocore.core.web.json;

import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.math.BigDecimal;
import java.util.Currency;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * How {@link Money}, {@link Quantity} and {@link UnitCost} cross the wire.
 *
 * <h2>Why this exists: JSON has no decimal type</h2>
 *
 * <p>{@code CLAUDE.md} rule 5 is absolute — money is {@code BigDecimal}, never {@code double},
 * anywhere, ever. A JSON <em>number</em> literal breaks that rule at the one layer that talks to the
 * outside world: whatever the backend writes, {@code JSON.parse} in a browser produces an IEEE-754
 * double, and {@code 12.505} is not representable in one. Q45 is the standing proof that sub-cent
 * arithmetic in this domain is not hypothetical — a lot at {@code 12.505} left a permanent residue in
 * the Inventory control account, and the fix depends on that figure surviving exactly.
 *
 * <p>So <strong>every amount and every quantity is a JSON string</strong>:
 *
 * <pre>
 *   "sellingPrice": { "amount": "12.50",       "currency": "EUR" }
 *   "unitCost":     { "amount": "12.505000",   "currency": "EUR" }
 *   "quantity":     "3.000000"
 * </pre>
 *
 * <p>A string carries its own scale and survives any parser exactly. The alternative — integer minor
 * units — is equally exact and was rejected because quantities and unit costs are 6-decimal while
 * amounts are 2, so every field would need the reader to know which scale it was in.
 *
 * <h2>A JSON number is refused, not accepted and rounded</h2>
 *
 * <p>Reading is deliberately stricter than it has to be. A client sending {@code 12.505} as a number
 * has already lost the value before the request was made — the bytes on the wire may still say
 * {@code 12.505}, but the client's own variable did not, and the next value it computes will be
 * wrong. Accepting it would make the backend the last place the mistake could have been caught and
 * the one place it was not. So a number where a string is required is an error naming the rule,
 * which is the same stance as {@code VatClassPrecedence} throwing rather than assuming 24%.
 *
 * <h2>Currency is carried, not assumed</h2>
 *
 * <p>ADR 0005 models currency from day one although behaviour is EUR-only, and the schema pairs every
 * monetary column with its currency. Flattening {@code Money} to a bare amount here would make the
 * boundary the single place that quietly assumes EUR. A missing currency is refused rather than
 * defaulted.
 */
public final class NovoCoreJsonModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    private static final String AMOUNT = "amount";
    private static final String CURRENCY = "currency";

    public NovoCoreJsonModule() {
        super("novocore");
        addSerializer(Money.class, new MoneySerializer());
        addDeserializer(Money.class, new MoneyDeserializer());
        addSerializer(UnitCost.class, new UnitCostSerializer());
        addDeserializer(UnitCost.class, new UnitCostDeserializer());
        addSerializer(Quantity.class, new QuantitySerializer());
        addDeserializer(Quantity.class, new QuantityDeserializer());
    }

    // -------------------------------------------------------------------------------------------
    // Writing
    // -------------------------------------------------------------------------------------------

    private static void writeAmount(JsonGenerator gen, BigDecimal value, Currency currency) {
        gen.writeStartObject();
        // toPlainString, never toString: the latter can produce scientific notation for values with
        // a large exponent, which is a second representation of the same number for a reader to
        // have to handle.
        gen.writeStringProperty(AMOUNT, value.toPlainString());
        gen.writeStringProperty(CURRENCY, currency.getCurrencyCode());
        gen.writeEndObject();
    }

    static final class MoneySerializer extends ValueSerializer<Money> {
        @Override
        public void serialize(Money value, JsonGenerator gen, SerializationContext ctxt) {
            writeAmount(gen, value.amount(), value.currency());
        }
    }

    static final class UnitCostSerializer extends ValueSerializer<UnitCost> {
        @Override
        public void serialize(UnitCost value, JsonGenerator gen, SerializationContext ctxt) {
            // The same shape as Money on purpose. A reader that can display one can display the
            // other; only the scale differs, and the scale is in the string.
            writeAmount(gen, value.value(), value.currency());
        }
    }

    static final class QuantitySerializer extends ValueSerializer<Quantity> {
        @Override
        public void serialize(Quantity value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeString(value.value().toPlainString());
        }
    }

    // -------------------------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------------------------

    /**
     * Reads the {@code {amount, currency}} shape, refusing everything else.
     *
     * @return the amount and currency, never null; reports an input mismatch instead of returning
     */
    private record AmountAndCurrency(BigDecimal amount, Currency currency) {

        static AmountAndCurrency read(JsonParser parser, DeserializationContext ctxt, Class<?> type) {
            if (parser.currentToken() != JsonToken.START_OBJECT) {
                return ctxt.reportInputMismatch(type,
                        "expected an object of the form {\"amount\": \"12.50\", \"currency\": "
                                + "\"EUR\"}, but found %s. A bare number is refused: JSON numbers "
                                + "are parsed as IEEE-754 doubles by JavaScript clients and cannot "
                                + "carry an exact decimal amount.",
                        parser.currentToken());
            }

            JsonNode node = ctxt.readTree(parser);
            JsonNode amountNode = node.get(AMOUNT);
            JsonNode currencyNode = node.get(CURRENCY);

            if (amountNode == null || !amountNode.isString()) {
                return ctxt.reportInputMismatch(type,
                        "\"%s\" must be present and must be a JSON string, not a number — a "
                                + "number cannot carry an exact decimal amount. Found: %s",
                        AMOUNT, amountNode);
            }
            if (currencyNode == null || !currencyNode.isString()) {
                // No default. ADR 0005 models currency explicitly precisely so that no layer has to
                // assume one, and a boundary that assumed EUR would be exactly that layer.
                return ctxt.reportInputMismatch(type,
                        "\"%s\" must be present and must be an ISO 4217 code such as \"EUR\". It "
                                + "is not defaulted. Found: %s",
                        CURRENCY, currencyNode);
            }

            BigDecimal amount;
            try {
                amount = new BigDecimal(amountNode.stringValue().trim());
            } catch (NumberFormatException notANumber) {
                return ctxt.reportInputMismatch(type,
                        "\"%s\" is not a valid decimal number: \"%s\"",
                        AMOUNT, amountNode.stringValue());
            }

            Currency currency;
            try {
                currency = Currency.getInstance(currencyNode.stringValue().trim());
            } catch (IllegalArgumentException unknownCurrency) {
                return ctxt.reportInputMismatch(type,
                        "\"%s\" is not a known ISO 4217 currency code: \"%s\"",
                        CURRENCY, currencyNode.stringValue());
            }

            return new AmountAndCurrency(amount, currency);
        }
    }

    static final class MoneyDeserializer extends ValueDeserializer<Money> {
        @Override
        public Money deserialize(JsonParser parser, DeserializationContext ctxt) {
            AmountAndCurrency read = AmountAndCurrency.read(parser, ctxt, Money.class);
            // Money's own constructor refuses more than two decimals rather than rounding, which
            // is the behaviour that should apply to a request body too: a caller sending 12.505 as
            // a price has made a mistake worth naming, not one worth silently correcting.
            return Money.of(read.amount(), read.currency());
        }
    }

    static final class UnitCostDeserializer extends ValueDeserializer<UnitCost> {
        @Override
        public UnitCost deserialize(JsonParser parser, DeserializationContext ctxt) {
            AmountAndCurrency read = AmountAndCurrency.read(parser, ctxt, UnitCost.class);
            return UnitCost.of(read.amount(), read.currency());
        }
    }

    static final class QuantityDeserializer extends ValueDeserializer<Quantity> {
        @Override
        public Quantity deserialize(JsonParser parser, DeserializationContext ctxt) {
            if (parser.currentToken() != JsonToken.VALUE_STRING) {
                return ctxt.reportInputMismatch(Quantity.class,
                        "a quantity must be a JSON string such as \"3.000000\", not %s. Quantities "
                                + "carry six decimals and a JSON number cannot represent them "
                                + "exactly in a JavaScript client.",
                        parser.currentToken());
            }
            try {
                return Quantity.of(new BigDecimal(parser.getString().trim()));
            } catch (NumberFormatException notANumber) {
                return ctxt.reportInputMismatch(Quantity.class,
                        "not a valid decimal quantity: \"%s\"", parser.getString());
            }
        }
    }
}
