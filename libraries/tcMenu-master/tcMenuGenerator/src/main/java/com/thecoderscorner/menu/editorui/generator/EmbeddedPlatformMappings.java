/*
 * Copyright (c) 2018 https://www.thecoderscorner.com (Nutricherry LTD).
 * This product is licensed under an Apache license, see the LICENSE file in the top-level directory.
 */

package com.thecoderscorner.menu.editorui.generator;


import java.util.Set;

public interface EmbeddedPlatformMappings {
    Set<EmbeddedPlatform> ALL_ARDUINO_BOARDS = Set.of(EmbeddedPlatform.ARDUINO);
    Set<EmbeddedPlatform> ALL_DEVICES = Set.of(EmbeddedPlatform.ARDUINO);
}
