import MapLoad.MapManager;
import MapLoad.TmxParser;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

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

            System.out.println("모든 맵 이미지 미리 로드 시작...");
            viewer.preloadMultipleTmxFiles(mapPaths);

            // 첫 번째 맵(Farm.tmx) 로드 및 표시
            if (new java.io.File(mapPaths[0]).exists()) {
                viewer.loadTMX(mapPaths[0]);
                viewer.setCurrentMapPath(mapPaths[0]);

                // 플레이어 시작 위치 설정
                viewer.setPlayerStartPosition(10, 10);

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

    /**
     * 맵 간 전환 트리거 설정
     */
    private static void setupMapTransitions(TmxParser viewer) {
        // Farm -> FarmHouse 전환 (집 입구 타일 밟으면 집 안으로)
        viewer.addMapTransition("resource/Farm.tmx",
                3, 6,
                "resource/FarmHouse.tmx",
                3, 8);                     // FarmHouse의 (5, 12)로 이동

        // FarmHouse -> Farm 전환 (집 문 밟으면 밖으로)
        viewer.addMapTransition("resource/FarmHouse.tmx",
                3, 9,                    // FarmHouse의 (5, 12) 타일 밟으면
                "resource/Farm.tmx",
                10, 10);                   // Farm의 (15, 8)로 이동

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