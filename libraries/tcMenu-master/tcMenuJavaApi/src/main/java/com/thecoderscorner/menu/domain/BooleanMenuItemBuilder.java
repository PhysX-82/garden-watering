/*
 * Copyright (c) 2018 https://www.thecoderscorner.com (Nutricherry LTD).
 * This product is licensed under an Apache license, see the LICENSE file in the top-level directory.
 */

package com.thecoderscorner.menu.domain;

/**
 * Constructs a BooleanMenuItemBuilder using the standard builder pattern. It is possible to either build
 * an item from scratch, or start with an existing item and make changes.
 */
public class BooleanMenuItemBuilder extends MenuItemBuilder<BooleanMenuItemBuilder> {

    private BooleanMenuItem.BooleanNaming naming = BooleanMenuItem.BooleanNaming.TRUE_FALSE;

    @Override
    public BooleanMenuItemBuilder getThis() {
        return this;
    }

    public BooleanMenuItemBuilder withExisting(BooleanMenuItem item) {
        baseFromExisting(item);
        this.naming = item.getNaming();
        return getThis();
    }

    public BooleanMenuItemBuilder withNaming(BooleanMenuItem.BooleanNaming naming) {
        this.naming = naming;
        return getThis();
    }

    public BooleanMenuItem menuItem() {
        return new BooleanMenuItem(this.name, this.id, this.eepromAddr, functionName, this.naming, readOnly);
    }

    public static BooleanMenuItemBuilder aBooleanMenuItemBuilder() {
        return new BooleanMenuItemBuilder();
    }

}
