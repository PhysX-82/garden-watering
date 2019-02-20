/*
 * Copyright (c) 2018 https://www.thecoderscorner.com (Nutricherry LTD).
 * This product is licensed under an Apache license, see the LICENSE file in the top-level directory.
 */

package com.thecoderscorner.menu.editorui.generator.input;

import com.thecoderscorner.menu.editorui.generator.AbstractCodeCreator;
import com.thecoderscorner.menu.editorui.generator.CreatorProperty;

import java.util.Collections;
import java.util.List;

import static com.thecoderscorner.menu.editorui.generator.CreatorProperty.SubSystem.INPUT;
import static com.thecoderscorner.menu.editorui.generator.arduino.ArduinoItemGenerator.LINE_BREAK;

public class UpDownOkInputCreator extends AbstractCodeCreator {

    private final List<CreatorProperty> creatorProperties = List.of(
            new CreatorProperty("PULLUP_LOGIC", "Use Pull Up switch logic (true/false)", "true", INPUT, CreatorProperty.PropType.TEXTUAL),
            new CreatorProperty("INTERRUPT_SWITCHES", "Use interrupts for switches (true/false)", "false", INPUT, CreatorProperty.PropType.TEXTUAL),
            new CreatorProperty("SWITCH_IODEVICE", "Optional: IoAbstractionRef, default is Arduino pins", "", INPUT, CreatorProperty.PropType.TEXTUAL),
            new CreatorProperty("ENCODER_UP_PIN", "Up button pin connector", "0", INPUT),
            new CreatorProperty("ENCODER_DOWN_PIN", "Down button pin connector", "0", INPUT),
            new CreatorProperty("ENCODER_OK_PIN", "OK button pin connector", "0", INPUT)
    );

    @Override
    public String getSetupCode(String rootItem) {

        boolean pullUp = getBooleanFromProperty("PULLUP_LOGIC");
        boolean intSwitch = getBooleanFromProperty("INTERRUPT_SWITCHES");
        String ioDevice = findPropertyValue("SWITCH_IODEVICE").getLatestValue();
        if(ioDevice == null || ioDevice.isEmpty()) {
            ioDevice = "ioUsingArduino()";
        }

        StringBuilder sb = new  StringBuilder(256);
        return sb.append("    switches.").append(intSwitch ? "initialiseInterrupt" : "initialise")
                 .append("(").append(ioDevice).append(", ").append(pullUp).append(");\n")
                 .append("    menuMgr.initForUpDownOk(&renderer, &")
                 .append(rootItem)
                 .append(", ENCODER_UP_PIN, ENCODER_DOWN_PIN, ENCODER_OK_PIN);")
                 .toString();
    }

    @Override
    public List<String> getIncludes() {
        return Collections.emptyList();
    }

    @Override
    public String getGlobalVariables() {
        return "";
    }

    @Override
    public String getExportDefinitions() {
        String additionalExports = "";
        String expVar = findPropertyValue("SWITCH_IODEVICE").getLatestValue();
        if(expVar != null && !expVar.isEmpty()) {
            additionalExports = "extern IoAbstractionRef " + expVar + ";" + LINE_BREAK;
        }
        return additionalExports + super.getExportDefinitions();
    }

    @Override
    public List<CreatorProperty> properties() {
        return creatorProperties;
    }
}
