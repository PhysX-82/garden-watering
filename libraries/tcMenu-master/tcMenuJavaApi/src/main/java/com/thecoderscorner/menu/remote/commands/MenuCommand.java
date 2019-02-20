/*
 * Copyright (c) 2018 https://www.thecoderscorner.com (Nutricherry LTD).
 * This product is licensed under an Apache license, see the LICENSE file in the top-level directory.
 */

package com.thecoderscorner.menu.remote.commands;

/**
 * Classes extending from MenuCommand can be sent and received on a connector. They are protocol
 * neutral so as to make replacing the protocol as easy as possible.
 */
public interface MenuCommand {
    /**
     * The type of message received.
     * @return the command type
     */
    MenuCommandType getCommandType();
}
