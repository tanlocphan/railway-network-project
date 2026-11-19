package com.railway.app.ui.dialogs;

import com.railway.app.data.Row;
import com.railway.app.ui.components.TicketView;
import com.railway.app.ui.components.Ui;
import com.railway.app.ui.theme.AppTheme;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.KeyEvent;

/**
 * Shows one ticket with a Print button.
 */
public class TicketDialog extends JDialog {

    public TicketDialog(Component owner, Row ticket) {
        super(SwingUtilities.getWindowAncestor(owner), "Ticket " + ticket.str("Booking_ID"),
                ModalityType.APPLICATION_MODAL);
        TicketView view = new TicketView();
        view.setTicket(ticket);

        JButton print = Ui.secondary("Print", "printer");
        print.addActionListener(e -> view.print());
        JButton close = Ui.primary("Close", null);
        close.addActionListener(e -> dispose());

        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setBackground(AppTheme.BACKGROUND);
        content.setBorder(new EmptyBorder(24, 24, 18, 24));
        content.add(view, BorderLayout.CENTER);
        content.add(Ui.flowRight(10, print, close), BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(close);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setResizable(false);
        pack();
        setLocationRelativeTo(getOwner());
    }
}
