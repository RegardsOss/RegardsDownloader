package org.jdownloader.controlling.contextmenu.gui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

import org.appwork.utils.StringUtils;
import org.appwork.utils.swing.renderer.RenderLabel;
import org.jdownloader.actions.AppAction;
import org.jdownloader.controlling.contextmenu.CustomizableAppAction;
import org.jdownloader.controlling.contextmenu.MenuContainer;
import org.jdownloader.controlling.contextmenu.MenuItemData;
import org.jdownloader.controlling.contextmenu.MenuLink;
import org.jdownloader.controlling.contextmenu.SeparatorData;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.images.NewTheme;

public class Renderer implements TreeCellRenderer {
    private JLabel    renderer;

    private Icon      right;

    private Font      bold;

    private Font      defFont;

    private Dimension dim;

    public Renderer() {

        renderer = new RenderLabel();

        dim = new Dimension(500, 24);
        defFont = renderer.getFont();
        bold = defFont.deriveFont(Font.BOLD);
        right = new AbstractIcon(IconKey.ICON_RIGHT, 20);

    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {

        MenuItemData mid = ((MenuItemData) value);
        Rectangle bounds = null;
        TreePath path = tree.getPathForRow(row);

        if (!mid.isVisible()) {
            renderer.setEnabled(false);
        } else {
            renderer.setEnabled(true);
        }
        // renderer.setPreferredSize(dim);
        // renderer.setSize(new Dimension(Math.max(200, tree.getParent().getWidth()) - bounds.x - 20, 24));
        // tree.revalidate();

        // renderer.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.RED));

        Font font = defFont;
        String type = null;
        String name = mid.getName();
        StringBuilder tt = new StringBuilder();
        Icon icon = null;

        icon = (MenuItemData.getIcon(mid.getIconKey(), 20));

        if (mid._isValidated()) {

            String desc = mid._getDescription();
            if (StringUtils.isNotEmpty(desc)) {
                if (tt.length() > 0) {
                    tt.append("; ");
                }
                tt.append(desc);
            }
        }
        if (mid instanceof MenuContainer) {

            type = _GUI.T.InfoPanel_update_submenu();

            font = bold;
            // label.setText(_GUI.T.InfoPanel_updateInfo_header_actionlabel(, ));

        } else if (mid instanceof SeparatorData) {

            name = _GUI.T.Renderer_getTreeCellRendererComponent_separator();

        } else {
            if (mid instanceof MenuLink) {
                type = _GUI.T.InfoPanel_update_link();

            } else {
                if (mid._isValidated()) {
                    try {
                        AppAction action = mid.createAction();
                        if (action instanceof CustomizableAppAction) {
                            ((CustomizableAppAction) action).requestUpdate(this);
                        }

                        if (StringUtils.isEmpty(name)) {
                            name = action.getName();
                        }
                        type = _GUI.T.InfoPanel_update_action();
                        if (icon == null) {
                            icon = action.getSmallIcon();
                        }
                    } catch (Exception e) {

                    }
                }

                if (StringUtils.isEmpty(name)) {
                    name = mid.getActionData().getName();
                }
                if (icon == null) {
                    if (mid.getActionData().getIconKey() != null) {
                        icon = NewTheme.I().getIcon(mid.getActionData().getIconKey(), 18);
                    }
                }
                if (MenuItemData.isEmptyValue(mid.getIconKey())) {
                    icon = null;
                }
                if (StringUtils.isEmpty(name)) {

                    name = mid.getActionData().getClazzName();
                    name = name.substring(name.lastIndexOf(".") + 1);
                }
                if (MenuItemData.isEmptyValue(name)) {
                    name = mid.getActionData().getClazzName();
                    name = name.substring(name.lastIndexOf(".") + 1);
                    name = MenuItemData.EMPTY + "(" + name + ")";
                }

            }

        }

        if (tt.length() > 0) {
            name += " (Description: " + tt + ")";
        }

        if (mid != null && mid.getActionData() != null && StringUtils.isNotEmpty(mid.getActionData().getClazzName()) && mid.getActionData().getClazzName().contains("eventscripter")) {
            name = "EventTrigger -> " + name;
        }

        if (StringUtils.isNotEmpty(type)) {
            if (StringUtils.equals(_GUI.T.InfoPanel_update_action(), type)) {
                renderer.setText(name);
            } else {
                renderer.setText(_GUI.T.InfoPanel_updateInfo_header_actionlabel(name, type));
            }
        } else {
            renderer.setText(name);
        }
        renderer.setFont(font);
        renderer.setIcon(icon);
        return renderer;

    }
}
