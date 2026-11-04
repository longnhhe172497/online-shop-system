package com.osmshop.validation;

/** Shared validation expressions for user-entered identity data. */
public final class UserInputRules {
    private UserInputRules() {}

    public static final String PERSON_NAME = "^ *[\\p{L}\\p{M}]+(?: +[\\p{L}\\p{M}]+)* *$";
    public static final String REQUIRED_PHONE = "^[0-9]{10}$";
    public static final String OPTIONAL_PHONE = "^$|^[0-9]{10}$";
}
