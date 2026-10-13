package com.example.kiwi_backend.vote.domain;
public enum ResultProduct {
    FIRST_CHOOSERS(1, false),
    MY_FIRST_CHOICE_RANK(1, false),
    USER_RANKINGS(2, true),
    BUNDLE(3, true),
    ALL_RANKINGS(10, false);
    private final int gems;
    private final boolean targetRequired;
    ResultProduct(int gems, boolean targetRequired) { this.gems = gems; this.targetRequired = targetRequired; }
    public int gems() { return gems; }
    public boolean targetRequired() { return targetRequired; }
}
