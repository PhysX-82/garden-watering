/*
 * Copyright (c) 2018 https://www.thecoderscorner.com (Nutricherry LTD).
 * This product is licensed under an Apache license, see the LICENSE file in the top-level directory.
 */

package com.thecoderscorner.menu.remote;

import com.thecoderscorner.menu.domain.*;
import com.thecoderscorner.menu.domain.state.MenuTree;
import com.thecoderscorner.menu.domain.util.MenuItemHelper;
import com.thecoderscorner.menu.domain.util.MenuItemVisitor;
import com.thecoderscorner.menu.remote.commands.*;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.thecoderscorner.menu.remote.RemoteInformation.NOT_CONNECTED;
import static com.thecoderscorner.menu.remote.commands.CommandFactory.newHeartbeatCommand;
import static com.thecoderscorner.menu.remote.commands.CommandFactory.newJoinCommand;
import static java.lang.System.Logger.Level.*;

/**
 * This class manages a single remote connection to an Arduino. It is responsible for check
 * if the connection is still alive, and sending heartbeat messages to keep the connection
 * alive too. This class abstracts the connectivity part away from the business logic.
 * The remote connection is then handled by the RemoteConnector. Normally, one creates a
 * whole remote stack using one the builders, such as Rs232ControllerBuilder.
 */
public class RemoteMenuController {

    private final System.Logger logger = System.getLogger(getClass().getSimpleName());
    private final RemoteConnector connector;
    private final MenuTree managedMenu;
    private final ScheduledExecutorService executor;
    private final Clock clock;
    private final AtomicLong lastRx = new AtomicLong();
    private final AtomicLong lastTx = new AtomicLong();
    private final AtomicReference<RemoteInformation> remoteParty = new AtomicReference<>(NOT_CONNECTED);
    private final int heartbeatFrequency;
    private final String localName;
    private final List<RemoteControllerListener> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean treeFullyPopulated = false;

    public RemoteMenuController(RemoteConnector connector, MenuTree managedMenu,
                                ScheduledExecutorService executor, String localName,
                                Clock clock, int heartbeatFrequency) {
        this.connector = connector;
        this.managedMenu = managedMenu;
        this.executor = executor;
        this.clock = clock;
        this.localName = localName;
        this.heartbeatFrequency = heartbeatFrequency;
    }

    /**
     * starts the remote connection such that it will attempt to establish connectivity
     */
    public void start() {
        connector.registerConnectorListener(this::onCommandReceived);
        connector.registerConnectionChangeListener(this::onConnectionChange);
        connector.start();
        executor.scheduleAtFixedRate(this::checkHeartbeat, 1000, 1000, TimeUnit.MILLISECONDS);
    }

    private void checkHeartbeat() {
        if(!connector.isConnected()) {
            return;
        }

        if((clock.millis() - lastRx.get()) > (3 * heartbeatFrequency)) {
            logger.log(WARNING, "Lost connection with " + getConnector().getConnectionName() + ", closing port");
            connector.close();
        }

        if((clock.millis() - lastTx.get()) > heartbeatFrequency) {
            logger.log(INFO, "Sending heartbeat to " + getConnector().getConnectionName() + " port");
            sendCommand(newHeartbeatCommand());
        }
    }

    private void onConnectionChange(RemoteConnector remoteConnector, boolean b) {
        if(b) {
            lastRx.set(clock.millis());
            sendCommand(CommandFactory.newJoinCommand(localName));
        }
        listeners.forEach(l-> l.connectionState(getRemotePartyInfo(), b));
    }

    /**
     * attempt to stop the underlying connector
     */
    public void stop() {
        connector.stop();
    }

    /**
     * send a command to the Arduino, normally use the CommandFactory to generate the command
     * @param command a command to send to the remote side.
     */
    public void sendCommand(MenuCommand command) {
        lastTx.set(clock.millis());
        try {
            connector.sendMenuCommand(command);
        } catch (IOException e) {
            logger.log(ERROR, "Error while writing out command", e);
            connector.close();
        }
    }

    /**
     * get the name of the device that we've connected to.
     * @return the connected remote device
     */
    public RemoteInformation getRemotePartyInfo() {
        return remoteParty.get();
    }

    /**
     * get the underlying connectivity, rarely needed
     * @return underlying connector
     */
    public RemoteConnector getConnector() {
        return connector;
    }

    /**
     * Check if all the menu items from the remote device are available locally yet.
     * @return true if the populated, otherwise false.
     */
    public boolean isTreeFullyPopulated() {
        return treeFullyPopulated;
    }

    /**
     * register for events when the tree becomes fully populated, a menu item changes
     * or there's a change in connectivity.
     * @param listener your listener to register for events
     */
    public void addListener(RemoteControllerListener listener) {
        listeners.add(listener);
    }

    private void onCommandReceived(RemoteConnector remoteConnector, MenuCommand menuCommand) {
        lastRx.set(clock.millis());
        switch(menuCommand.getCommandType()) {
            case HEARTBEAT:
                break;
            case JOIN:
                onJoinCommand((MenuJoinCommand) menuCommand);
                break;
            case BOOTSTRAP:
                onBootstrapCommand((MenuBootstrapCommand) menuCommand);
                break;
            case ANALOG_BOOT_ITEM:
            case ENUM_BOOT_ITEM:
            case BOOLEAN_BOOT_ITEM:
            case SUBMENU_BOOT_ITEM:
            case TEXT_BOOT_ITEM:
            case REMOTE_BOOT_ITEM:
            case FLOAT_BOOT_ITEM:
            case ACTION_BOOT_ITEM:
                onMenuItemBoot((BootItemMenuCommand)menuCommand);
                break;
            case CHANGE_INT_FIELD:
                onChangeField((MenuChangeCommand) menuCommand);
        }
    }

    @SuppressWarnings("unchecked")
    private void onMenuItemBoot(BootItemMenuCommand menuCommand) {
        managedMenu.addOrUpdateItem(menuCommand.getSubMenuId(), menuCommand.getMenuItem());
        managedMenu.changeItem(menuCommand.getMenuItem(), menuCommand.newMenuState(
                managedMenu.getMenuState(menuCommand.getMenuItem())));
        listeners.forEach(l-> l.menuItemChanged(menuCommand.getMenuItem(), false));
    }

    private void onChangeField(MenuChangeCommand menuCommand) {
        // we cannot process until the tree is populated
        if(!treeFullyPopulated) return;

        SubMenuItem subMenuParent = MenuItemHelper.asSubMenu(managedMenu.getSubMenuById(menuCommand.getParentItemId()).orElse(MenuTree.ROOT));
        managedMenu.getMenuById(subMenuParent, menuCommand.getMenuItemId()).ifPresent((item) -> {
            item.accept(new MenuItemVisitor() {
                @Override
                public void visit(AnalogMenuItem item) {
                    managedMenu.changeItem(item, item.newMenuState(Integer.valueOf(menuCommand.getValue()), true, false));
                    listeners.forEach(l-> l.menuItemChanged(item, true));
                }

                @Override
                public void visit(BooleanMenuItem item) {
                    managedMenu.changeItem(item, item.newMenuState(Integer.valueOf(menuCommand.getValue()) != 0, true, false));
                    listeners.forEach(l-> l.menuItemChanged(item, true));
                }

                @Override
                public void visit(EnumMenuItem item) {
                    managedMenu.changeItem(item, item.newMenuState(Integer.valueOf(menuCommand.getValue()), true, false));
                    listeners.forEach(l-> l.menuItemChanged(item, true));
                }

                @Override
                public void visit(SubMenuItem item) { /*ignored*/ }

                @Override
                public void visit(TextMenuItem item) {
                    managedMenu.changeItem(item, item.newMenuState(menuCommand.getValue(), true, false));
                    listeners.forEach(l-> l.menuItemChanged(item, true));
                }

                @Override
                public void visit(RemoteMenuItem item) {
                    managedMenu.changeItem(item, item.newMenuState(menuCommand.getValue(), true, false));
                    listeners.forEach(l-> l.menuItemChanged(item, true));
                }

                @Override
                public void visit(FloatMenuItem item) {
                    managedMenu.changeItem(item, item.newMenuState(Float.valueOf(menuCommand.getValue()), true, false));
                    listeners.forEach(l-> l.menuItemChanged(item, true));
                }

                @Override
                public void visit(ActionMenuItem item) {
                    /* ignored, there is no state for this type */
                }
            });

        });
    }

    private void onBootstrapCommand(MenuBootstrapCommand cmd) {
        treeFullyPopulated = (cmd.getBootType() == MenuBootstrapCommand.BootType.END);
        if(treeFullyPopulated) {
            listeners.forEach(RemoteControllerListener::treeFullyPopulated);
        }
    }

    private void onJoinCommand(MenuJoinCommand join) {
        remoteParty.set(new RemoteInformation(join.getMyName(), join.getApiVersion() / 100,
                join.getApiVersion() % 100, join.getPlatform()));
        listeners.forEach(l-> l.connectionState(getRemotePartyInfo(), true));
        executor.execute(() -> sendCommand(newJoinCommand(localName)) );
    }
}
