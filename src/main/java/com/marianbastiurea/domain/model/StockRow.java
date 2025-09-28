package com.marianbastiurea.domain.model;


public record StockRow(long version, int finalStock) {
    public StockRow withFinal(int newFinal) {
        return new StockRow(version, newFinal);
    }

    public StockRow bumpVersion() {
        return new StockRow(version + 1, finalStock);
    }
}
