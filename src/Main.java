import MapLoad.TmxParser;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        String[] Mappath = new String[] {"resource/FarmHouse.tmx", "resource/Farm.tmx"};

        SwingUtilities.invokeLater(() -> {
            TmxParser viewer = new TmxParser();

            viewer.loadDefaultTmxFile(Mappath[1]);

            viewer.show();
        });
    }
}