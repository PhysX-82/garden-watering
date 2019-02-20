/*
 * Copyright (c) 2018 https://www.thecoderscorner.com (Nutricherry LTD).
 * This product is licensed under an Apache license, see the LICENSE file in the top-level directory.
 */

package com.thecoderscorner.menu.editorui.generator;

import java.util.List;

public interface EmbeddedCodeCreator {
    List<String> getIncludes();
    String getGlobalVariables();
    String getExportDefinitions();
    String getSetupCode(String rootItem);
    List<String> getRequiredFiles();
    List<CreatorProperty> properties();
}
