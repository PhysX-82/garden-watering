/*
 * Copyright (c) 2018 https://www.thecoderscorner.com (Nutricherry LTD).
 * This product is licensed under an Apache license, see the LICENSE file in the top-level directory.
 */

package com.thecoderscorner.menu.editorui;

import com.thecoderscorner.menu.editorui.controller.MenuEditorController;
import com.thecoderscorner.menu.editorui.generator.arduino.ArduinoLibraryInstaller;
import com.thecoderscorner.menu.editorui.project.CurrentEditorProject;
import com.thecoderscorner.menu.editorui.project.FileBasedProjectPersistor;
import com.thecoderscorner.menu.editorui.uimodel.CurrentProjectEditorUIImpl;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/**
 * The application starting point for the JavaFX version of the application
 */
public class MenuEditorApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Embedded Menu Designer loading");
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/menuEditor.fxml"));
        Pane myPane = loader.load();

        MenuEditorController controller = loader.getController();

        CurrentProjectEditorUIImpl editorUI = new CurrentProjectEditorUIImpl(primaryStage);

        FileBasedProjectPersistor persistor = new FileBasedProjectPersistor();

        CurrentEditorProject project = new CurrentEditorProject(editorUI, persistor);

        controller.initialise(project, new ArduinoLibraryInstaller(), editorUI);

        Scene myScene = new Scene(myPane);
        primaryStage.setScene(myScene);
        primaryStage.show();

        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/img/menu-icon.png")));

        primaryStage.setOnCloseRequest((evt)-> {
            controller.persistPreferences();
            if(project.isDirty()) {
                evt.consume();
                Alert alert = new Alert(AlertType.CONFIRMATION);
                alert.setTitle("Are you sure");
                alert.setHeaderText("There are unsaved changes, continue with exit anyway?");
                if(alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                    Platform.exit();
                }
            }
        });
    }
}
