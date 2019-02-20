/*
 * Copyright (c) 2018 https://www.thecoderscorner.com (Nutricherry LTD).
 * This product is licensed under an Apache license, see the LICENSE file in the top-level directory.
 */

package com.thecoderscorner.menu.domain;

import com.thecoderscorner.menu.domain.state.BooleanMenuState;
import com.thecoderscorner.menu.domain.state.MenuState;
import com.thecoderscorner.menu.domain.util.MenuItemVisitor;

import java.util.Objects;

/**
 * SubMenuItem represents a menu item that has children. To get the child items call the MenuTree
 * methods that interact with items.
 */
public class SubMenuItem extends MenuItem<Boolean> {

    public SubMenuItem() {
        super("", -1, -1, null, false);
        // needed for serialisation
    }

    public SubMenuItem(String name, int id, int eepromAddr) {
        super(name, id, eepromAddr, null, false);
    }

    /**
     * SubMenuItems always have child items, so they always return true
     * @return
     */
    @Override
    public boolean hasChildren() {
        return true;
    }

    @Override
    public MenuState<Boolean> newMenuState(Boolean value, boolean changed, boolean active) {
        return new BooleanMenuState(changed, active, value);
    }

    @Override
    public void accept(MenuItemVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SubMenuItem that = (SubMenuItem) o;
        return getId() == that.getId() &&
                getEepromAddress() == that.getEepromAddress() &&
                isReadOnly() == that.isReadOnly() &&
                Objects.equals(getName(), that.getName()) &&
                Objects.equals(getFunctionName(), that.getFunctionName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getId(), getEepromAddress(), getFunctionName(), isReadOnly());
    }
}
