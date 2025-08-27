package MapLoad;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public class GrassRenderer {
    private static class GrassInstance {
        final int x, y, index;

        GrassInstance(int x, int y, int index) {
            this.x = x;
            this.y = y;
            this.index = index;
        }
    }

    private final Map<String, List<GrassInstance>> grassPositionCache = new HashMap<>();
    private final Map<String, BufferedImage[]> preExtractedGrassTiles = new HashMap<>();
    private final Map<String, BufferedImage> customPathImages;

    public GrassRenderer(Map<String, BufferedImage> customPathImages) {
        this.customPathImages = customPathImages;
    }

    public void preExtractGrassTiles(String imagePath, TmxParser.PathTileCustomization customization) {
        BufferedImage sourceImage = customPathImages.get(imagePath);
        if (sourceImage != null) {
            BufferedImage[] grassTiles = extractAllGrassTiles(sourceImage, customization);
            preExtractedGrassTiles.put(imagePath, grassTiles);
        }
    }

    private BufferedImage[] extractAllGrassTiles(BufferedImage sourceImage, TmxParser.PathTileCustomization customization) {
        int tilesPerRow = sourceImage.getWidth() / customization.tileWidth;
        int totalTiles = (sourceImage.getHeight() / customization.tileHeight) * tilesPerRow;
        BufferedImage[] tiles = new BufferedImage[Math.min(totalTiles, 3)];

        for (int i = 0; i < tiles.length; i++) {
            int tileX = (i % tilesPerRow) * customization.tileWidth;
            int tileY = (i / tilesPerRow) * customization.tileHeight;
            tiles[i] = sourceImage.getSubimage(tileX, tileY, customization.tileWidth, customization.tileHeight);
        }

        return tiles;
    }

    public void renderGrassTile(Graphics2D g2d, int screenX, int screenY, int tileWidth, int tileHeight,
                                int gid, int tileX, int tileY, TmxParser.PathTileCustomization customization) {
        if (customization == null) return;

        String tileKey = gid + "_" + tileX + "_" + tileY;

        // 잔디 위치 가져오기 또는 생성
        List<GrassInstance> grassInstances = grassPositionCache.get(tileKey);
        if (grassInstances == null) {
            grassInstances = generateGrassPositions(tileKey, tileWidth, tileHeight, customization);
            grassPositionCache.put(tileKey, grassInstances);
        }

        // z-order로 정렬
        grassInstances.sort((g1, g2) -> {
            int centerX = tileWidth / 2;
            int centerY = tileHeight / 2;

            int quadrant1 = getQuadrant(g1.x, g1.y, centerX, centerY);
            int quadrant2 = getQuadrant(g2.x, g2.y, centerX, centerY);

            if (quadrant1 != quadrant2) {
                return getQuadrantPriority(quadrant1) - getQuadrantPriority(quadrant2);
            }

            if (g1.y != g2.y) {
                return Integer.compare(g1.y, g2.y);
            }
            return Integer.compare(g1.x, g2.x);
        });

        // 렌더링
        BufferedImage[] preExtractedTiles = preExtractedGrassTiles.get(customization.imagePath);
        if (preExtractedTiles != null) {
            for (GrassInstance grass : grassInstances) {
                if (grass.index < preExtractedTiles.length) {
                    BufferedImage grassTile = preExtractedTiles[grass.index];
                    if (grassTile != null) {
                        renderGrassWithAspectFill(g2d, grassTile, screenX + grass.x, screenY + grass.y,
                                tileWidth, tileHeight, customization);
                    }
                }
            }
        }
    }

    private void renderGrassWithAspectFill(Graphics2D g2d, BufferedImage grassTile, int drawX, int drawY,
                                           int targetWidth, int targetHeight, TmxParser.PathTileCustomization customization) {
        int originalWidth = grassTile.getWidth();
        int originalHeight = grassTile.getHeight();

        double scale = Math.max((double) targetWidth / originalWidth, (double) targetHeight / originalHeight);
        int renderWidth = (int) (originalWidth * scale);
        int renderHeight = (int) (originalHeight * scale);

        // 중앙 정렬 + 오프셋
        int renderX = drawX + (targetWidth - renderWidth) / 2 + customization.offsetX;
        int renderY = drawY + (targetHeight - renderHeight) / 2 + customization.offsetY;

        g2d.drawImage(grassTile, renderX, renderY, renderWidth, renderHeight, null);
    }

    private List<GrassInstance> generateGrassPositions(String tileKey, int tileWidth, int tileHeight,
                                                       TmxParser.PathTileCustomization customization) {
        List<GrassInstance> instances = new ArrayList<>();
        Random random = new Random(tileKey.hashCode());

        // 잔디 개수 늘려서 빈칸 줄이기
        int grassCount = 4 + random.nextInt(3); // 4~6개

        // 최소 거리 좁게
        int minDistance = Math.min(tileWidth, tileHeight) / 8;

        for (int i = 0; i < grassCount; i++) {
            int attempts = 0;
            int grassX, grassY;
            boolean validPosition;

            do {
                // 경계 2~3px 벗어나는 것 허용
                grassX = random.nextInt(tileWidth + 4) - 2;
                grassY = random.nextInt(tileHeight + 4) - 2;

                validPosition = true;
                for (GrassInstance existing : instances) {
                    double distance = Math.sqrt(Math.pow(grassX - existing.x, 2) + Math.pow(grassY - existing.y, 2));
                    if (distance < minDistance) {
                        validPosition = false;
                        break;
                    }
                }
                attempts++;
            } while (!validPosition && attempts < 20);

            // 타일 인덱스 분포
            int randValue = random.nextInt(100);
            int grassIndex = (randValue < 25) ? 0 : (randValue < 65 ? 1 : 2);

            instances.add(new GrassInstance(grassX, grassY, grassIndex));
        }

        return instances;
    }

    private int getQuadrant(int x, int y, int centerX, int centerY) {
        if (x >= centerX && y < centerY) return 1;
        if (x < centerX && y < centerY) return 2;
        if (x < centerX && y >= centerY) return 3;
        return 4;
    }

    private int getQuadrantPriority(int quadrant) {
        switch (quadrant) {
            case 3: return 0;
            case 4: return 1;
            case 2: return 2;
            case 1: return 3;
            default: return 4;
        }
    }

    public void clearCache() {
        grassPositionCache.clear();
        preExtractedGrassTiles.clear();
    }
}
