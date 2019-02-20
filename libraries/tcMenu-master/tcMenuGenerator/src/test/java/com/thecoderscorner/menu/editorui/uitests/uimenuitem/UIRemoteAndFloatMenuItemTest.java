package com.thecoderscorner.menu.editorui.uitests.uimenuitem;

import com.thecoderscorner.menu.domain.FloatMenuItem;
import com.thecoderscorner.menu.domain.MenuItem;
import com.thecoderscorner.menu.domain.RemoteMenuItem;
import com.thecoderscorner.menu.domain.state.MenuTree;
import com.thecoderscorner.menu.editorui.uimodel.UIMenuItem;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@ExtendWith(ApplicationExtension.class)
public class UIRemoteAndFloatMenuItemTest extends UIMenuItemTestBase{

    @Start
    public void setup(Stage stage) {
        init(stage);
    }

    @AfterEach
    protected void closeWindow() {
        Platform.runLater(() -> stage.close());
    }

    @Test
    void testRemoteMenuItemEditing(FxRobot robot) throws InterruptedException {
        MenuItem remoteItem = menuTree.getMenuById(MenuTree.ROOT, 7).get();
        Optional<UIMenuItem> uiSubItem = editorUI.createPanelForMenuItem(remoteItem, menuTree, mockedConsumer);

        // open the sub menu item editor panel
        createMainPanel(uiSubItem);

        // firstly check that all the fields are populated properly
        performAllCommonChecks(remoteItem);

        tryToEnterBadValueIntoField(robot, "remoteNumField", "nameField", "100",
                "Remote No - Value must be between 0 and 3");

        robot.clickOn("#remoteNumField");
        robot.eraseText(4);
        robot.write("2");

        ArgumentCaptor<MenuItem> captor = ArgumentCaptor.forClass(MenuItem.class);
        verify(mockedConsumer, atLeastOnce()).accept(any(), captor.capture());
        RemoteMenuItem item = (RemoteMenuItem) captor.getValue();
        assertEquals(2, item.getRemoteNum());
    }

    @Test
    void testFloatMenuItemEditing(FxRobot robot) throws InterruptedException {
        MenuItem floatItem = menuTree.getMenuById(MenuTree.ROOT, 6).get();
        Optional<UIMenuItem> uiFloatPanel = editorUI.createPanelForMenuItem(floatItem, menuTree, mockedConsumer);

        // open the sub menu item editor panel
        createMainPanel(uiFloatPanel);

        // firstly check that all the fields are populated properly
        performAllCommonChecks(floatItem);

        tryToEnterBadValueIntoField(robot, "decimalPlacesField", "nameField", "100",
                "Decimal Places - Value must be between 1 and 6");

        robot.clickOn("#decimalPlacesField");
        robot.eraseText(4);
        robot.write("3");

        ArgumentCaptor<MenuItem> captor = ArgumentCaptor.forClass(MenuItem.class);
        verify(mockedConsumer, atLeastOnce()).accept(any(), captor.capture());
        FloatMenuItem item = (FloatMenuItem) captor.getValue();
        assertEquals(3, item.getNumDecimalPlaces());
    }


}
