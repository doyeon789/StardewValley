package MapLoad;

import javax.swing.*;
import java.util.*;
import java.util.List;

/**
 * 여러 맵을 동적으로 로드하고 관리하는 시스템
 */
public class MapManager {
    /** ========== 맵 정보 저장 클래스 ========== **/
    public static class MapInfo {
        public String mapPath;              // TMX 파일 경로
        public String mapName;              // 맵 이름
        public int playerStartX;            // 플레이어 시작 X 좌표 (타일)
        public int playerStartY;            // 플레이어 시작 Y 좌표 (타일)
        public Map<String, Object> metadata; // 추가 메타데이터

        public MapInfo(String mapPath, String mapName, int startX, int startY) {
            this.mapPath = mapPath;
            this.mapName = mapName;
            this.playerStartX = startX;
            this.playerStartY = startY;
            this.metadata = new HashMap<>();
        }
    }

    /** ========== 맵 관리 변수들 ========== **/
    private List<MapInfo> availableMaps;        // 사용 가능한 모든 맵 목록
    private int currentMapIndex;                // 현재 활성 맵 인덱스
    private TmxParser tmxParser;                // TMX 파서 참조

    /** ========== 맵 전환 콜백 인터페이스 ========== **/
    public interface MapChangeListener {
        void onMapChanged(MapInfo oldMap, MapInfo newMap);
        void onMapLoadFailed(MapInfo mapInfo, Exception error);
    }

    private List<MapChangeListener> listeners;

    public MapManager(TmxParser tmxParser) {
        this.tmxParser = tmxParser;
        this.availableMaps = new ArrayList<>();
        this.listeners = new ArrayList<>();
        this.currentMapIndex = -1;
    }

    /** ========== 맵 등록 메서드들 ========== **/

    /**
     * 단일 맵 추가
     */
    public void addMap(String mapPath, String mapName, int startX, int startY) {
        MapInfo mapInfo = new MapInfo(mapPath, mapName, startX, startY);
        availableMaps.add(mapInfo);
        System.out.println("맵 추가됨: " + mapName + " (" + mapPath + ")");
    }

    /**
     * 여러 맵을 한번에 추가
     */
    public void addMaps(String[] mapPaths) {
        for (int i = 0; i < mapPaths.length; i++) {
            String path = mapPaths[i];
            String name = extractMapName(path);
            // 기본 시작 위치 설정 (나중에 변경 가능)
            addMap(path, name, 10, 10);
        }
    }

    /**
     * 맵 설정을 위한 빌더 클래스
     */
    public static class MapBuilder {
        private String mapPath;
        private String mapName;
        private int startX = 10;
        private int startY = 10;
        private Map<String, Object> metadata = new HashMap<>();

        public MapBuilder(String mapPath) {
            this.mapPath = mapPath;
            this.mapName = extractMapName(mapPath);
        }

        public MapBuilder name(String name) {
            this.mapName = name;
            return this;
        }

        public MapBuilder startPosition(int x, int y) {
            this.startX = x;
            this.startY = y;
            return this;
        }

        public MapBuilder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public MapInfo build() {
            MapInfo info = new MapInfo(mapPath, mapName, startX, startY);
            info.metadata.putAll(metadata);
            return info;
        }
    }

    /**
     * 빌더 패턴을 사용한 맵 추가
     */
    public void addMap(MapBuilder builder) {
        MapInfo mapInfo = builder.build();
        availableMaps.add(mapInfo);
        System.out.println("맵 추가됨 (빌더): " + mapInfo.mapName);
    }

    /** ========== 맵 로딩 및 전환 메서드들 ========== **/

    /**
     * 인덱스로 맵 로드
     */
    public boolean loadMap(int index) {
        if (index < 0 || index >= availableMaps.size()) {
            System.err.println("잘못된 맵 인덱스: " + index);
            return false;
        }

        MapInfo oldMap = getCurrentMap();
        MapInfo newMap = availableMaps.get(index);

        System.out.println("맵 로딩 시도: " + newMap.mapName + " (" + newMap.mapPath + ")");

        try {
            boolean success = tmxParser.loadTMX(newMap.mapPath);
            if (success) {
                currentMapIndex = index;
                // 플레이어 시작 위치 설정
                tmxParser.setPlayerStartPosition(newMap.playerStartX, newMap.playerStartY);

                // 리스너들에게 맵 변경 알림
                notifyMapChanged(oldMap, newMap);

                System.out.println("맵 로딩 성공: " + newMap.mapName);
                return true;
            } else {
                System.err.println("맵 로딩 실패: " + newMap.mapName);
                return false;
            }
        } catch (Exception e) {
            System.err.println("맵 로딩 중 오류: " + e.getMessage());
            notifyMapLoadFailed(newMap, e);
            return false;
        }
    }

    /**
     * 맵 이름으로 로드
     */
    public boolean loadMap(String mapName) {
        for (int i = 0; i < availableMaps.size(); i++) {
            if (availableMaps.get(i).mapName.equals(mapName)) {
                return loadMap(i);
            }
        }
        System.err.println("맵을 찾을 수 없음: " + mapName);
        return false;
    }

    /**
     * 첫 번째 맵 로드 (초기화용)
     */
    public boolean loadFirstMap() {
        if (availableMaps.isEmpty()) {
            System.err.println("등록된 맵이 없습니다.");
            return false;
        }
        return loadMap(0);
    }

    /**
     * 다음 맵으로 전환
     */
    public boolean loadNextMap() {
        if (availableMaps.isEmpty()) return false;
        int nextIndex = (currentMapIndex + 1) % availableMaps.size();
        return loadMap(nextIndex);
    }

    /**
     * 이전 맵으로 전환
     */
    public boolean loadPreviousMap() {
        if (availableMaps.isEmpty()) return false;
        int prevIndex = (currentMapIndex - 1 + availableMaps.size()) % availableMaps.size();
        return loadMap(prevIndex);
    }

    /** ========== 맵 정보 조회 메서드들 ========== **/

    public MapInfo getCurrentMap() {
        if (currentMapIndex >= 0 && currentMapIndex < availableMaps.size()) {
            return availableMaps.get(currentMapIndex);
        }
        return null;
    }

    public List<MapInfo> getAllMaps() {
        return new ArrayList<>(availableMaps);
    }

    public int getMapCount() {
        return availableMaps.size();
    }

    public int getCurrentMapIndex() {
        return currentMapIndex;
    }

    /** ========== 유틸리티 메서드들 ========== **/

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

    /**
     * 맵 변경 리스너 등록
     */
    public void addMapChangeListener(MapChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * 맵 변경 리스너 제거
     */
    public void removeMapChangeListener(MapChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * 맵 변경 알림
     */
    private void notifyMapChanged(MapInfo oldMap, MapInfo newMap) {
        for (MapChangeListener listener : listeners) {
            try {
                listener.onMapChanged(oldMap, newMap);
            } catch (Exception e) {
                System.err.println("맵 변경 리스너 오류: " + e.getMessage());
            }
        }
    }

    /**
     * 맵 로딩 실패 알림
     */
    private void notifyMapLoadFailed(MapInfo mapInfo, Exception error) {
        for (MapChangeListener listener : listeners) {
            try {
                listener.onMapLoadFailed(mapInfo, error);
            } catch (Exception e) {
                System.err.println("맵 로딩 실패 리스너 오류: " + e.getMessage());
            }
        }
    }

    /**
     * 현재 맵의 플레이어 시작 위치 업데이트
     */
    public void updatePlayerStartPosition(int x, int y) {
        MapInfo current = getCurrentMap();
        if (current != null) {
            current.playerStartX = x;
            current.playerStartY = y;
        }
    }

    /**
     * 디버그 정보 출력
     */
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== MapManager Debug Info ===\n");
        sb.append("총 맵 개수: ").append(availableMaps.size()).append("\n");
        sb.append("현재 맵 인덱스: ").append(currentMapIndex).append("\n");

        MapInfo current = getCurrentMap();
        if (current != null) {
            sb.append("현재 맵: ").append(current.mapName).append(" (").append(current.mapPath).append(")\n");
            sb.append("시작 위치: (").append(current.playerStartX).append(", ").append(current.playerStartY).append(")\n");
        }

        sb.append("\n등록된 맵 목록:\n");
        for (int i = 0; i < availableMaps.size(); i++) {
            MapInfo map = availableMaps.get(i);
            String marker = (i == currentMapIndex) ? " [CURRENT]" : "";
            sb.append("  ").append(i).append(": ").append(map.mapName).append(marker).append("\n");
        }

        return sb.toString();
    }
}