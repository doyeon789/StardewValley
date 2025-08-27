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

        // 주변 타일 포함해서 잔디 가져오기
        List<GrassInstance> surroundingGrass = getSurroundingGrass(tileX, tileY, tileWidth, tileHeight);

        // 현재 타일 잔디 가져오기 또는 생성
        List<GrassInstance> grassInstances = grassPositionCache.get(tileKey);
        if (grassInstances == null) {
            grassInstances = generateGrassPositions(tileKey, tileWidth, tileHeight, customization, surroundingGrass);
            grassPositionCache.put(tileKey, grassInstances);
        }

        // 주변 잔디 포함해서 z-order 정렬
        List<GrassInstance> allGrass = new ArrayList<>(surroundingGrass);
        allGrass.addAll(grassInstances);
        allGrass.sort((g1, g2) -> {
            int centerX = tileWidth / 2;
            int centerY = tileHeight / 2;

            int quadrant1 = getQuadrant(g1.x % tileWidth, g1.y % tileHeight, centerX, centerY);
            int quadrant2 = getQuadrant(g2.x % tileWidth, g2.y % tileHeight, centerX, centerY);

            if (quadrant1 != quadrant2) {
                return getQuadrantPriority(quadrant1) - getQuadrantPriority(quadrant2);
            }

            if (g1.y != g2.y) return Integer.compare(g1.y, g2.y);
            return Integer.compare(g1.x, g2.x);
        });

        // 렌더링
        BufferedImage[] preExtractedTiles = preExtractedGrassTiles.get(customization.imagePath);
        if (preExtractedTiles != null) {
            for (GrassInstance grass : allGrass) {
                int tileBaseX = (grass.x / tileWidth) * tileWidth;
                int tileBaseY = (grass.y / tileHeight) * tileHeight;
                BufferedImage grassTile = preExtractedTiles[grass.index];
                if (grassTile != null) {
                    renderGrassWithAspectFill(g2d, grassTile,
                            screenX + (grass.x - tileBaseX),
                            screenY + (grass.y - tileBaseY),
                            tileWidth, tileHeight, customization);
                }
            }
        }
    }

    private List<GrassInstance> getSurroundingGrass(int tileX, int tileY, int tileWidth, int tileHeight) {
        List<GrassInstance> surrounding = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue; // 현재 타일 제외
                String key = "_tile_" + (tileX + dx) + "_" + (tileY + dy);
                List<GrassInstance> neighbor = grassPositionCache.get(key);
                if (neighbor != null) {
                    // 좌표를 전체 맵 기준으로 변환
                    for (GrassInstance g : neighbor) {
                        surrounding.add(new GrassInstance(g.x + dx * tileWidth, g.y + dy * tileHeight, g.index));
                    }
                }
            }
        }
        return surrounding;
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
                                                       TmxParser.PathTileCustomization customization,
                                                       List<GrassInstance> surroundingGrass) {
        List<GrassInstance> instances = new ArrayList<>();
        Random random = new Random(tileKey.hashCode());

        int grassCount = 4 + random.nextInt(3);
        int minDistance = Math.min(tileWidth, tileHeight) / 4;

        for (int i = 0; i < grassCount; i++) {
            int attempts = 0;
            int grassX, grassY;
            boolean validPosition;

            do {
                grassX = random.nextInt(tileWidth + 4) - 4;
                grassY = random.nextInt(tileHeight + 4) - 4;

                validPosition = true;
                for (GrassInstance existing : instances) {
                    if (Math.hypot(grassX - existing.x, grassY - existing.y) < minDistance) {
                        validPosition = false;
                        break;
                    }
                }
                for (GrassInstance existing : surroundingGrass) {
                    if (Math.hypot(grassX - existing.x % tileWidth, grassY - existing.y % tileHeight) < minDistance) {
                        validPosition = false;
                        break;
                    }
                }

                attempts++;
            } while (!validPosition && attempts < 50);

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