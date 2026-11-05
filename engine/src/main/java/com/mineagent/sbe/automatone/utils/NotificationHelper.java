// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/utils/NotificationHelper.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.mineagent.sbe.automatone.utils;

import com.mineagent.sbe.automatone.Automatone;
import org.apache.commons.lang3.SystemUtils;

import java.awt.*;
import java.io.IOException;

/**
 * This class is not called from the main game thread.
 * Do not refer to any Minecraft classes, it wouldn't be thread safe.
 *
 * @author aUniqueUser
 */
public class NotificationHelper {

    private static TrayIcon trayIcon;

    public static void notify(String text, boolean error) {
        if (SystemUtils.IS_OS_WINDOWS) {
            windows(text, error);
        } else if (SystemUtils.IS_OS_MAC_OSX) {
            mac(text);
        } else if (SystemUtils.IS_OS_LINUX) {
            linux(text);
        }
    }

    private static void windows(String text, boolean error) {
        if (SystemTray.isSupported()) {
            try {
                if (trayIcon == null) {
                    SystemTray tray = SystemTray.getSystemTray();
                    Image image = Toolkit.getDefaultToolkit().createImage("");

                    trayIcon = new TrayIcon(image, "Baritone");
                    trayIcon.setImageAutoSize(true);
                    trayIcon.setToolTip("Baritone");
                    tray.add(trayIcon);
                }

                trayIcon.displayMessage("Baritone", text, error ? TrayIcon.MessageType.ERROR : TrayIcon.MessageType.INFO);
            } catch (Exception e) {
                Automatone.LOGGER.error(e);
            }
        } else {
            Automatone.LOGGER.error("SystemTray is not supported");
        }
    }

    private static void mac(String text) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("osascript", "-e", "display notification \"" + text + "\" with title \"Baritone\"");
        try {
            processBuilder.start();
        } catch (IOException e) {
            Automatone.LOGGER.error(e);
        }
    }

    // The only way to display notifications on linux is to use the java-gnome library,
    // or send notify-send to shell with a ProcessBuilder. Unfortunately the java-gnome
    // library is licenced under the GPL, see (https://en.wikipedia.org/wiki/Java-gnome)
    private static void linux(String text) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("notify-send", "-a", "Baritone", text);
        try {
            processBuilder.start();
        } catch (IOException e) {
            Automatone.LOGGER.error(e);
        }
    }
}
