/*
 * Copyright (c) 2018 https://www.thecoderscorner.com (Nutricherry LTD).
 * This product is licensed under an Apache license, see the LICENSE file in the top-level directory.
 */

package com.thecoderscorner.menu.editorui.controller;

import com.thecoderscorner.menu.domain.MenuItem;
import com.thecoderscorner.menu.domain.SubMenuItem;
import com.thecoderscorner.menu.domain.state.MenuTree;
import com.thecoderscorner.menu.domain.util.MenuItemHelper;
import com.thecoderscorner.menu.editorui.dialog.AppInformationPanel;
import com.thecoderscorner.menu.editorui.dialog.RegistrationDialog;
import com.thecoderscorner.menu.editorui.generator.arduino.ArduinoLibraryInstaller;
import com.thecoderscorner.menu.editorui.project.CurrentEditorProject;
import com.thecoderscorner.menu.editorui.project.MenuIdChooser;
import com.thecoderscorner.menu.editorui.project.MenuIdChooserImpl;
import com.thecoderscorner.menu.editorui.project.MenuItemChange.Command;
import com.thecoderscorner.menu.editorui.uimodel.CurrentProjectEditorUI;
import com.thecoderscorner.menu.editorui.uimodel.UIMenuItem;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.control.Button;
import javafx.scene.control.MenuBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import static com.thecoderscorner.menu.editorui.dialog.AppInformationPanel.LIBRARY_DOCS_URL;
import static java.lang.System.Logger.Level.ERROR;

public class MenuEditorController {
    public static final String RECENT_DEFAULT = "Recent";
    public static final String REGISTERED_KEY = "Registered";
    private final System.Logger logger = System.getLogger(MenuEditorController.class.getSimpleName());
    private CurrentEditorProject editorProject;
    public javafx.scene.control.MenuItem menuCut;
    public javafx.scene.control.MenuItem menuCopy;
    public javafx.scene.control.MenuItem menuPaste;
    public javafx.scene.control.MenuItem menuUndo;
    public javafx.scene.control.MenuItem menuRedo;
    public javafx.scene.control.MenuItem menuRecent1;
    public javafx.scene.control.MenuItem menuRecent2;
    public javafx.scene.control.MenuItem menuRecent3;
    public javafx.scene.control.MenuItem menuRecent4;
    public javafx.scene.control.MenuItem exitMenuItem;
    public javafx.scene.control.MenuItem aboutMenuItem;

    public TextArea prototypeTextArea;
    public BorderPane rootPane;
    public TreeView<MenuItem> menuTree;
    public Button menuTreeAdd;
    public Button menuTreeRemove;
    public Button menuTreeCopy;
    public Button menuTreeUp;
    public Button menuTreeDown;
    public BorderPane editorBorderPane;
    public MenuBar mainMenu;

    private List<Button> toolButtons;
    private Optional<UIMenuItem> currentEditor = Optional.empty();
    private ArduinoLibraryInstaller installer;
    private CurrentProjectEditorUI editorUI;

    public void initialise(CurrentEditorProject editorProject, ArduinoLibraryInstaller installer,
                           CurrentProjectEditorUI editorUI) {
        this.editorProject = editorProject;
        this.installer = installer;
        this.editorUI = editorUI;

        menuTree.getSelectionModel().selectedItemProperty().addListener((observable, oldItem, newItem) -> {
            if (newItem != null) {
                onTreeChangeSelection(newItem.getValue());
            }
        });

        loadPreferences();

        Platform.runLater(() -> {
            sortOutToolButtons();
            sortOutMenuForMac();
            redrawTreeControl();
        });
    }

    private void sortOutMenuForMac() {
        final String os = System.getProperty ("os.name");
        if (os != null && os.startsWith ("Mac")) {
            mainMenu.useSystemMenuBarProperty().set(true);
            try {
                if(OSXAdapter.setAboutHandler(this, getClass().getMethod("onAboutOsX"))) {
                    aboutMenuItem.setVisible(false);
                }

                OSXAdapter.setQuitHandler(this, getClass().getMethod("onExitOsX"));
                exitMenuItem.setVisible(false);
            } catch (NoSuchMethodException e) {
                logger.log(ERROR, "Unable to set Mac menu properly", e);
            }
        }
    }

    public void onExitOsX() {
        getStage().fireEvent(new WindowEvent(getStage(), WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    public void onAboutOsX() {
        aboutMenuPressed(null);
    }

    private void sortOutToolButtons() {
        toolButtons = Arrays.asList(menuTreeAdd, menuTreeRemove, menuTreeCopy, menuTreeUp, menuTreeDown);

        toolButtons.forEach(button -> button.getStyleClass().setAll("tool-button"));
    }

    private void onEditorChange(MenuItem original, MenuItem changed) {
        if (!original.equals(changed)) {
            menuTree.getSelectionModel().getSelectedItem().setValue(changed);
            editorProject.applyCommand(Command.EDIT, changed);
            redrawPrototype();
        }
    }

    public void onTreeChangeSelection(MenuItem newValue) {
        editorUI.createPanelForMenuItem(newValue, editorProject.getMenuTree(), this::onEditorChange)
                .ifPresentOrElse((uiMenuItem) -> {
                    editorBorderPane.setCenter(uiMenuItem.initPanel());
                    currentEditor = Optional.of(uiMenuItem);
                },
                () -> {
                    AppInformationPanel panel = new AppInformationPanel(installer, this);
                    editorBorderPane.setCenter(panel.showEmptyInfoPanel());
                    currentEditor = Optional.empty();
                }
        );

        // we cannot modify root.
        toolButtons.stream().filter(b -> b != menuTreeAdd)
                .forEach(b -> b.setDisable(MenuTree.ROOT.equals(newValue)));

        // We cannot copy sub menus whole. Only value items
        if(newValue.hasChildren()) menuTreeCopy.setDisable(true);
    }

    private void redrawTreeControl() {
        TreeItem<MenuItem> selectedItem = menuTree.getSelectionModel().getSelectedItem();
        int sel = MenuTree.ROOT.getId();
        if (selectedItem != null && selectedItem.getValue() != null) {
            sel = selectedItem.getValue().getId();
        }

        TreeItem<MenuItem> rootItem = new TreeItem<>(MenuTree.ROOT);
        rootItem.setExpanded(true);

        SubMenuItem root = MenuTree.ROOT;
        recurseTreeItems(editorProject.getMenuTree().getMenuItems(root), rootItem);
        menuTree.setRoot(rootItem);
        menuTree.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        menuTree.getSelectionModel().selectFirst();

        redrawPrototype();
        selectChildInTreeById(rootItem, sel);
    }

    private void selectChildInTreeById(TreeItem<MenuItem> item, int id) {
        // if we had a selection before the rebuild, honour it
        for (TreeItem<MenuItem> child : item.getChildren()) {
            if (child.getValue().getId() == id) {
                menuTree.getSelectionModel().select(child);
                return;
            } else if (!child.getChildren().isEmpty()) {
                selectChildInTreeById(child, id);
            }
        }
    }

    private void redrawPrototype() {
        prototypeTextArea.setText(new TextTreeItemRenderer(editorProject.getMenuTree()).getTreeAsText());
    }

    private void recurseTreeItems(List<MenuItem> menuItems, TreeItem<MenuItem> treeItem) {
        if (menuItems == null) return;

        for (MenuItem<?> item : menuItems) {
            TreeItem<MenuItem> child = new TreeItem<>(item);
            if (item.hasChildren()) {
                child.setExpanded(true);
                recurseTreeItems(editorProject.getMenuTree().getMenuItems(item), child);
            }
            treeItem.getChildren().add(child);
        }
    }

    public void aboutMenuPressed(ActionEvent actionEvent) {
        editorUI.showAboutDialog(installer);
    }

    public void onMenuDocumentation(ActionEvent actionEvent) {
        try {
            Desktop.getDesktop().browse(new URI(LIBRARY_DOCS_URL));
        } catch (IOException | URISyntaxException e) {
            // not much we can do here really!
            logger.log(ERROR, "Could not open browser", e);
        }
    }

    public void registerMenuPressed(ActionEvent actionEvent) {
        RegistrationDialog.showRegistration(getStage());
    }

    public void onTreeCopy(ActionEvent actionEvent) {
        MenuItem selected = menuTree.getSelectionModel().getSelectedItem().getValue();
        MenuIdChooser chooser = new MenuIdChooserImpl(editorProject.getMenuTree());
        MenuItem item = MenuItemHelper.createFromExistingWithId(selected, chooser.nextHighestId());
        SubMenuItem subMenu = getSelectedSubMenu();
        editorProject.applyCommand(Command.NEW, item, subMenu);

        // select the newly created item and render it.
        redrawTreeControl();
        selectChildInTreeById(menuTree.getRoot(), item.getId());
    }

    public void onTreeMoveUp(ActionEvent event) {
        MenuItem selected = menuTree.getSelectionModel().getSelectedItem().getValue();
        editorProject.applyCommand(Command.UP, selected);
        redrawTreeControl();
    }

    public void onTreeMoveDown(ActionEvent event) {
        MenuItem selected = menuTree.getSelectionModel().getSelectedItem().getValue();
        editorProject.applyCommand(Command.DOWN, selected);
        redrawTreeControl();
    }

    public void onAddToTreeMenu(ActionEvent actionEvent) {
        SubMenuItem subMenu = getSelectedSubMenu();

        Optional<MenuItem> maybeItem = editorUI.showNewItemDialog(editorProject.getMenuTree());
        maybeItem.ifPresent((menuItem) -> {
            editorProject.applyCommand(Command.NEW, menuItem, subMenu);
            redrawTreeControl();
            selectChildInTreeById(menuTree.getRoot(), menuItem.getId());
        });

    }

    private SubMenuItem getSelectedSubMenu() {
        MenuItem selMenu = menuTree.getSelectionModel().getSelectedItem().getValue();
        if (!selMenu.hasChildren()) {
            selMenu = editorProject.getMenuTree().findParent(selMenu);
        }
        return MenuItemHelper.asSubMenu(selMenu);
    }

    private Stage getStage() {
        return (Stage) rootPane.getScene().getWindow();
    }

    public void onRemoveTreeMenu(ActionEvent actionEvent) {
        MenuItem toRemove = menuTree.getSelectionModel().getSelectedItem().getValue();

        if (toRemove.equals(MenuTree.ROOT)) {
            return; // cannot remove root.
        }

        // if there are children, confirm before removing.
        if (toRemove.hasChildren()) {
            if(!editorUI.questionYesNo("Remove ALL items within [" + toRemove.getName() + "]?",
                    "If you click yes and proceed, you will remove all items under " + toRemove.getName())) {
                return;
            }
        }

        editorProject.applyCommand(Command.REMOVE, toRemove);
        redrawTreeControl();
    }

    public void onFileNew(ActionEvent event) {
        editorProject.newProject();
        redrawTreeControl();
        handleRecents();
    }

    public void onFileOpen(ActionEvent event) {
        if (editorProject.openProject()) {
            redrawTreeControl();
            handleRecents();
        }
    }

    public void onRecent(ActionEvent event) {
        javafx.scene.control.MenuItem item = (javafx.scene.control.MenuItem) event.getSource();
        String recent = item.getText();
        if (!RECENT_DEFAULT.equals(recent)) {
            editorProject.openProject(recent);
            redrawTreeControl();
        }
    }

    public void onFileSave(ActionEvent event) {
        editorProject.saveProject(CurrentEditorProject.EditorSaveMode.SAVE);
        redrawTreeControl();
        handleRecents();
    }

    public void onFileSaveAs(ActionEvent event) {
        editorProject.saveProject(CurrentEditorProject.EditorSaveMode.SAVE_AS);
        redrawTreeControl();
        handleRecents();
    }

    public void onFileExit(ActionEvent event) {
        getStage().fireEvent(new WindowEvent(getStage(), WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    public void onCodeShowLayout(ActionEvent actionEvent) {
        editorUI.showRomLayoutDialog(editorProject.getMenuTree());
    }

    public void onGenerateCode(ActionEvent event) {
        editorUI.showCodeGeneratorDialog(editorProject, installer);
    }

    public void loadPreferences() {
        Preferences prefs = Preferences.userNodeForPackage(getClass());

        menuRecent1.setText(prefs.get(RECENT_DEFAULT + "1", RECENT_DEFAULT));
        menuRecent2.setText(prefs.get(RECENT_DEFAULT + "2", RECENT_DEFAULT));
        menuRecent3.setText(prefs.get(RECENT_DEFAULT + "3", RECENT_DEFAULT));
        menuRecent4.setText(prefs.get(RECENT_DEFAULT + "4", RECENT_DEFAULT));

        if(prefs.get(REGISTERED_KEY, "").isEmpty()) {
            Platform.runLater(()-> {
                RegistrationDialog.showRegistration(getStage());
                TreeItem<MenuItem> item = menuTree.getSelectionModel().getSelectedItem();
                if(item != null) {
                    onTreeChangeSelection(item.getValue());
                }
            });
        }
    }

    public void persistPreferences() {
        Preferences prefs = Preferences.userNodeForPackage(getClass());
        prefs.put(RECENT_DEFAULT + "1", menuRecent1.getText());
        prefs.put(RECENT_DEFAULT + "2", menuRecent2.getText());
        prefs.put(RECENT_DEFAULT + "3", menuRecent3.getText());
        prefs.put(RECENT_DEFAULT + "4", menuRecent4.getText());
    }

    public void onUndo(ActionEvent event) {
        editorProject.undoChange();
        redrawTreeControl();
    }

    public void onRedo(ActionEvent event) {
        editorProject.redoChange();
        redrawTreeControl();
    }

    public void onCut(ActionEvent event) {
        currentEditor.ifPresent(UIMenuItem::handleCut);
    }

    public void onCopy(ActionEvent event) {
        currentEditor.ifPresent(UIMenuItem::handleCopy);
    }

    public void onPaste(ActionEvent event) {
        currentEditor.ifPresent(UIMenuItem::handlePaste);
    }

    public void onShowEditMenu(Event event) {
        currentEditor.ifPresentOrElse((uiItem)-> {
            menuCopy.setDisable(!uiItem.canCopy());
            menuCut.setDisable(!uiItem.canCopy());
            menuPaste.setDisable(!uiItem.canPaste());
        }, ()-> {
            menuCopy.setDisable(true);
            menuCut.setDisable(true);
            menuPaste.setDisable(true);
        });
        menuRedo.setDisable(!editorProject.canRedo());
        menuUndo.setDisable(!editorProject.canUndo());
    }


    private void handleRecents() {
        if (!editorProject.isFileNameSet()) return;

        List<String> recents = Arrays.asList(editorProject.getFileName(), menuRecent1.getText(), menuRecent2.getText(),
                                             menuRecent3.getText(), menuRecent4.getText());
        LinkedList<String> cleanedRecents = recents.stream()
                .filter(name -> !name.equals(RECENT_DEFAULT))
                .distinct()
                .collect(Collectors.toCollection(LinkedList::new));

        List<javafx.scene.control.MenuItem> recentItems = Arrays.asList(menuRecent1, menuRecent2, menuRecent3, menuRecent4);
        recentItems.forEach(menuItem -> {
            if (cleanedRecents.isEmpty()) {
                menuItem.setText(RECENT_DEFAULT);
            } else {
                menuItem.setText(cleanedRecents.removeFirst());
            }
        });
    }

    public void installLibraries(Event actionEvent) {
        try {
            installer.copyLibraryFromPackage("IoAbstraction");
            installer.copyLibraryFromPackage("tcMenu");
            installer.copyLibraryFromPackage("LiquidCrystalIO");
            onTreeChangeSelection(MenuTree.ROOT);
        } catch (IOException e) {
            logger.log(ERROR, "Did not complete copying embedded files", e);
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to copy embedded files");
            alert.setTitle("Error while copying");
            alert.showAndWait();
        }
    }
}
