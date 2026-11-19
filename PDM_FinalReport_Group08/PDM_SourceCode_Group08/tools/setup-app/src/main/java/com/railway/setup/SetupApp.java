package com.railway.setup;

import com.railway.setup.core.Project;
import com.railway.setup.core.SetupLog;
import com.railway.setup.core.Output;
import com.railway.setup.ui.MainWindow;
import com.railway.setup.ui.Theme;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Railway Setup: everything a team member needs after "git clone", behind
 * buttons. Started by Setup.exe / Setup.bat from the repository root.
 */
public final class SetupApp {

    private SetupApp() {
    }

    public static void main(String[] args) {
        System.setProperty("flatlaf.uiScale.allowScaleDown", "true");
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            if (!Project.isProjectRoot(Project.root())) {
                JOptionPane.showMessageDialog(null,
                        "Railway Setup must stay in the project folder (next to pom.xml and the database folder).\n"
                                + "It is looking in: " + Project.root(),
                        "Railway Setup", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
            SetupLog.write(Output.Kind.TEXT, "Railway Setup started, Java " + System.getProperty("java.version")
                    + " at " + Project.javaHome() + ", project " + Project.root());
            new MainWindow().setVisible(true);
        });
    }
}
