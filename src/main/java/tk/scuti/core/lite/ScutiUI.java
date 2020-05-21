package tk.scuti.core.lite;

import com.formdev.flatlaf.FlatDarculaLaf;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.function.Consumer;

public class ScutiUI extends JDialog {

    private JPanel contentPane;
    private JButton continueButton;
    private JButton inputFileButton;
    private JButton outputFileButton;

    private File inputFile;
    private File outputFile;

    public static void main(String[] args) {

        if (!FlatDarculaLaf.install()) {
            return;
        }
        ScutiUI dialog = new ScutiUI();
        dialog.setVisible(true);
    }

    public ScutiUI() {
        setMinimumSize(new Dimension(300, 200));
        setResizable(false);
        setContentPane(contentPane);
        setLocationRelativeTo(null);
        setModal(true);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        inputFileButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                findJar(selectedFile -> {
                    if(selectedFile == null){
                        JOptionPane.showMessageDialog(null, "Input file is null. try to select an valid file.");
                        return;
                    }
                    inputFile = selectedFile;
                });
            }
        });
        outputFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                findJar(selectedFile -> {
                    if(selectedFile == null){
                        JOptionPane.showMessageDialog(null, "Input file is null. try to select an valid file.");
                        return;
                    }
                    outputFile = selectedFile;
                });
            }
        });
        continueButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                if (!inputFile.exists() || !inputFile.canRead()) {
                    JOptionPane.showMessageDialog(null, "Failed when tried to get the inputFile.");
                    return;
                }
                try {
                    Scuti.start(inputFile, outputFile);
                } catch (Throwable throwable) {
                    JOptionPane.showMessageDialog(null, "Something failed. \n" + throwable.getMessage());
                    throwable.printStackTrace();
                }
            }
        });
    }

    public static void findJar(Consumer<File> fileConsumer){
        JFileChooser chooser = new JFileChooser(".");
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("Only jar files", "jar"));
        if(chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION){
            if(chooser.getSelectedFile() != null) {
                fileConsumer.accept(chooser.getSelectedFile());
                return;
            }
        }
        fileConsumer.accept(null);
    }

    private void onOK() {
        dispose();
    }

    private void onCancel() {
        dispose();
    }


}
