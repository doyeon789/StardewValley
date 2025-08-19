import MapLoad.TmxParser;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TmxParser viewer = new TmxParser();

            viewer.loadDefaultTmxFile("resource/FarmHouse.tmx");

            viewer.show();
        });
    }
}