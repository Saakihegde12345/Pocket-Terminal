/* SPDX-License-Identifier: GPL-3.0-only */
package com.termux.app.studio;

import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import java.io.File;

/** Start an explicit executable in a fresh PTY, preserving the selected shell's input. */
final class StudioCommands {
    private StudioCommands() {}

    static void start(TermuxActivity activity, String program, String[] arguments, String title) {
        TermuxService service = activity.getTermuxService();
        if (service == null || service.wantsToStop() || activity.getCurrentSession() == null) {
            activity.showToast("Wait for the initial shell to finish starting.", true); return;
        }
        if (service.getTermuxSessionsSize() >= 8) {
            activity.showToast("Eight sessions are already open. Close one before continuing.", true); return;
        }
        File executable = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, program);
        if (!executable.isFile() || !executable.canExecute()) {
            activity.showToast("Install " + (program.equals("ssh") || program.equals("sftp") ? "openssh" : program)
                + " using the package catalog or pkg, then try again.", true); return;
        }
        TermuxSession created = service.createTermuxSession(executable.getPath(), arguments, null,
            TermuxConstants.TERMUX_HOME_DIR_PATH, false, title);
        if (created != null) {
            activity.getTermuxTerminalSessionClient().setCurrentSession(created.getTerminalSession());
            activity.getDrawer().closeDrawers();
            activity.getTerminalView().requestFocus();
        }
    }
}
