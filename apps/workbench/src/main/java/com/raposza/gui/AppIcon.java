// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.Window;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;

/**
 * The window icon, and the X11 application class that goes with it.
 *
 * /icon.png is written into the classes directory at build time by
 * raposza-design-maven-plugin, from src/main/svg/icon.svg. A build that did
 * not run it leaves this class with nothing to load, and that case is
 * silent by design: an icon is not a reason to refuse to open a window.
 *
 * SEVERAL SIZES ARE OFFERED, not one. A task bar, a title bar and an
 * alt-tab switcher ask for different pixel sizes, and a toolkit given one
 * large image scales it itself with results that look like a mistake.
 *
 * WM_CLASS is BEST EFFORT and will normally fail. The only handle on it is a
 * private field of the X11 toolkit, and setAccessible on a package that is not
 * opened throws on any current JDK. It is attempted because it costs nothing
 * and, where it works, it is what makes the launcher show this icon rather than
 * a generic Java one. run.sh passes the --add-opens that makes it work; a plain
 * java -jar does not, and that is not an error worth printing.
 *
 * Author Claude/bentzn
 */
public final class AppIcon {

    /** Classpath location the build writes to. */
    public static final String PATH_ICON = "/icon.png";

    /** X11 application class; also what a .desktop file must declare. */
    public static final String NAME_WM_CLASS = "workbench";

    /** Sizes offered to the window manager, beside the full-size original. */
    private static final int[] ARR_SIZE = {16, 32, 64, 128};


    private AppIcon() {
    }


    /**
     * @param win the window to decorate, never null
     */
    public static void apply(Window win) {
        List<Image> lstIcon = load();
        if (!lstIcon.isEmpty())
            win.setIconImages(lstIcon);

        setWmClass(NAME_WM_CLASS);
    }


    /**
     * @return the icon at its own size followed by the scaled variants, empty
     *         when no icon is on the classpath or it could not be read
     */
    public static List<Image> load() {
        URL urlIcon = AppIcon.class.getResource(PATH_ICON);
        if (urlIcon == null)
            return List.of();

        Image imgFull = new ImageIcon(urlIcon).getImage();
        if (imgFull == null || imgFull.getWidth(null) <= 0)
            return List.of();

        List<Image> lstIcon = new ArrayList<>();
        lstIcon.add(imgFull);
        for (int cntPx : ARR_SIZE) {
            lstIcon.add(imgFull.getScaledInstance(cntPx, cntPx, Image.SCALE_SMOOTH));
        }
        return lstIcon;
    }


    /**
     * @param strWmClass the application class to report to the window manager
     */
    private static void setWmClass(String strWmClass) {
        try {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            Field fldClass = toolkit.getClass().getDeclaredField("awtAppClassName");
            fldClass.setAccessible(true);
            fldClass.set(toolkit, strWmClass);
        }
        catch (Exception ex) {
            // Not X11, or the package is not opened. Both are normal.
        }
        catch (Error err) {
            // InaccessibleObjectException is an exception, but a toolkit that
            // rejects the field in another way must not take the window with
            // it.
        }
    }

}
