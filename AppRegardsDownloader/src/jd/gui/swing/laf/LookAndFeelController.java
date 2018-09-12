//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//     along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.gui.swing.laf;

import java.io.File;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.metal.MetalLookAndFeel;

import jd.SecondLevelLaunch;

import org.appwork.storage.config.JsonConfig;
import org.appwork.storage.config.ValidationException;
import org.appwork.storage.config.events.GenericConfigEventListener;
import org.appwork.storage.config.handler.KeyHandler;
import org.appwork.swing.components.tooltips.ExtTooltip;
import org.appwork.swing.synthetica.SyntheticaHelper;
import org.appwork.uio.UIOManager;
import org.appwork.utils.Application;
import org.appwork.utils.IO;
import org.appwork.utils.StringUtils;
import org.appwork.utils.logging2.LogSource;
import org.appwork.utils.logging2.extmanager.LoggerFactory;
import org.appwork.utils.os.CrossSystem;
import org.appwork.utils.swing.dialog.Dialog;
import org.appwork.utils.swing.dialog.LAFManagerInterface;
import org.appwork.utils.swing.windowmanager.WindowManager;
import org.appwork.utils.swing.windowmanager.WindowsWindowManager;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.images.NewTheme;
import org.jdownloader.logging.LogController;
import org.jdownloader.settings.GraphicalUserInterfaceSettings;
import org.jdownloader.settings.GraphicalUserInterfaceSettings.LookAndFeelType;
import org.jdownloader.settings.staticreferences.CFG_GUI;
import org.jdownloader.updatev2.UpdateController;
import org.jdownloader.updatev2.gui.LAFOptions;

public class LookAndFeelController implements LAFManagerInterface {

    public static final String DE_JAVASOFT_PLAF_SYNTHETICA_SYNTHETICA_SIMPLE2D_LOOK_AND_FEEL = "org.jdownloader.gui.laf.jddefault.JDDefaultLookAndFeel";

    public static final String JD_PLAIN = "org.jdownloader.gui.laf.plain.PlainLookAndFeel";

    private static final LookAndFeelController INSTANCE = new LookAndFeelController();

    /**
     * get the only existing instance of LookAndFeelController. This is a singleton
     */
    public static LookAndFeelController getInstance() {
        return LookAndFeelController.INSTANCE;
    }

    private GraphicalUserInterfaceSettings config;

    private LogSource logger;

    /**
     * Create a new instance of LookAndFeelController. This is a singleton class. Access the only existing instance by using
     * {@link #getInstance()}.
     */
    private LookAndFeelController() {
        config = JsonConfig.create(GraphicalUserInterfaceSettings.class);
        logger = LogController.getInstance().getLogger(getClass().getName());

        SecondLevelLaunch.UPDATE_HANDLER_SET.executeWhenReached(new Runnable() {

            @Override
            public void run() {
                CFG_GUI.LOOK_AND_FEEL_THEME.getEventSender().addListener(new GenericConfigEventListener<Enum>() {

                    @Override
                    public void onConfigValueModified(KeyHandler<Enum> keyHandler, Enum newValue) {
                        handleThemesInstallation();
                    }

                    @Override
                    public void onConfigValidatorError(KeyHandler<Enum> keyHandler, Enum invalidValue,
                            ValidationException validateException) {
                    }
                });
                handleThemesInstallation();
            }
        });
    }

    protected void handleThemesInstallation() {
        if (UpdateController.getInstance().getHandler() == null) {
            return;
        }

        LookAndFeelType lafTheme = CFG_GUI.CFG.getLookAndFeelTheme();
        if (lafTheme == null) {
            lafTheme = LookAndFeelType.DEFAULT;
            CFG_GUI.CFG.setLookAndFeelTheme(lafTheme);
        }

        if (LookAndFeelType.DEFAULT.equals(lafTheme) || lafTheme.isAvailable() || lafTheme.getExtensionID() == null) {
            return;
        }

        if (UpdateController.getInstance().isExtensionInstalled(lafTheme.getExtensionID())) {
            return;
        }
        if (UIOManager.I().showConfirmDialog(0, _GUI.T.LookAndFeelController_handleThemesInstallation_title_(),
                                             _GUI.T.LookAndFeelController_handleThemesInstallation_message_(
                                                     lafTheme.name()), new AbstractIcon(IconKey.ICON_UPDATERICON0, 64),
                                             null, null)) {
            final LookAndFeelType finalLafTheme = lafTheme;
            new Thread("Install Extension") {

                public void run() {
                    try {
                        UpdateController.getInstance().setGuiVisible(true);
                        UpdateController.getInstance().runExtensionInstallation(finalLafTheme.getExtensionID());
                        while (true) {
                            Thread.sleep(500);
                            if (!UpdateController.getInstance().isRunning()) {
                                break;
                            }
                            UpdateController.getInstance().waitForUpdate();
                        }
                    } catch (Exception e) {
                        org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().log(e);
                    }
                }
            }.start();
        } else {
            CFG_GUI.CFG.setLookAndFeelTheme(LookAndFeelType.DEFAULT);
        }
    }

    /**
     * Config parameter to store the users laf selection
     */

    public static final String DEFAULT_PREFIX = "LAF_CFG";

    private static boolean uiInitated = false;

    /**
     * setups the correct Look and Feel
     */
    public synchronized void setUIManager() {
        if (uiInitated) {
            return;
        } else {
            uiInitated = true;
        }
        initWindowManager();
        try {
            // de.javasoft.plaf.synthetica.SyntheticaLookAndFeel.setLookAndFeel("de.javasoft.plaf.synthetica.SyntheticaStandardLookAndFeel");
            String laf = null;
            try {
                final String customLookAndFeel = config.getCustomLookAndFeelClass();
                if (StringUtils.isNotEmpty(customLookAndFeel)) {
                    try {
                        Class.forName(customLookAndFeel);
                        laf = customLookAndFeel;
                    } catch (Throwable e) {
                        logger.log(e);
                    }
                }
                if (laf == null) {
                    final LookAndFeelType theme = config.getLookAndFeelTheme();
                    if (theme == null || !theme.isAvailable()) {
                        laf = LookAndFeelType.DEFAULT.getClazz();
                    } else {
                        laf = theme.getClazz();
                    }
                }
            } catch (Throwable e) {
                logger.log(e);
                laf = LookAndFeelType.DEFAULT.getClazz();
            } finally {
                org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().info("Use Look & Feel: " + laf);
            }
            LAFOptions.init(laf);
            ExtTooltip.setForgroundColor(LAFOptions.getInstance().getColorForTooltipForeground());
            new SyntheticaHelper(LAFOptions.getInstance().getCfg()).load();
        } catch (Throwable e) {
            try {
                LookAndFeel currentLaf = UIManager.getLookAndFeel();
                // this may happen if the updater launcher already has set the look and feel.
                if (currentLaf != null && !(currentLaf instanceof MetalLookAndFeel)) {
                    LogController.CL().info("Don't set System look and feel " + currentLaf + " is already set");
                    return;
                }
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                LAFOptions.init(UIManager.getSystemLookAndFeelClassName());
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e1) {
                e1.printStackTrace();
            }
        } finally {
            try {
                final String theme = LAFOptions.getInstance().getCfg().getIconSetID();
                NewTheme.getInstance().setTheme(theme);
            } catch (Throwable e) {
                LoggerFactory.getDefaultLogger().log(e);
            }
        }
    }

    private void initWindowManager() {
        if (Application.isHeadless()) {
            return;
        }
        WindowManager wm = WindowManager.getInstance();
        if (wm instanceof WindowsWindowManager && CrossSystem.isWindows()) {
            final WindowsWindowManager wwm = (WindowsWindowManager) wm;

            wwm.setAltWorkaroundEnabled(CFG_GUI.CFG.isWindowsWindowManagerAltKeyWorkaroundEnabled());
            wwm.setAltWorkaroundKeys(CFG_GUI.CFG.getWindowsWindowManagerAltKeyCombi());
            try {
                CFG_GUI.CFG
                        .setWindowsWindowManagerForegroundLockTimeout(WindowsWindowManager.readForegroundLockTimeout());
            } catch (Exception e) {
                CFG_GUI.CFG.setWindowsWindowManagerForegroundLockTimeout(-1);
                logger.log(e);
            }
            CFG_GUI.WINDOWS_WINDOW_MANAGER_FOREGROUND_LOCK_TIMEOUT.getEventSender()
                    .addListener(new GenericConfigEventListener<Integer>() {

                        @Override
                        public void onConfigValidatorError(KeyHandler<Integer> keyHandler, Integer invalidValue,
                                ValidationException validateException) {
                        }

                        @Override
                        public void onConfigValueModified(KeyHandler<Integer> keyHandler, Integer newValue) {
                            try {
                                if (newValue >= 0 && newValue != WindowsWindowManager.readForegroundLockTimeout()) {

                                    WindowsWindowManager.writeForegroundLockTimeout(newValue);
                                    Dialog.getInstance().showMessageDialog(
                                            _GUI.T.LookAndFeelController_onConfigValueModified_reboot_required());
                                }
                            } catch (Exception e) {
                                logger.log(e);
                                Dialog.getInstance().showExceptionDialog(_GUI.T.lit_error_occured(), e.getMessage(), e);
                            }
                        }
                    });
            CFG_GUI.WINDOWS_WINDOW_MANAGER_ALT_KEY_WORKAROUND_ENABLED.getEventSender()
                    .addListener(new GenericConfigEventListener<Boolean>() {

                        @Override
                        public void onConfigValidatorError(KeyHandler<Boolean> keyHandler, Boolean invalidValue,
                                ValidationException validateException) {
                        }

                        @Override
                        public void onConfigValueModified(KeyHandler<Boolean> keyHandler, Boolean newValue) {
                            wwm.setAltWorkaroundEnabled(CFG_GUI.CFG.isWindowsWindowManagerAltKeyWorkaroundEnabled());
                        }
                    });
            CFG_GUI.WINDOWS_WINDOW_MANAGER_ALT_KEY_COMBI.getEventSender()
                    .addListener(new GenericConfigEventListener<int[]>() {

                        @Override
                        public void onConfigValueModified(KeyHandler<int[]> keyHandler, int[] newValue) {
                            wwm.setAltWorkaroundKeys(CFG_GUI.CFG.getWindowsWindowManagerAltKeyCombi());
                        }

                        @Override
                        public void onConfigValidatorError(KeyHandler<int[]> keyHandler, int[] invalidValue,
                                ValidationException validateException) {
                        }
                    });
        }
    }

    @Override
    public void init() {

        setUIManager();
    }
}
