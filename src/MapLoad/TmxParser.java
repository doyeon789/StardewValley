package MapLoad;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.*;
import java.util.List;

import  Character.SpriteRenderer;

public class TmxParser {
    // 타일셋 정보를 저장하는 클래스
    static class Tileset {
        int firstGid;
        String name;
        int tileWidth;
        int tileHeight;
        int tileCount;
        int columns;
        String imagePath;
        BufferedImage image;

        // 타일 캐시 추가
        Map<Integer, BufferedImage> tileCache = new HashMap<>();
    }

    // 레이어 정보를 저장하는 클래스
    static class Layer {
        String name;
        int width;
        int height;
        int[] data;
        boolean visible = true;
    }

    // 커스텀 렌더링 컴포넌트 (뷰포트 컬링 추가)
    private class TileMapCanvas extends JComponent {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            renderTileMapOptimized(g);
        }

        /*
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(mapWidth * tileWidth, mapHeight * tileHeight);
        }
        */
    }

    // 맵 정보
    private int mapWidth;
    private int mapHeight;
    private int tileWidth;
    private int tileHeight;
    private List<Tileset> tilesets;
    private List<Layer> layers;

    // 모든 PNG 이미지를 저장하는 맵 (파일명 -> 이미지)
    private Map<String, BufferedImage> preloadedImages;

    // 성능 최적화를 위한 추가 필드
    private Map<Integer, Tileset> gidToTilesetCache; // GID -> Tileset 캐시
    private Map<Integer, BufferedImage> globalTileCache; // GID -> 타일이미지 캐시

    private JFrame frame;
    private TileMapCanvas canvas;

    private SpriteRenderer sprite;

    public TmxParser() {
        tilesets = new ArrayList<>();
        layers = new ArrayList<>();
        preloadedImages = new HashMap<>();
        gidToTilesetCache = new HashMap<>();
        globalTileCache = new HashMap<>();

        // 생성자에서 PNG 파일들을 미리 로드
        preloadAllPngImages();

        initializeUI();
    }

    private void initializeUI() {
        // 스프라이트 생성
        sprite = new SpriteRenderer();
        sprite.setPosition(200, 150); // 초기 위치 설정

        frame = new JFrame("TMX 타일맵 뷰어 (최적화됨)");
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        canvas = new TileMapCanvas();
        canvas.setPreferredSize(new Dimension(1200, 780));
        canvas.setBackground(Color.BLACK);
        canvas.setOpaque(true);
        canvas.requestFocusInWindow();

        // 키보드 이벤트 리스너 추가
        canvas.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                String key = java.awt.event.KeyEvent.getKeyText(e.getKeyCode()).toLowerCase();
                sprite.handleKeyPressed(key);
                canvas.repaint(); // 화면 업데이트
            }

            @Override
            public void keyReleased(java.awt.event.KeyEvent e) {
                String key = java.awt.event.KeyEvent.getKeyText(e.getKeyCode()).toLowerCase();
                sprite.handleKeyReleased(key);
                canvas.repaint(); // 화면 업데이트
            }
        });

        // canvas만 추가 (스프라이트는 canvas 위에 그려짐)
        frame.add(canvas);
    }

    // 자동으로 기본 TMX 파일 찾아서 로드하는 메소드
    public void loadDefaultTmxFile(String Mappath) {

        File file = new File(Mappath);
        if (file.exists()) {
            System.out.println("기본 TMX 파일 발견: " + Mappath);
            if (loadTMX(Mappath)) {
                frame.setTitle("TMX 타일맵 뷰어 - " + file.getName());
                return;
            }
        }
        // resource 디렉토리에서 첫 번째 .tmx 파일 찾기
        File resourceDir = new File("resource");
        if (resourceDir.exists() && resourceDir.isDirectory()) {
            File[] tmxFiles = resourceDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".tmx"));
            if (tmxFiles != null && tmxFiles.length > 0) {
                String path = tmxFiles[0].getAbsolutePath();
                System.out.println("resource 디렉토리에서 TMX 파일 발견: " + path);
                if (loadTMX(path)) {
                    frame.setTitle("TMX 타일맵 뷰어 - " + tmxFiles[0].getName());
                    return;
                }
            }
        }

        System.out.println("기본 TMX 파일을 찾을 수 없습니다.");
    }

    public void show() {
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        SwingUtilities.invokeLater(() -> {
            canvas.requestFocusInWindow();
        });
    }

    // resource 디렉토리의 모든 PNG 파일을 재귀적으로 미리 로드
    private void preloadAllPngImages() {
        preloadedImages.clear();

        File resourceDir = new File("resource");
        if (!resourceDir.exists() || !resourceDir.isDirectory()) {
            File projectRoot = new File(System.getProperty("user.dir"));
            resourceDir = new File(projectRoot, "resource");

            if (!resourceDir.exists() || !resourceDir.isDirectory()) {
                System.err.println("resource 디렉토리를 찾을 수 없습니다: " + resourceDir.getAbsolutePath());
                return;
            }
        }

        System.out.println("resource 디렉토리에서 PNG 파일들을 재귀적으로 로드 중: " + resourceDir.getAbsolutePath());

        // 재귀적으로 PNG 파일 찾기
        List<File> pngFiles = new ArrayList<>();
        collectPngFiles(resourceDir, pngFiles);

        for (File pngFile : pngFiles) {
            try {
                BufferedImage image = ImageIO.read(pngFile);
                String fileName = pngFile.getName();
                String fileNameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));

                // 확장자 포함/미포함 둘 다 저장
                preloadedImages.put(fileName, image);
                preloadedImages.put(fileNameWithoutExt, image);

            } catch (Exception e) {
                System.err.println("PNG 파일 로드 실패: " + pngFile.getName() + " - " + e.getMessage());
            }
        }

        System.out.println("총 " + pngFiles.size() + "개의 PNG 파일이 resource 디렉토리에서 로드되었습니다.");
    }

    // 재귀적으로 PNG 파일 수집
    private void collectPngFiles(File dir, List<File> pngFiles) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                collectPngFiles(file, pngFiles); // 하위 폴더 재귀 탐색
            } else if (file.getName().toLowerCase().endsWith(".png")) {
                pngFiles.add(file);
            }
        }
    }

    // 이미지 파일명으로부터 이미지 찾기
    private BufferedImage findImageByName(String imagePath) {
        // 파일명만 추출
        String fileName = new File(imagePath).getName();

        // 확장자 포함해서 찾기
        BufferedImage image = preloadedImages.get(fileName);
        if (image != null) {
            return image;
        }

        // 확장자 제거해서 찾기
        if (fileName.contains(".")) {
            String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
            image = preloadedImages.get(nameWithoutExt);
            if (image != null) {
                return image;
            }
        }

        // 확장자가 없으면 .png를 붙여서 찾기
        if (!fileName.contains(".")) {
            image = preloadedImages.get(fileName + ".png");
            if (image != null) {
                return image;
            }
        }

        return null;
    }

    // TMX 파일 로드
    public boolean loadTMX(String tmxPath) {
        try {
            // 기존 데이터 초기화
            tilesets.clear();
            layers.clear();
            gidToTilesetCache.clear();
            globalTileCache.clear();

            File tmxFile = new File(tmxPath);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(tmxFile);
            doc.getDocumentElement().normalize();

            // 맵 속성 파싱
            Element mapElement = doc.getDocumentElement();
            mapWidth = Integer.parseInt(mapElement.getAttribute("width"));
            mapHeight = Integer.parseInt(mapElement.getAttribute("height"));
            tileWidth = Integer.parseInt(mapElement.getAttribute("tilewidth"));
            tileHeight = Integer.parseInt(mapElement.getAttribute("tileheight"));

            System.out.println("맵 로딩: " + mapWidth + "x" + mapHeight + " (타일 크기: " + tileWidth + "x" + tileHeight + ")");

            // 타일셋 파싱
            NodeList tilesetNodes = doc.getElementsByTagName("tileset");
            for (int i = 0; i < tilesetNodes.getLength(); i++) {
                Element tilesetElement = (Element) tilesetNodes.item(i);
                Tileset tileset = new Tileset();

                tileset.firstGid = Integer.parseInt(tilesetElement.getAttribute("firstgid"));
                tileset.name = tilesetElement.getAttribute("name");
                tileset.tileWidth = Integer.parseInt(tilesetElement.getAttribute("tilewidth"));
                tileset.tileHeight = Integer.parseInt(tilesetElement.getAttribute("tileheight"));
                tileset.tileCount = Integer.parseInt(tilesetElement.getAttribute("tilecount"));
                tileset.columns = Integer.parseInt(tilesetElement.getAttribute("columns"));

                // 이미지 경로 가져오기
                NodeList imageNodes = tilesetElement.getElementsByTagName("image");
                if (imageNodes.getLength() > 0) {
                    Element imageElement = (Element) imageNodes.item(0);
                    tileset.imagePath = imageElement.getAttribute("source");

                    // 미리 로드된 이미지에서 찾기
                    tileset.image = findImageByName(tileset.imagePath);

                    if (tileset.image != null) {
                        System.out.println("타일셋 이미지 연결됨: " + tileset.imagePath + " -> " + tileset.name);
                    } else {
                        System.err.println("타일셋 이미지를 찾을 수 없습니다: " + tileset.imagePath);
                    }
                }

                tilesets.add(tileset);
                System.out.println("타일셋 추가됨: " + tileset.name + " (GID: " + tileset.firstGid + ")");
            }

            // 레이어 파싱
            NodeList layerNodes = doc.getElementsByTagName("layer");
            for (int i = 0; i < layerNodes.getLength(); i++) {
                Element layerElement = (Element) layerNodes.item(i);
                Layer layer = new Layer();

                layer.name = layerElement.getAttribute("name");
                layer.width = Integer.parseInt(layerElement.getAttribute("width"));
                layer.height = Integer.parseInt(layerElement.getAttribute("height"));

                // 투명도 체크
                String opacity = layerElement.getAttribute("opacity");
                if (!opacity.isEmpty() && Float.parseFloat(opacity) == 0.0f) {
                    layer.visible = false;
                }

                // 데이터 파싱
                NodeList dataNodes = layerElement.getElementsByTagName("data");
                if (dataNodes.getLength() > 0) {
                    Element dataElement = (Element) dataNodes.item(0);
                    String encoding = dataElement.getAttribute("encoding");

                    if ("csv".equals(encoding)) {
                        String csvData = dataElement.getTextContent().trim();
                        String[] values = csvData.split(",");
                        layer.data = new int[values.length];

                        for (int j = 0; j < values.length; j++) {
                            layer.data[j] = Integer.parseInt(values[j].trim());
                        }
                    }
                }

                layers.add(layer);
                System.out.println("레이어 추가됨: " + layer.name + " (" + layer.width + "x" + layer.height + ")");
            }

            buildTilesetCache();

            preloadTileImages();

            SwingUtilities.invokeLater(() -> {
                canvas.revalidate();
                canvas.repaint();
                frame.pack();
            });

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame,
                    "TMX 파일 로드 중 오류 발생:\n" + e.getMessage(),
                    "로드 오류", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void buildTilesetCache() {
        for (Tileset tileset : tilesets) {
            for (int i = 0; i < tileset.tileCount; i++) {
                int gid = tileset.firstGid + i;
                gidToTilesetCache.put(gid, tileset);
            }
        }
        System.out.println("타일셋 캐시 구축 완료: " + gidToTilesetCache.size() + " entries");
    }

    private void preloadTileImages() {
        new Thread(() -> {
            System.out.println("타일 이미지 캐싱 시작...");
            int cachedCount = 0;

            for (Layer layer : layers) {
                if (!layer.visible) continue;

                Set<Integer> uniqueGids = new HashSet<>();
                for (int gid : layer.data) {
                    if (gid > 0) uniqueGids.add(gid);
                }

                for (int gid : uniqueGids) {
                    BufferedImage tileImage = createTileImage(gid);
                    if (tileImage != null) {
                        globalTileCache.put(gid, tileImage);
                        cachedCount++;
                    }
                }
            }

            System.out.println("타일 이미지 캐싱 완료: " + cachedCount + " tiles");
            SwingUtilities.invokeLater(() -> canvas.repaint());
        }).start();
    }

    private Tileset findTilesetForGid(int gid) {
        return gidToTilesetCache.get(gid);
    }

    private BufferedImage createTileImage(int gid) {
        if (gid == 0) return null;

        Tileset tileset = findTilesetForGid(gid);
        if (tileset == null || tileset.image == null) return null;


        BufferedImage cachedTile = tileset.tileCache.get(gid);
        if (cachedTile != null) return cachedTile;

        int tileId = gid - tileset.firstGid;
        int tilesPerRow = tileset.columns;
        int tileX = (tileId % tilesPerRow) * tileset.tileWidth;
        int tileY = (tileId / tilesPerRow) * tileset.tileHeight;

        try {
            BufferedImage tileImage = tileset.image.getSubimage(tileX, tileY, tileset.tileWidth, tileset.tileHeight);

            tileset.tileCache.put(gid, tileImage);
            return tileImage;
        } catch (Exception e) {
            return null;
        }
    }

    private BufferedImage getTileImage(int gid) {
        if (gid == 0) return null;

        BufferedImage cachedImage = globalTileCache.get(gid);
        if (cachedImage != null) return cachedImage;

        return createTileImage(gid);
    }

    private void renderTileMapOptimized(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // 배경색 설정
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // 모든 레이어 렌더링
        for (Layer layer : layers) {
            if (!layer.visible) continue;

            for (int y = 0; y < layer.height; y++) {
                for (int x = 0; x < layer.width; x++) {
                    int index = y * layer.width + x;
                    if (index >= layer.data.length) continue;

                    int gid = layer.data[index];
                    if (gid == 0) continue;

                    BufferedImage tileImage = getTileImage(gid);
                    if (tileImage != null) {
                        int drawX = x * tileWidth;
                        int drawY = y * tileHeight;
                        g2d.drawImage(tileImage, drawX, drawY, tileWidth, tileHeight, null);
                    }
                }
            }
        }

        if (sprite != null) {
            sprite.render(g2d);
        }
    }
}