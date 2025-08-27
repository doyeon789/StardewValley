import MapLoad.TmxParser;
import MapLoad.TmxParser.PathTileCustomization.RenderMode;

import javax.swing.*;

public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // TMX 파서 생성
            TmxParser viewer = new TmxParser();

            // 모든 맵의 이미지를 미리 캐싱
            String[] mapPaths = {
                    "resource/Farm.tmx",
                    "resource/FarmHouse.tmx",
            };

            setupPathCustomizations(viewer);

            // 첫 번째 맵(Farm.tmx) 로드 및 표시
            if (new java.io.File(mapPaths[0]).exists()) {
                viewer.loadTMX(mapPaths[0]);
                viewer.setCurrentMapPath(mapPaths[0]);

                // 플레이어 시작 위치 설정
                viewer.setPlayerStartPosition(67, 15);

                setupMapTransitions(viewer);

                // 맵 전환 트리거 설정
                setupMapTransitions(viewer);

                System.out.println("게임 시작: " + extractMapName(mapPaths[0]));
            } else {
                System.err.println("첫 번째 맵을 찾을 수 없습니다: " + mapPaths[0]);
            }

            // 뷰어 표시
            viewer.show();
        });
    }

    private static void setupPathCustomizations(TmxParser viewer) {
        viewer.printPathLayerGids();

        //Grass
        viewer.addPathTileCustomization(87, "resource/TerrainFeatures/grass.png",
                0, 15, 20, RenderMode.ASPECT_FILL,0,0,0, true);

        //small Bush
        viewer.addPathTileCustomization(91,"resource/TileSheets/bushes.png",
                56, 16, 32, RenderMode.ASPECT_FILL,0,0,0, false);

        //Big Bush
        viewer.addPathTileCustomization(90,"resource/TileSheets/bushes.png",
                0, 32, 45, RenderMode.ORIGINAL_SIZE,0,0,0, false);

        // Nothing
        viewer.addPathTileCustomization(94,"resource/Maps/paths.png",
                0, 16, 16, RenderMode.ASPECT_FIT,0,0,0, false);
        viewer.addPathTileCustomization(95,"resource/Maps/paths.png",
                0, 16, 16, RenderMode.ASPECT_FIT, 0,0,0, false);

        //Big Tree Stump
        viewer.addPathTileCustomization(86,"resource/Maps/springobjects.png",
                156, 32, 32, RenderMode.ORIGINAL_SIZE,0,0,-16, false);
        //Big Stone
        viewer.addPathTileCustomization(85,"resource/Maps/springobjects.png",
               168, 32, 32, RenderMode.ORIGINAL_SIZE,0,0,0, false);

        System.out.println("Path 타일 커스터마이징 설정 완료");
    }

    private static void setupMapTransitions(TmxParser viewer) {
        // Farm -> FarmHouse 전환 (집 입구 타일 밟으면 집 안으로)
        viewer.addMapTransition("resource/Farm.tmx",
                77, 8,                   // Farm의 (3, 6) 타일 밟으면
                "resource/FarmHouse.tmx",
                3, 6);                     // FarmHouse의 (5, 8)로 이동

        // FarmHouse -> Farm 전환 (집 문 밟으면 밖으로)
        viewer.addMapTransition("resource/FarmHouse.tmx",
                3, 9,                    // FarmHouse의 (3, 9) 타일 밟으면
                "resource/Farm.tmx",
                67, 15);                   // Farm의 (15, 8)로 이동

        // 추가 전환점들 (필요에 따라)
        // viewer.addMapTransition("resource/Farm.tmx", 25, 30, "resource/AnotherMap.tmx", 10, 10);

        System.out.println("맵 전환 트리거 설정 완료");
    }

    /**
     * 파일 경로에서 맵 이름 추출
     */
    private static String extractMapName(String filePath) {
        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        return fileName;
    }

}