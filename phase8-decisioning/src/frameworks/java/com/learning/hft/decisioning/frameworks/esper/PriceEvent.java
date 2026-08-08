package com.learning.hft.decisioning.frameworks.esper;

/** A market-data event fed into the Esper CEP engine. JavaBean getters so Esper can read properties. */
public class PriceEvent {
    private final String symbol;
    private final double price;

    public PriceEvent(String symbol, double price) {
        this.symbol = symbol;
        this.price = price;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getPrice() {
        return price;
    }
}
